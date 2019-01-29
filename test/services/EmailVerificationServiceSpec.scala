/*
 * Copyright 2019 HM Revenue & Customs
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
import fixtures.UserDetailsFixture
import helpers.SCRSSpec
import models.auth.AuthDetails
import models.{Email, EmailVerificationRequest, SendTemplatedEmailRequest}
import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.mockito.{ArgumentCaptor, Matchers}
import play.api.libs.json.Json
import play.api.mvc.{AnyContent, Request}
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.auth.core.{AffinityGroup, Enrolments}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.audit.http.connector.AuditResult.Success
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.{ExecutionContext, Future}

class EmailVerificationServiceSpec extends UnitSpec with SCRSSpec with WithFakeApplication with UserDetailsFixture {

  implicit val req = FakeRequest("GET", "/test-path")

  override def beforeEach() {
    resetMocks()
  }

  val mockEmailConnector = mockEmailVerificationConnector
  val mockCrConnector = mockCompanyRegistrationConnector
  val mockKsConnector = mockKeystoreConnector

  trait Setup {
    val stubbedService = new EmailVerificationService {
      val emailConnector = mockEmailConnector
      val templatedEmailConnector = mockSendTemplateEmailConnector
      val crConnector = mockCrConnector
      val returnUrl = "TestUrl"
      val keystoreConnector = mockKsConnector
      val auditConnector = mockAuditConnector
      val sendTemplatedEmailURL = "TemplatedEmailUrl"
      val handOffService = mockHandOffService


      override def verifyEmailAddressAndSaveEmailBlockWithFlag(s: String, e: String)(implicit hc: HeaderCarrier, req: Request[AnyContent]) =
        Future.successful(
          Some(e match {
            case "verified" => true
            case _ => false
          })
        )

      override def sendVerificationLink(address: String, rId: String)(implicit hc: HeaderCarrier, req: Request[AnyContent]) =
        Future.successful(
          Some(address match {
            case "existing" => true
            case _ => false
          })
        )
    }


    val emailService = new EmailVerificationService{
      val emailConnector = mockEmailConnector
      val templatedEmailConnector = mockSendTemplateEmailConnector
      val crConnector = mockCrConnector
      val returnUrl = "TestUrl"
      val keystoreConnector = mockKsConnector
      val auditConnector = mockAuditConnector
      val sendTemplatedEmailURL = "TemplatedEmailUrl"
      val handOffService = mockHandOffService
    }
  }

  def testDefaultEmail = Email("testEmail", "GG", linkSent = false, verified = false, returnLinkEmailSent = false)
  def testVerifiedEmail = Email("verified", "GG", linkSent = true, verified = true, returnLinkEmailSent = true)
  def testUnverifiedEmail = Email("unverified", "GG", linkSent = true, verified = false, returnLinkEmailSent = false)
  def testExistingEmail = Email("existing", "GG", linkSent = false, verified = true, returnLinkEmailSent = false)
  def testNoEmail = Email("", "", linkSent = false, verified = false, returnLinkEmailSent = false)
  val regId = UUID.randomUUID().toString

  import scala.language.implicitConversions
  implicit def toAnswerWithArgs[T](f: InvocationOnMock => T): Answer[T] = new Answer[T] {
    override def answer(i: InvocationOnMock): T = f(i)
  }

  val authDetails = AuthDetails(
    AffinityGroup.Organisation,
    Enrolments(Set()),
    "testEmail",
    "extID",
    Credentials("proid", "protyp")
  )

  "checkEmailStatus" should {
    "return verified when a user has an SCRS verified email " in new Setup {
      val expected = VerifiedEmail()

      when(mockHandOffService.cacheRegistrationID(Matchers.eq(regId))(Matchers.any()))
        .thenReturn(Future.successful(CacheMap("x", Map())))

      await(stubbedService.checkEmailStatus(regId, Some(testVerifiedEmail), authDetails)) shouldBe expected


    }

    "return notVerified when a user has not got an SCRS verified email " in new Setup {
      val expected = NotVerifiedEmail()

      when(mockHandOffService.cacheRegistrationID(Matchers.eq(regId))(Matchers.any()))
        .thenReturn(Future.successful(CacheMap("x", Map())))

      await(stubbedService.checkEmailStatus(regId, Some(testUnverifiedEmail), authDetails)) shouldBe expected


    }
  }

  "checkVerifiedEmail" should {
    "return true when email is verified" in new Setup {
      when(mockEmailConnector.checkVerifiedEmail(Matchers.anyString())(Matchers.any()))
      .thenReturn(Future.successful(true))

      when(mockKsConnector.cache(Matchers.eq("email"), Matchers.any())(Matchers.any(), Matchers.any()))
      .thenReturn(Future.successful(CacheMap("x", Map())))

      when(mockCrConnector.updateEmail(Matchers.eq(regId), Matchers.any[Email]())(Matchers.any[HeaderCarrier]()))
        .thenAnswer( (i: InvocationOnMock) => Future.successful(Some(i.getArguments()(1).asInstanceOf[Email])) )

      val captor = ArgumentCaptor.forClass(classOf[EmailVerifiedEvent])

      when(mockAuditConnector.sendExtendedEvent(captor.capture())(Matchers.any[HeaderCarrier](), Matchers.any[ExecutionContext]()))
      .thenReturn(Future.successful(Success))

      await(emailService.verifyEmailAddressAndSaveEmailBlockWithFlag("testEmail", regId)) shouldBe Some(true)

      //todo audit event is moving
//      (captor.getValue.detail \ "previouslyVerified").as[Boolean] shouldBe false
    }
    "return false when email is unverified" in new Setup {
      when(mockEmailConnector.checkVerifiedEmail(Matchers.anyString())(Matchers.any()))
        .thenReturn(Future.successful(false))

      when(mockKsConnector.cache(Matchers.eq("email"), Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(CacheMap("x", Map())))

      when(mockCrConnector.updateEmail(Matchers.eq(regId), Matchers.any[Email]())(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      when(mockAuditConnector.sendExtendedEvent(Matchers.any[EmailVerifiedEvent]())(Matchers.any[HeaderCarrier](), Matchers.any[ExecutionContext]()))
        .thenReturn(Future.successful(Success))

      await(emailService.verifyEmailAddressAndSaveEmailBlockWithFlag("testEmail", regId)) shouldBe Some(false)
    }
  }

  "sendVerificationLink" should {
    "should return false when a link has been sent" in new Setup {
      when(mockKsConnector.cache(Matchers.eq("email"), Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(CacheMap("x", Map())))

      when(mockEmailConnector.requestVerificationEmailReturnVerifiedEmailStatus(Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(true))

      when(mockCrConnector.updateEmail(Matchers.eq(regId), Matchers.any[Email]())(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      await(emailService.sendVerificationLink("testEmail", regId)) shouldBe Some(true)
    }
    "should return true when a link has not been sent due to a conflict" in new Setup {

      when(mockKsConnector.cache(Matchers.eq("email"), Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(CacheMap("x", Map())))

      when(mockEmailConnector.requestVerificationEmailReturnVerifiedEmailStatus(Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(false))

      when(mockCrConnector.updateEmail(Matchers.eq(regId), Matchers.any[Email]())(Matchers.any[HeaderCarrier]()))
        .thenAnswer( (i: InvocationOnMock) => Future.successful(Some(i.getArguments()(1).asInstanceOf[Email])) )

      val captor = ArgumentCaptor.forClass(classOf[EmailVerifiedEvent])

      when(mockAuditConnector.sendExtendedEvent(captor.capture())(Matchers.any[HeaderCarrier](), Matchers.any[ExecutionContext]()))
        .thenReturn(Future.successful(Success))

      await(emailService.sendVerificationLink("testEmail", regId))shouldBe Some(false)
//todo - audit event has moved
//      (captor.getValue.detail \ "previouslyVerified").as[Boolean] shouldBe true
    }
  }

  "generateEmailRequest" should {

    val testEmail = "myTestEmail@test.test"
    val testRequest = EmailVerificationRequest(
      email = testEmail,
      templateId = "register_your_company_verification_email",
      templateParameters = Map(),
      linkExpiryDuration = "P3D",
      continueUrl = "TestUrl/register-your-company/post-sign-in"
    )

    "return a verificationRequest with the correct email " in new Setup {
      stubbedService.generateEmailRequest(testEmail) shouldBe testRequest
    }
  }

  "Generating an email request" should {
    "construct the correct JSON" in new Setup {
      val result = stubbedService.generateEmailRequest("foo@bar.wibble")

      val resultAsJson = Json.toJson(result)

      val expectedJson = Json.parse{
        s"""
           |{
           |  "email":"foo@bar.wibble",
           |  "templateId":"register_your_company_verification_email",
           |  "templateParameters":{},
           |  "linkExpiryDuration":"P3D",
           |  "continueUrl":"TestUrl/register-your-company/post-sign-in"
           |}
         """.stripMargin
      }

      resultAsJson shouldBe expectedJson
    }
  }

  "isUserSCP" should {
    "always return a false for now" in new Setup {
      await(stubbedService.isUserScp) shouldBe false
    }
  }

}
