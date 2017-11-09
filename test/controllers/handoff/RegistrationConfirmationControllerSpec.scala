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

package controllers.handoff

import builders.AuthBuilder
import config.FrontendAuthConnector
import connectors.KeystoreConnector
import controllers.handoff.HO6AuthenticationProvider.RegistrationConfirmationController
import fixtures.{LoginFixture, PayloadFixture}
import helpers.SCRSSpec
import models.connectors.ConfirmationReferences
import models.handoff.NavLinks
import models.{ConfirmationReferencesSuccessResponse, DESFailureDeskpro, DESFailureRetriable, RegistrationConfirmationPayload}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.{HandBackService, NavModelNotFoundException}
import org.mockito.Mockito._
import org.mockito.Matchers
import play.api.libs.json.Json
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.test.WithFakeApplication
import utils.{DecryptionError, Jwe, PayloadError}

import scala.concurrent.Future
import scala.util.{Failure, Success}
import uk.gov.hmrc.http.HeaderCarrier

class RegistrationConfirmationControllerSpec extends SCRSSpec with PayloadFixture with LoginFixture with WithFakeApplication {

  val payload = RegistrationConfirmationPayload("user","journey","transaction",Some("ref"),Some("amount"), Json.obj(), Json.obj(), Json.obj())

  class Setup {
    object TestController extends RegistrationConfirmationController {
      val authConnector = mockAuthConnector
      val keystoreConnector = mockKeystoreConnector
      val handBackService = mockHandBackService
      val companyRegistrationConnector = mockCompanyRegistrationConnector
      val handOffService = mockHandOffService
    }
  }

  "RegistrationConfirmationController" should {
    "use the correct auth connector" in {
      RegistrationConfirmationController.authConnector shouldBe FrontendAuthConnector
    }

    "use the correct keystore connector" in {
      RegistrationConfirmationController.keystoreConnector shouldBe KeystoreConnector
    }

    "use the correct hand back service" in {
      RegistrationConfirmationController.handBackService shouldBe HandBackService
    }
  }

  "registrationConfirmation" should {

    "return a SEE_OTHER if submitting without authorisation" in new Setup {
      val result = TestController.registrationConfirmation("requestData")(FakeRequest())
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe
        Some("/register-your-company/application-not-complete")
    }

    "return a SEE_OTHER if sending a valid request with authorisation" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      when(mockHandBackService.decryptConfirmationHandback(Matchers.eq(confirmationPayload))(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Success(payload)))

      when(mockHandBackService.storeConfirmationHandOff(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(ConfirmationReferencesSuccessResponse(ConfirmationReferences("test", Some("test"), Some("test"), "test"))))
      //TODO: add mock
      when(mockHandBackService.payloadHasForwardLinkAndNoPaymentRefs(Matchers.any()))
        .thenReturn(false)

      AuthBuilder.showWithAuthorisedUser(TestController.registrationConfirmation(confirmationPayload), mockAuthConnector) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/register-your-company/confirmation")
      }
    }

    "redirect to next url if Handoff 5.1 and if sending a valid request with authorisation" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      when(mockHandBackService.decryptConfirmationHandback(Matchers.eq(confirmationPayload))(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Success(payload)))

      when(mockHandBackService.storeConfirmationHandOff(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(ConfirmationReferencesSuccessResponse(ConfirmationReferences("test", None, None, "test"))))

      when(mockHandBackService.payloadHasForwardLinkAndNoPaymentRefs(Matchers.any()))
        .thenReturn(true)

      when(mockHandOffService.buildPaymentConfirmationHandoff(Matchers.any(),Matchers.any())).thenReturn(Some(("coho-url","encrypted-payload")))

      when(mockHandOffService.buildHandOffUrl(Matchers.any(),Matchers.any())).thenReturn("coho-url?request=encrypted-payload")

      AuthBuilder.showWithAuthorisedUser(TestController.registrationConfirmation(confirmationPayload), mockAuthConnector) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("coho-url?request=encrypted-payload")
      }
    }

    "redirect to signInOutController if no nav model is found" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      when(mockHandBackService.decryptConfirmationHandback(Matchers.eq(confirmationPayload))(Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(new NavModelNotFoundException))

      AuthBuilder.showWithAuthorisedUser(TestController.registrationConfirmation(confirmationPayload), mockAuthConnector) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/register-your-company/post-sign-in")
      }
    }

    "return a BadRequest if a confirmation handoff cannot be built" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      when(mockHandBackService.decryptConfirmationHandback(Matchers.eq(confirmationPayload))(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Success(payload)))

      when(mockHandBackService.storeConfirmationHandOff(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(ConfirmationReferencesSuccessResponse(ConfirmationReferences("test", None, None, "test"))))

      when(mockHandBackService.payloadHasForwardLinkAndNoPaymentRefs(Matchers.any()))
        .thenReturn(true)

      when(mockHandOffService.buildPaymentConfirmationHandoff(Matchers.any(),Matchers.any())).thenReturn(Future.successful(None))

      AuthBuilder.showWithAuthorisedUser(TestController.registrationConfirmation(confirmationPayload), mockAuthConnector) {
        result =>
          status(result) shouldBe BAD_REQUEST
      }
    }

    "return a SEE_OTHER if sending a request with authorisation but has a deskpro error" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      when(mockHandBackService.decryptConfirmationHandback(Matchers.eq(confirmationPayload))(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Success(payload)))

      when(mockHandBackService.storeConfirmationHandOff(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(DESFailureDeskpro))

      AuthBuilder.showWithAuthorisedUser(TestController.registrationConfirmation(confirmationPayload), mockAuthConnector) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/register-your-company/something-went-wrong")
      }
    }

    "return a SEE_OTHER if sending a request with authorisation but is put into a retriable state" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      when(mockHandBackService.decryptConfirmationHandback(Matchers.eq(confirmationPayload))(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Success(payload)))

      when(mockHandBackService.storeConfirmationHandOff(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(DESFailureRetriable))

      AuthBuilder.showWithAuthorisedUser(TestController.registrationConfirmation(confirmationPayload), mockAuthConnector) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/register-your-company/submission-failure")
      }
    }

    "return a SEE_OTHER if sending a valid request with auth but keystore does not exist" in new Setup {
      mockKeystoreFetchAndGet("registrationID", None)
      when(mockHandBackService.decryptConfirmationHandback(Matchers.eq(confirmationPayload))(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Success(payload)))

      AuthBuilder.showWithAuthorisedUser(TestController.registrationConfirmation(confirmationPayload), mockAuthConnector) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/register-your-company/post-sign-in")
      }
    }

    "return a BAD_REQUEST if payload cant be decrypted" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      when(mockHandBackService.decryptConfirmationHandback(Matchers.eq(""))(Matchers.any[AuthContext], Matchers.any[HeaderCarrier]))
        .thenReturn(Future.successful(Failure(DecryptionError)))

      AuthBuilder.showWithAuthorisedUser(TestController.registrationConfirmation(""), mockAuthConnector) {
        result =>
          status(result) shouldBe BAD_REQUEST
          redirectLocation(result) shouldBe None
      }
    }
  }

  "paymentConfirmation" should {
    "redirect to the Confirmation page" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      when(mockHandBackService.decryptConfirmationHandback(Matchers.eq(confirmationPayload))(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Success(payload)))

      when(mockHandBackService.storeConfirmationHandOff(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(ConfirmationReferencesSuccessResponse(ConfirmationReferences("test", Some("test"), Some("test"), "test"))))

      when(mockHandBackService.payloadHasForwardLinkAndNoPaymentRefs(Matchers.any()))
        .thenReturn(false)

      AuthBuilder.showWithAuthorisedUser(TestController.paymentConfirmation(confirmationPayload), mockAuthConnector) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/register-your-company/confirmation")
      }
    }
  }
}
