/*
 * Copyright 2022 HM Revenue & Customs
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
import config.AppConfig
import controllers.reg.ControllerErrorHandler
import fixtures.{LoginFixture, PayloadFixture}
import helpers.SCRSSpec
import models.connectors.ConfirmationReferences
import models.{ConfirmationReferencesSuccessResponse, DESFailureDeskpro, DESFailureRetriable, RegistrationConfirmationPayload}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.Json
import play.api.mvc.MessagesControllerComponents
import play.api.test.Helpers._
import services.NavModelNotFoundException
import uk.gov.hmrc.http.HeaderCarrier
import utils.{DecryptionError, JweCommon}
import views.html.{error_template, error_template_restart}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

class RegistrationConfirmationControllerSpec extends SCRSSpec with PayloadFixture with LoginFixture with AuthBuilder with GuiceOneAppPerSuite {

  lazy val payload = RegistrationConfirmationPayload("user", "journey", "transaction", Some("ref"), Some("amount"), Json.obj(), Json.obj(), Json.obj())
  lazy val jweInstance = () => app.injector.instanceOf[JweCommon]
  lazy val errorTemplateRestartPage = app.injector.instanceOf[error_template_restart]
  lazy val errorTemplatePage = app.injector.instanceOf[error_template]
  lazy val mockMcc = app.injector.instanceOf[MessagesControllerComponents]
  lazy val mockControllerErrorHandler = app.injector.instanceOf[ControllerErrorHandler]

  class Setup {

    val testController = new RegistrationConfirmationController(
      mockAuthConnector,
      mockKeystoreConnector,
      mockHandOffService,
      mockCompanyRegistrationConnector,
      mockHandBackService,
      mockMcc,
      errorTemplateRestartPage,
      errorTemplatePage
    )(mockAppConfig, global)

  }

  val externalID = Some("test-exid")

  "registrationConfirmation" should {

    "return a SEE_OTHER if submitting without authorisation" in new Setup {
      showWithUnauthorisedUser(testController.registrationConfirmation("requestData")) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/register-your-company/sign-in-complete-application")
      }
    }

    "return a SEE_OTHER if sending a valid request with authorisation" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      val payloadEncr = confirmationPayload(jweInstance())
      when(mockHandBackService.decryptConfirmationHandback(ArgumentMatchers.eq(payloadEncr))(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Success(payload)))

      when(mockHandBackService.storeConfirmationHandOff(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(ConfirmationReferencesSuccessResponse(ConfirmationReferences("test", Some("test"), Some("test"), "test"))))

      when(mockHandBackService.payloadHasForwardLinkAndNoPaymentRefs(ArgumentMatchers.any()))
        .thenReturn(false)

      showWithAuthorisedUserRetrieval(testController.registrationConfirmation(payloadEncr), externalID) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/register-your-company/application-submitted")
      }
    }

    "redirect to next url if Handoff 5.1 and if sending a valid request with authorisation" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      val payloadEncr = confirmationPayload(jweInstance())
      when(mockHandBackService.decryptConfirmationHandback(ArgumentMatchers.eq(payloadEncr))(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Success(payload)))

      when(mockHandBackService.storeConfirmationHandOff(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(ConfirmationReferencesSuccessResponse(ConfirmationReferences("test", None, None, "test"))))

      when(mockHandBackService.payloadHasForwardLinkAndNoPaymentRefs(ArgumentMatchers.any()))
        .thenReturn(true)

      when(mockHandOffService.buildPaymentConfirmationHandoff(ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(Some(("coho-url", "encrypted-payload")))

      when(mockHandOffService.buildHandOffUrl(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn("coho-url?request=encrypted-payload")

      showWithAuthorisedUserRetrieval(testController.registrationConfirmation(payloadEncr), externalID) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("coho-url?request=encrypted-payload")
      }
    }

    "redirect to signInOutController if no nav model is found" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      val payloadEncr = confirmationPayload(jweInstance())
      when(mockHandBackService.decryptConfirmationHandback(ArgumentMatchers.eq(payloadEncr))(ArgumentMatchers.any()))
        .thenReturn(Future.failed(new NavModelNotFoundException))

      showWithAuthorisedUserRetrieval(testController.registrationConfirmation(payloadEncr), externalID) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/register-your-company/post-sign-in")
      }
    }

    "return a BadRequest if a confirmation handoff cannot be built" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      val payloadEncr = confirmationPayload(jweInstance())
      when(mockHandBackService.decryptConfirmationHandback(ArgumentMatchers.eq(payloadEncr))(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Success(payload)))

      when(mockHandBackService.storeConfirmationHandOff(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(ConfirmationReferencesSuccessResponse(ConfirmationReferences("test", None, None, "test"))))

      when(mockHandBackService.payloadHasForwardLinkAndNoPaymentRefs(ArgumentMatchers.any()))
        .thenReturn(true)

      when(mockHandOffService.buildPaymentConfirmationHandoff(ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(Future.successful(None))

      showWithAuthorisedUserRetrieval(testController.registrationConfirmation(payloadEncr), externalID) {
        result =>
          status(result) shouldBe BAD_REQUEST
      }
    }

    "return a SEE_OTHER if sending a request with authorisation but has a deskpro error" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      val payloadEncr = confirmationPayload(jweInstance())
      when(mockHandBackService.decryptConfirmationHandback(ArgumentMatchers.eq(payloadEncr))(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Success(payload)))

      when(mockHandBackService.storeConfirmationHandOff(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(DESFailureDeskpro))

      showWithAuthorisedUserRetrieval(testController.registrationConfirmation(payloadEncr), externalID) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/register-your-company/something-went-wrong")
      }
    }

    "return a SEE_OTHER if sending a request with authorisation but is put into a retriable state" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      val payloadEncr = confirmationPayload(jweInstance())
      when(mockHandBackService.decryptConfirmationHandback(ArgumentMatchers.eq(payloadEncr))(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Success(payload)))

      when(mockHandBackService.storeConfirmationHandOff(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(DESFailureRetriable))

      showWithAuthorisedUserRetrieval(testController.registrationConfirmation(payloadEncr), externalID) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/register-your-company/application-submitted")
      }
    }

    "return a SEE_OTHER if sending a valid request with auth but keystore does not exist" in new Setup {
      mockKeystoreFetchAndGet("registrationID", None)
      val payloadEncr = confirmationPayload(jweInstance())
      when(mockHandBackService.decryptConfirmationHandback(ArgumentMatchers.eq(payloadEncr))(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Success(payload)))

      showWithAuthorisedUserRetrieval(testController.registrationConfirmation(payloadEncr), externalID) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/register-your-company/post-sign-in")
      }
    }

    "return a BAD_REQUEST if payload cant be decrypted" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      when(mockHandBackService.decryptConfirmationHandback(ArgumentMatchers.eq(""))(ArgumentMatchers.any[HeaderCarrier]))
        .thenReturn(Future.successful(Failure(DecryptionError)))

      showWithAuthorisedUserRetrieval(testController.registrationConfirmation(""), externalID) {
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
      when(mockHandBackService.decryptConfirmationHandback(ArgumentMatchers.eq(payloadEncr))(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Success(payload)))

      when(mockHandBackService.storeConfirmationHandOff(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(ConfirmationReferencesSuccessResponse(ConfirmationReferences("test", Some("test"), Some("test"), "test"))))

      when(mockHandBackService.payloadHasForwardLinkAndNoPaymentRefs(ArgumentMatchers.any()))
        .thenReturn(false)

      showWithAuthorisedUserRetrieval(testController.paymentConfirmation(payloadEncr), externalID) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/register-your-company/application-submitted")
      }
    }
  }
}
