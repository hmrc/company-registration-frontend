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

      override def verifyEmailAddress(s: String, e: String, ae : AuthDetails)(implicit hc: HeaderCarrier, req: Request[AnyContent]) =
        Future.successful(
          Some(e match {
            case "verified" => true
            case _ => false
          })
        )

      override def sendVerificationLink(address: String, rId: String, ae : AuthDetails)(implicit hc: HeaderCarrier, req: Request[AnyContent]) =
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

  val authDetails = AuthDetails(
    AffinityGroup.Organisation,
    Enrolments(Set()),
    "testEmail",
    "extID",
    Credentials("proid", "protyp")
  )

  "isVerified" should {

    "return an option of true when a user has an authenticated email when asking the Email service" in new Setup {
      val expected = (Some(true),Some("verified"))

      when(mockKsConnector.cache(Matchers.eq("email"), Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(CacheMap("x", Map())))

      await(stubbedService.isVerified(regId, Some(verifiedEmail), None, authDetails)) shouldBe expected

      verify(mockKsConnector, times(1)).cache(Matchers.eq("email"), Matchers.any())(Matchers.any(), Matchers.any())
    }

    "return an option of false when a user has an unauthenticated email" in new Setup {
      val expected = (Some(false),Some(unverifiedEmail.address))
      await(stubbedService.isVerified(regId, Some(unverifiedEmail), None, authDetails)) shouldBe expected
    }

    "return an option of false when a user has a EVS authenticated email but unauthenticated on company registration" in new Setup {
      val expected = (Some(true), Some(unverifiedEmail.address))
      await(stubbedService.isVerified("verified", Some(unverifiedEmail), None, authDetails)) shouldBe expected
    }

    "return an option of false and the email address when a user has not previously been sent a verification email" in new Setup {
      val expected = (Some(false), Some(defaultEmail.address))
      await(stubbedService.isVerified(regId, Some(defaultEmail), None, authDetails)) shouldBe expected
    }

    "return an option of true when a user has not been sent a verification email but is verified" in new Setup {
      val expected = (Some(true),Some("existing"))

      when(mockKsConnector.cache(Matchers.eq("email"), Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(CacheMap("x", Map())))

      await(stubbedService.isVerified(regId, Some(existingEmail), None, authDetails))shouldBe expected

      verify(mockKsConnector, times(1)).cache(Matchers.eq("email"), Matchers.any())(Matchers.any(), Matchers.any())
    }

    "return None when a user has no email" in new Setup {
      val expected = (None,None)
      await(stubbedService.isVerified(regId, Some(noEmail), None, authDetails)) shouldBe expected
    }
  }

  "getEmail" should {
    "return the contents of Some(email)" in new Setup {
      await(emailService.getEmail("", Some(defaultEmail), None, authDetails)) shouldBe defaultEmail
    }
    "return the email from session when none is provided" in new Setup {
      when(mockCrConnector.updateEmail(Matchers.eq(regId), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(Some(Email("testEmail", "GG", false, false, false))))

      await(emailService.getEmail(regId, None, None, authDetails)) shouldBe defaultEmail

      val captor = ArgumentCaptor.forClass(classOf[Email])
      verify(mockCrConnector).updateEmail(Matchers.eq(regId), captor.capture())(Matchers.any())

      captor.getValue shouldBe Email("testEmail", "GG", false, false, false)
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

      await(emailService.verifyEmailAddress("testEmail", regId, authDetails)) shouldBe Some(true)

      (captor.getValue.detail \ "previouslyVerified").as[Boolean] shouldBe false
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

      await(emailService.verifyEmailAddress("testEmail", regId, authDetails)) shouldBe Some(false)
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

      await(emailService.sendVerificationLink("testEmail", regId, authDetails)) shouldBe Some(false)
    }
    "should return true when a link has not been sent due to a conflict" in new Setup {

      when(mockKsConnector.cache(Matchers.eq("email"), Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(CacheMap("x", Map())))

      when(mockEmailConnector.requestVerificationEmail(Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(false))

      when(mockCrConnector.updateEmail(Matchers.eq(regId), Matchers.any[Email]())(Matchers.any[HeaderCarrier]()))
        .thenAnswer( (i: InvocationOnMock) => Future.successful(Some(i.getArguments()(1).asInstanceOf[Email])) )

      val captor = ArgumentCaptor.forClass(classOf[EmailVerifiedEvent])

      when(mockAuditConnector.sendExtendedEvent(captor.capture())(Matchers.any[HeaderCarrier](), Matchers.any[ExecutionContext]()))
        .thenReturn(Future.successful(Success))

      await(emailService.sendVerificationLink("testEmail", regId, authDetails))shouldBe Some(true)

      (captor.getValue.detail \ "previouslyVerified").as[Boolean] shouldBe true
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

  "sendWelcomeEmail" should {
    "send an email" when {
      "signposting is enabled with no return sent" in new Setup {
        System.setProperty("feature.signPosting", "false")

        when(mockCrConnector.retrieveEmail(Matchers.anyString())(Matchers.any()))
          .thenReturn(Future.successful(Some(defaultEmail)))

        when(mockSendTemplateEmailConnector.requestTemplatedEmail(Matchers.any())(Matchers.any()))
          .thenReturn(Future.successful(true))

        when(mockCrConnector.updateEmail(Matchers.eq(regId), Matchers.any[Email]())(Matchers.any[HeaderCarrier]()))
          .thenAnswer( (i: InvocationOnMock) => Future.successful(Some(i.getArguments()(1).asInstanceOf[Email])))

        val captor = ArgumentCaptor.forClass(classOf[EmailVerifiedEvent])

        when(mockAuditConnector.sendExtendedEvent(captor.capture())(Matchers.any[HeaderCarrier](), Matchers.any[ExecutionContext]()))
          .thenReturn(Future.successful(Success))

        await(emailService.sendWelcomeEmail(regId, "myEmail@email.com", authDetails)) shouldBe Some(true)
      }
    }

    "not send an email" when {
      "signposting is enabled with no return sent" in new Setup {
        System.setProperty("feature.signPosting", "true")

        when(mockCrConnector.retrieveEmail(Matchers.anyString())(Matchers.any()))
          .thenReturn(Future.successful(Some(defaultEmail)))

        when(mockCrConnector.updateEmail(Matchers.eq(regId), Matchers.any[Email]())(Matchers.any[HeaderCarrier]()))
          .thenAnswer( (i: InvocationOnMock) => Future.successful(Some(i.getArguments()(1).asInstanceOf[Email])))

        val captor = ArgumentCaptor.forClass(classOf[EmailVerifiedEvent])

        when(mockAuditConnector.sendExtendedEvent(captor.capture())(Matchers.any[HeaderCarrier](), Matchers.any[ExecutionContext]()))
          .thenReturn(Future.successful(Success))

        await(emailService.sendWelcomeEmail(regId, "myEmail@email.com", authDetails)) shouldBe Some(true)

        verify(mockSendTemplateEmailConnector, times(0)).requestTemplatedEmail(Matchers.any())(Matchers.any())
      }

      "signposting is disabled with a verified email" in new Setup {
        System.setProperty("feature.signPosting", "false")

        when(mockCrConnector.retrieveEmail(Matchers.anyString())(Matchers.any()))
          .thenReturn(Future.successful(Some(verifiedEmail)))

        await(emailService.sendWelcomeEmail(regId, "myEmail@email.com", authDetails)) shouldBe Some(false)

        verify(mockSendTemplateEmailConnector, times(0)).requestTemplatedEmail(Matchers.any())(Matchers.any())
      }
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
      stubbedService.generateWelcomeEmailRequest(Seq(testEmail)) shouldBe testRequest
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
 "Generating a templated email request" should {
    "construct the correct JSON" in new Setup {
      val result = stubbedService.generateWelcomeEmailRequest(Seq("foo@bar.wibble"))

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

      await(stubbedService.fetchEmailBlock(regId)) shouldBe defaultEmail
    }
    "return an appropriate exception when email is not found" in new Setup {
      when(mockCrConnector.retrieveEmail(Matchers.anyString())(Matchers.any()))
        .thenReturn(Future.successful(None))

      intercept[EmailBlockNotFound](await(stubbedService.fetchEmailBlock(regId)))
    }
  }
}
