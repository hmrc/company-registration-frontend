/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package services

import java.util.UUID

import audit.events.EmailVerifiedEvent
import builders.AuthBuilder
import config.FrontendAuthConnector
import connectors.{CompanyRegistrationConnector, EmailVerificationConnector, KeystoreConnector, SendTemplatedEmailConnector}
import fixtures.UserDetailsFixture
import models._
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.mockito.{ArgumentCaptor, Matchers}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.Json
import play.api.mvc.{AnyContent, Request}
import play.api.test.FakeRequest
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import uk.gov.hmrc.play.audit.http.connector.AuditResult.Success

import scala.concurrent.{ExecutionContext, Future}



class EmailVerificationServiceSpec extends UnitSpec with MockitoSugar with WithFakeApplication with UserDetailsFixture with BeforeAndAfterEach  {

  implicit val user = AuthBuilder.createTestUser
  implicit val hc = HeaderCarrier()
  implicit val req = FakeRequest("GET", "/test-path")

  override def beforeEach() {
    resetMocks()
  }

  val mockAuthConnector = mock[AuthConnector]
  val mockEmailConnector = mock[EmailVerificationConnector]
  val mockSendTemplatedEmailConnector = mock[SendTemplatedEmailConnector]
  val mockCrConnector = mock[CompanyRegistrationConnector]
  val mockKsConnector = mock[KeystoreConnector]
  val mockAuditConnector = mock[AuditConnector]

  def resetMocks() = {
    reset(mockAuthConnector)
    reset(mockEmailConnector)
    reset(mockSendTemplatedEmailConnector)
    reset(mockCrConnector)
    reset(mockKsConnector)
    reset(mockAuditConnector)
  }

  trait Setup {
    val service = new EmailVerificationService {
      val feAuthConnector = mockAuthConnector
      val emailConnector = mockEmailConnector
      val templatedEmailConnector = mockSendTemplatedEmailConnector
      val crConnector = mockCrConnector
      val returnUrl = "TestUrl"
      val keystoreConnector = mockKsConnector
      val auditConnector = mockAuditConnector
      val sendTemplatedEmailURL = "TemplatedEmailUrl"

      override def verifyEmailAddress(s: String, e: String)(implicit hc: HeaderCarrier, authContext: AuthContext, req: Request[AnyContent]) =
        Future.successful(
          Some(e match {
            case "verified" => true
            case _ => false
          })
        )

      override def sendVerificationLink(address: String, rId: String)(implicit hc: HeaderCarrier, authContext: AuthContext, req: Request[AnyContent]) =
        Future.successful(
          Some(address match {
            case "existing" => true
            case _ => false
          })
        )
    }

    val emailService = new EmailVerificationService{
      val feAuthConnector = mockAuthConnector
      val emailConnector = mockEmailConnector
      val crConnector = mockCrConnector
      val returnUrl = "TestUrl"
      val keystoreConnector = mockKsConnector
      val auditConnector = mockAuditConnector
      val sendTemplatedEmailURL = "TemplatedEmailUrl"
      val templatedEmailConnector = mockSendTemplatedEmailConnector
    }
  }

  def defaultEmail = Email("testEmail", "GG", linkSent = false, verified = false, returnLinkEmailSent = false)
  def verifiedEmail = Email("verified", "GG", linkSent = true, verified = true, returnLinkEmailSent = true)
  def unverifiedEmail = Email("unverified", "GG", linkSent = true, verified = false, returnLinkEmailSent = false)
  def existingEmail = Email("existing", "GG", linkSent = false, verified = true, returnLinkEmailSent = false)
  def noEmail = Email("", "", linkSent = false, verified = false, returnLinkEmailSent = false)
  val regId = UUID.randomUUID().toString

  import scala.language.implicitConversions
  implicit def toAnswerWithArgs[T](f: InvocationOnMock => T): Answer[T] = new Answer[T] {
    override def answer(i: InvocationOnMock): T = f(i)
  }

  "emailVerificationService" should {
    "use the correct Auth connector" in {
      EmailVerificationService.feAuthConnector shouldBe FrontendAuthConnector
    }
    "use the correct Email connector" in {
      EmailVerificationService.emailConnector shouldBe EmailVerificationConnector
    }
    "use the correct Company Registration connector" in {
      EmailVerificationService.crConnector shouldBe CompanyRegistrationConnector
    }
  }

  "isVerified" should {

    "return an option of true when a user already has an authenticated email" in new Setup {
      val expected = (Some(true),Some("verified"))
      await(service.isVerified(regId, Some(verifiedEmail), None)) shouldBe expected
    }

    "return an option of true when a user has an authenticated email when asking the Email service" in new Setup {
      val expected = (Some(true),Some("verified"))
      await(service.isVerified(regId, Some(verifiedEmail), None)) shouldBe expected
    }

    "return an option of false when a user has an unauthenticated email" in new Setup {
      val expected = (Some(false),Some(unverifiedEmail.address))
      await(service.isVerified(regId, Some(unverifiedEmail), None)) shouldBe expected
    }

    "return an option of false when a user has a EVS authenticated email but unauthenticated on company registration" in new Setup {
      val expected = (Some(true), Some(unverifiedEmail.address))
      await(service.isVerified("verified", Some(unverifiedEmail), None)) shouldBe expected
    }

    "return an option of false and the email address when a user has not previously been sent a verification email" in new Setup {
      val expected = (Some(false), Some(defaultEmail.address))
      await(service.isVerified(regId, Some(defaultEmail), None)) shouldBe expected
    }

    "return an option of true when a user has not been sent a verification email but is verified" in new Setup {
      val expected = (Some(true),Some("existing"))
      await(service.isVerified(regId, Some(existingEmail), None)) shouldBe expected
    }

    "return None when a user has no email" in new Setup {
      val expected = (None,None)
      await(service.isVerified(regId, Some(noEmail), None)) shouldBe expected
    }
  }

  "getEmail" should {
    "return the contents of Some(email)" in new Setup {
      await(emailService.getEmail("", Some(defaultEmail), None)) shouldBe defaultEmail
    }
    "return the email from session when none is provided" in new Setup {
      when(mockAuthConnector.getUserDetails[UserDetailsModel](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(userDetailsModel)

      when(mockCrConnector.updateEmail(Matchers.eq(regId), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(Some(Email("testEmail", "GG", false, false, false))))

      await(emailService.getEmail(regId, None, None)) shouldBe defaultEmail

      val captor = ArgumentCaptor.forClass(classOf[Email])
      verify(mockCrConnector).updateEmail(Matchers.eq(regId), captor.capture())(Matchers.any())

      captor.getValue shouldBe Email("testEmail", "GG", false, false, false)
    }
  }

  "checkVerifiedEmail" should {
    "return true when email is verified" in new Setup {
      when(mockEmailConnector.checkVerifiedEmail(Matchers.anyString())(Matchers.any()))
      .thenReturn(Future.successful(true))

      when(mockAuthConnector.getIds[UserIDs](Matchers.any[AuthContext]())(Matchers.any[HeaderCarrier](), Matchers.any()))
      .thenReturn(Future.successful(UserIDs("testEXID","testIID")))

      when(mockKsConnector.cache(Matchers.eq("email"), Matchers.any())(Matchers.any(), Matchers.any()))
      .thenReturn(Future.successful(CacheMap("x", Map())))

      when(mockAuthConnector.getUserDetails[UserDetailsModel](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(userDetailsModel))

      when(mockCrConnector.updateEmail(Matchers.eq(regId), Matchers.any[Email]())(Matchers.any[HeaderCarrier]()))
        .thenAnswer( (i: InvocationOnMock) => Future.successful(Some(i.getArguments()(1).asInstanceOf[Email])) )

      val captor = ArgumentCaptor.forClass(classOf[EmailVerifiedEvent])

      when(mockAuditConnector.sendEvent(captor.capture())(Matchers.any[HeaderCarrier](), Matchers.any[ExecutionContext]()))
      .thenReturn(Future.successful(Success))

      await(emailService.verifyEmailAddress("testEmail", regId)) shouldBe Some(true)

      (captor.getValue.detail \ "previouslyVerified").as[Boolean] shouldBe false
    }
    "return false when email is unverified" in new Setup {
      when(mockEmailConnector.checkVerifiedEmail(Matchers.anyString())(Matchers.any()))
        .thenReturn(Future.successful(false))

      when(mockAuthConnector.getIds[UserIDs](Matchers.any[AuthContext]())(Matchers.any[HeaderCarrier](), Matchers.any()))
        .thenReturn(Future.successful(UserIDs("testEXID","testIID")))

      when(mockKsConnector.cache(Matchers.eq("email"), Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(CacheMap("x", Map())))

      when(mockCrConnector.updateEmail(Matchers.eq(regId), Matchers.any[Email]())(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      when(mockAuditConnector.sendEvent(Matchers.any[EmailVerifiedEvent]())(Matchers.any[HeaderCarrier](), Matchers.any[ExecutionContext]()))
        .thenReturn(Future.successful(Success))

      await(emailService.verifyEmailAddress("testEmail", regId)) shouldBe Some(false)
    }
  }

  "sendVerificationLink" should {
    "should return false when a link has been sent" in new Setup {
      when(mockKsConnector.cache(Matchers.eq("email"), Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(CacheMap("x", Map())))

      when(mockEmailConnector.requestVerificationEmail(Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(true))

      when(mockCrConnector.updateEmail(Matchers.eq(regId), Matchers.any[Email]())(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      await(emailService.sendVerificationLink("testEmail", regId)) shouldBe Some(false)
    }
    "should return true when a link has not been sent due to a conflict" in new Setup {

      when(mockKsConnector.cache(Matchers.eq("email"), Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(CacheMap("x", Map())))

      when(mockEmailConnector.requestVerificationEmail(Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(false))

      when(mockCrConnector.updateEmail(Matchers.eq(regId), Matchers.any[Email]())(Matchers.any[HeaderCarrier]()))
        .thenAnswer( (i: InvocationOnMock) => Future.successful(Some(i.getArguments()(1).asInstanceOf[Email])) )

      when(mockAuthConnector.getUserDetails[UserDetailsModel](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(userDetailsModel))

      when(mockAuthConnector.getIds[UserIDs](Matchers.any[AuthContext]())(Matchers.any[HeaderCarrier](), Matchers.any()))
        .thenReturn(Future.successful(UserIDs("testEXID","testIID")))

      val captor = ArgumentCaptor.forClass(classOf[EmailVerifiedEvent])

      when(mockAuditConnector.sendEvent(captor.capture())(Matchers.any[HeaderCarrier](), Matchers.any[ExecutionContext]()))
        .thenReturn(Future.successful(Success))

      await(emailService.sendVerificationLink("testEmail", regId)) shouldBe Some(true)

      (captor.getValue.detail \ "previouslyVerified").as[Boolean] shouldBe true
    }
  }

  "generateEmailRequest" should {

    val testEmail = "myTestEmail@test.test"
    val testRequest = EmailVerificationRequest(
      email = testEmail,
      templateId = "register_your_company_verification_email",
      templateParameters = Map(),
      linkExpiryDuration = "P1D",
      continueUrl = "TestUrl/register-your-company/post-sign-in"
    )

    "return a verificationRequest with the correct email " in new Setup {
      service.generateEmailRequest(testEmail) shouldBe testRequest
    }
  }

  "generateTemplatedEmailRequest" should {

    val testEmail = "myTestEmail@test.test"
    val testRequest = SendTemplatedEmailRequest(
      to = Seq(testEmail),
      templateId = "register_your_company_welcome_email",
      parameters = Map(
      "returnLink" -> "TemplatedEmailUrl"),
      force = true
    )

    "return a templatedEmailRequest with the correct email " in new Setup {
      service.generateWelcomeEmailRequest(Seq(testEmail)) shouldBe testRequest
    }
  }


  "Generating an email request" should {
    "construct the correct JSON" in new Setup {
      val result = service.generateEmailRequest("foo@bar.wibble")

      val resultAsJson = Json.toJson(result)

      val expectedJson = Json.parse{
        s"""
           |{
           |  "email":"foo@bar.wibble",
           |  "templateId":"register_your_company_verification_email",
           |  "templateParameters":{},
           |  "linkExpiryDuration":"P1D",
           |  "continueUrl":"TestUrl/register-your-company/post-sign-in"
           |}
         """.stripMargin
      }

      resultAsJson shouldBe expectedJson
    }
  }
 "Generating a templated email request" should {
    "construct the correct JSON" in new Setup {
      val result = service.generateWelcomeEmailRequest(Seq("foo@bar.wibble"))

      val resultAsJson = Json.toJson(result)

      val expectedJson = Json.parse{
        s"""
           |{
           |  "to":["foo@bar.wibble"],
           |  "templateId":"register_your_company_welcome_email",
           |  "parameters":{
           |  "returnLink" : "TemplatedEmailUrl"
           |  },
           |  "force":true
           |}
         """.stripMargin
      }

      resultAsJson shouldBe expectedJson
    }
  }

  "fetchEmailBlock" should {
    "return an email block" in new Setup {
      when(mockCrConnector.retrieveEmail(Matchers.anyString())(Matchers.any()))
        .thenReturn(Future.successful(Some(defaultEmail)))

      await(service.fetchEmailBlock(regId)) shouldBe defaultEmail
    }
    "return an appropriate exception when email is not found" in new Setup {
      when(mockCrConnector.retrieveEmail(Matchers.anyString())(Matchers.any()))
        .thenReturn(Future.successful(None))

      intercept[EmailBlockNotFound](await(service.fetchEmailBlock(regId)))
    }
  }
}
