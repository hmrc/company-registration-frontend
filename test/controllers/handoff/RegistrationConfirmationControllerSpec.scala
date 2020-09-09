/*
 * Copyright 2020 HM Revenue & Customs
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

package controllers.handoff

import builders.AuthBuilder
import config.FrontendAppConfig
import fixtures.{LoginFixture, PayloadFixture}
import helpers.SCRSSpec
import models.connectors.ConfirmationReferences
import models.{ConfirmationReferencesSuccessResponse, DESFailureDeskpro, DESFailureRetriable, RegistrationConfirmationPayload}
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.MessagesApi
import play.api.libs.json.Json
import play.api.mvc.MessagesControllerComponents
import play.api.test.Helpers._
import services.NavModelNotFoundException
import uk.gov.hmrc.http.HeaderCarrier
import utils.{DecryptionError, JweCommon}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

class RegistrationConfirmationControllerSpec extends SCRSSpec with PayloadFixture with LoginFixture with AuthBuilder with GuiceOneAppPerSuite {

  val payload = RegistrationConfirmationPayload("user", "journey", "transaction", Some("ref"), Some("amount"), Json.obj(), Json.obj(), Json.obj())

  class Setup {

    object TestController extends RegistrationConfirmationController {
      override val controllerComponents = app.injector.instanceOf[MessagesControllerComponents]
      override val ec = global
      val authConnector = mockAuthConnector
      val keystoreConnector = mockKeystoreConnector
      val handBackService = mockHandBackService
      val compRegConnector = mockCompanyRegistrationConnector
      val handOffService = mockHandOffService
      implicit val appConfig: FrontendAppConfig = app.injector.instanceOf[FrontendAppConfig]
      override val messagesApi = app.injector.instanceOf[MessagesApi]
    }

    val jweInstance = () => app.injector.instanceOf[JweCommon]
  }

  val externalID = Some("test-exid")

  "registrationConfirmation" should {

    "return a SEE_OTHER if submitting without authorisation" in new Setup {
      showWithUnauthorisedUser(TestController.registrationConfirmation("requestData")) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/register-your-company/sign-in-complete-application")
      }
    }

    "return a SEE_OTHER if sending a valid request with authorisation" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      val payloadEncr = confirmationPayload(jweInstance())
      when(mockHandBackService.decryptConfirmationHandback(Matchers.eq(payloadEncr))(Matchers.any()))
        .thenReturn(Future.successful(Success(payload)))

      when(mockHandBackService.storeConfirmationHandOff(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(ConfirmationReferencesSuccessResponse(ConfirmationReferences("test", Some("test"), Some("test"), "test"))))

      when(mockHandBackService.payloadHasForwardLinkAndNoPaymentRefs(Matchers.any()))
        .thenReturn(false)

      showWithAuthorisedUserRetrieval(TestController.registrationConfirmation(payloadEncr), externalID) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/register-your-company/application-submitted")
      }
    }

    "redirect to next url if Handoff 5.1 and if sending a valid request with authorisation" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      val payloadEncr = confirmationPayload(jweInstance())
      when(mockHandBackService.decryptConfirmationHandback(Matchers.eq(payloadEncr))(Matchers.any()))
        .thenReturn(Future.successful(Success(payload)))

      when(mockHandBackService.storeConfirmationHandOff(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(ConfirmationReferencesSuccessResponse(ConfirmationReferences("test", None, None, "test"))))

      when(mockHandBackService.payloadHasForwardLinkAndNoPaymentRefs(Matchers.any()))
        .thenReturn(true)

      when(mockHandOffService.buildPaymentConfirmationHandoff(Matchers.any())(Matchers.any())).thenReturn(Some(("coho-url", "encrypted-payload")))

      when(mockHandOffService.buildHandOffUrl(Matchers.any(), Matchers.any())).thenReturn("coho-url?request=encrypted-payload")

      showWithAuthorisedUserRetrieval(TestController.registrationConfirmation(payloadEncr), externalID) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("coho-url?request=encrypted-payload")
      }
    }

    "redirect to signInOutController if no nav model is found" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      val payloadEncr = confirmationPayload(jweInstance())
      when(mockHandBackService.decryptConfirmationHandback(Matchers.eq(payloadEncr))(Matchers.any()))
        .thenReturn(Future.failed(new NavModelNotFoundException))

      showWithAuthorisedUserRetrieval(TestController.registrationConfirmation(payloadEncr), externalID) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/register-your-company/post-sign-in")
      }
    }

    "return a BadRequest if a confirmation handoff cannot be built" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      val payloadEncr = confirmationPayload(jweInstance())
      when(mockHandBackService.decryptConfirmationHandback(Matchers.eq(payloadEncr))(Matchers.any()))
        .thenReturn(Future.successful(Success(payload)))

      when(mockHandBackService.storeConfirmationHandOff(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(ConfirmationReferencesSuccessResponse(ConfirmationReferences("test", None, None, "test"))))

      when(mockHandBackService.payloadHasForwardLinkAndNoPaymentRefs(Matchers.any()))
        .thenReturn(true)

      when(mockHandOffService.buildPaymentConfirmationHandoff(Matchers.any())(Matchers.any())).thenReturn(Future.successful(None))

      showWithAuthorisedUserRetrieval(TestController.registrationConfirmation(payloadEncr), externalID) {
        result =>
          status(result) shouldBe BAD_REQUEST
      }
    }

    "return a SEE_OTHER if sending a request with authorisation but has a deskpro error" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      val payloadEncr = confirmationPayload(jweInstance())
      when(mockHandBackService.decryptConfirmationHandback(Matchers.eq(payloadEncr))(Matchers.any()))
        .thenReturn(Future.successful(Success(payload)))

      when(mockHandBackService.storeConfirmationHandOff(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(DESFailureDeskpro))

      showWithAuthorisedUserRetrieval(TestController.registrationConfirmation(payloadEncr), externalID) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/register-your-company/something-went-wrong")
      }
    }

    "return a SEE_OTHER if sending a request with authorisation but is put into a retriable state" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      val payloadEncr = confirmationPayload(jweInstance())
      when(mockHandBackService.decryptConfirmationHandback(Matchers.eq(payloadEncr))(Matchers.any()))
        .thenReturn(Future.successful(Success(payload)))

      when(mockHandBackService.storeConfirmationHandOff(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(DESFailureRetriable))

      showWithAuthorisedUserRetrieval(TestController.registrationConfirmation(payloadEncr), externalID) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/register-your-company/application-submitted")
      }
    }

    "return a SEE_OTHER if sending a valid request with auth but keystore does not exist" in new Setup {
      mockKeystoreFetchAndGet("registrationID", None)
      val payloadEncr = confirmationPayload(jweInstance())
      when(mockHandBackService.decryptConfirmationHandback(Matchers.eq(payloadEncr))(Matchers.any()))
        .thenReturn(Future.successful(Success(payload)))

      showWithAuthorisedUserRetrieval(TestController.registrationConfirmation(payloadEncr), externalID) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/register-your-company/post-sign-in")
      }
    }

    "return a BAD_REQUEST if payload cant be decrypted" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      when(mockHandBackService.decryptConfirmationHandback(Matchers.eq(""))(Matchers.any[HeaderCarrier]))
        .thenReturn(Future.successful(Failure(DecryptionError)))

      showWithAuthorisedUserRetrieval(TestController.registrationConfirmation(""), externalID) {
        result =>
          status(result) shouldBe BAD_REQUEST
          redirectLocation(result) shouldBe None
      }
    }
  }

  "paymentConfirmation" should {
    "redirect to the Confirmation page" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      val payloadEncr = confirmationPayload(jweInstance())
      when(mockHandBackService.decryptConfirmationHandback(Matchers.eq(payloadEncr))(Matchers.any()))
        .thenReturn(Future.successful(Success(payload)))

      when(mockHandBackService.storeConfirmationHandOff(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(ConfirmationReferencesSuccessResponse(ConfirmationReferences("test", Some("test"), Some("test"), "test"))))

      when(mockHandBackService.payloadHasForwardLinkAndNoPaymentRefs(Matchers.any()))
        .thenReturn(false)

      showWithAuthorisedUserRetrieval(TestController.paymentConfirmation(payloadEncr), externalID) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/register-your-company/application-submitted")
      }
    }
  }
}
