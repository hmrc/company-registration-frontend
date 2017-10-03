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

package controllers

import builders.AuthBuilder
import connectors._
import controllers.reg.SignInOutController
import fixtures._
import helpers.SCRSSpec
import mocks.MetricServiceMock
import models.connectors.ConfirmationReferences
import models.{ConfirmationReferencesNotFoundResponse, ConfirmationReferencesSuccessResponse, ThrottleResponse, UserDetailsModel}
import org.mockito.Matchers
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.mvc.Results
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.{EmailVerificationService, EnrolmentsService}
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.test.WithFakeApplication

import scala.concurrent.Future

class SignInOutControllerSpec extends SCRSSpec
  with UserDetailsFixture with PayloadFixture with PPOBFixture with BusinessRegistrationFixture with CompanyDetailsFixture with WithFakeApplication {

  val mockEmailService = mock[EmailVerificationService]
  val mockEnrolmentsService = mock[EnrolmentsService]

  class Setup {
    val controller = new SignInOutController {
      override val authConnector = mockAuthConnector
      override val compRegConnector = mockCompanyRegistrationConnector
      override val handOffService = mockHandOffService
      override val emailService = mockEmailService
      override val enrolmentsService = mockEnrolmentsService
      val keystoreConnector = mockKeystoreConnector
      override val metrics = MetricServiceMock
      val cRFEBaseUrl = "test-base-url"
    }
  }

  val cacheMap = CacheMap("", Map("" -> Json.toJson("")))

  "postSignIn" should {

    val registrationID = "12345"

    "return a 303 if accessing without authorisation" in new Setup {
      val result = controller.postSignIn(None)(FakeRequest())
      status(result) shouldBe SEE_OTHER
    }

    "return a 303 if accessing with authorisation for a new journey" in new Setup {
      val expected = ThrottleResponse("12345", true, false)

      when(mockAuthConnector.getUserDetails[UserDetailsModel](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(userDetailsModel))

      when(mockEnrolmentsService.hasBannedRegimes(Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(false))

      when(mockCompanyRegistrationConnector.retrieveOrCreateFootprint()(Matchers.any()))
        .thenReturn(Future.successful(FootprintFound(expected)))

      when(mockEmailService.isVerified(Matchers.any(),Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful((Some(true), Some("String"))))

      when(mockEmailService.sendWelcomeEmail(Matchers.any(),Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(true)))

      when(mockCompanyRegistrationConnector.fetchRegistrationStatus(Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(None))

      when(mockHandOffService.cacheRegistrationID(Matchers.eq(registrationID))(Matchers.any()))
        .thenReturn(Future.successful(cacheMap))

      AuthBuilder.showWithAuthorisedUser(controller.postSignIn(None), mockAuthConnector) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/register-your-company/about-you")
      }
    }

    "return a 303 if accessing with authorisation for an existing journey" in new Setup {
      val expected = ThrottleResponse("12345", false, false)

      when(mockAuthConnector.getUserDetails[UserDetailsModel](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(userDetailsModel))

      when(mockCompanyRegistrationConnector.retrieveOrCreateFootprint()(Matchers.any()))
        .thenReturn(Future.successful(FootprintFound(expected)))

      when(mockCompanyRegistrationConnector.fetchRegistrationStatus(Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(None))

      when(mockHandOffService.cacheRegistrationID(Matchers.eq(registrationID))(Matchers.any()))
        .thenReturn(Future.successful(cacheMap))

      AuthBuilder.showWithAuthorisedUser(controller.postSignIn(None), mockAuthConnector) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/register-your-company/about-you")
      }
    }

    "return a 303 if accessing with authorisation for an existing journey that has been as far as HO5 and redirect to HO1" in new Setup {
      import constants.RegistrationProgressValues.HO5
      val expected = ThrottleResponse("12345", false, false, registrationProgress = Some(HO5))

      when(mockAuthConnector.getUserDetails[UserDetailsModel](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(userDetailsModel))

      when(mockCompanyRegistrationConnector.retrieveOrCreateFootprint()(Matchers.any()))
        .thenReturn(Future.successful(FootprintFound(expected)))

      when(mockCompanyRegistrationConnector.fetchRegistrationStatus(Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(None))

      when(mockHandOffService.cacheRegistrationID(Matchers.eq(registrationID))(Matchers.any()))
        .thenReturn(Future.successful(cacheMap))

      mockKeystoreCache(registrationID, registrationID, cacheMap)
      when(mockKeystoreConnector.cache(Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(cacheMap))

      AuthBuilder.showWithAuthorisedUser(controller.postSignIn(None), mockAuthConnector) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/register-your-company/basic-company-details")
      }
    }

    "return a 303 if accessing with authorisation for a complete journey" in new Setup {
      val expected = ThrottleResponse("12345", false, false)

      when(mockAuthConnector.getUserDetails[UserDetailsModel](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(userDetailsModel))

      when(mockCompanyRegistrationConnector.retrieveOrCreateFootprint()(Matchers.any()))
        .thenReturn(Future.successful(FootprintFound(expected)))

      when(mockCompanyRegistrationConnector.fetchRegistrationStatus(Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(Some("held")))

      when(mockHandOffService.cacheRegistrationID(Matchers.eq(registrationID))(Matchers.any()))
        .thenReturn(Future.successful(cacheMap))

      AuthBuilder.showWithAuthorisedUser(controller.postSignIn(None), mockAuthConnector) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/register-your-company/dashboard")
      }
    }

    "return a 303 if accessing with authorised but that account has restricted enrolments" in new Setup {
      val expected = ThrottleResponse("12345", true, false)

      when(mockAuthConnector.getUserDetails[UserDetailsModel](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(userDetailsModel))

      when(mockEnrolmentsService.hasBannedRegimes(Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(true))

      when(mockCompanyRegistrationConnector.retrieveOrCreateFootprint()(Matchers.any()))
        .thenReturn(Future.successful(FootprintFound(expected)))

      when(mockEmailService.isVerified(Matchers.any(),Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful((Some(true), Some("String"))))

      when(mockCompanyRegistrationConnector.fetchRegistrationStatus(Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(None))

      when(mockHandOffService.cacheRegistrationID(Matchers.eq(registrationID))(Matchers.any()))
        .thenReturn(Future.successful(cacheMap))

      AuthBuilder.showWithAuthorisedUser(controller.postSignIn(None), mockAuthConnector) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/register-your-company/incorrect-service")
      }
    }

    "handle the too many requests case appropriately" in new Setup {
      when(mockAuthConnector.getUserDetails[UserDetailsModel](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(userDetailsModel))

      when(mockCompanyRegistrationConnector.retrieveOrCreateFootprint()(Matchers.any()))
        .thenReturn(Future.successful(FootprintTooManyRequestsResponse))

      AuthBuilder.showWithAuthorisedUser(controller.postSignIn(None), mockAuthConnector) {
        result => status(result) shouldBe SEE_OTHER
      }
    }

    "handle the forbidden error appropriately" in new Setup {
      when(mockAuthConnector.getUserDetails[UserDetailsModel](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(userDetailsModel))
      when(mockEnrolmentsService.hasBannedRegimes(Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(false))

      when(mockCompanyRegistrationConnector.retrieveOrCreateFootprint()(Matchers.any()))
        .thenReturn(Future.successful(FootprintForbiddenResponse))

      AuthBuilder.showWithAuthorisedUser(controller.postSignIn(None), mockAuthConnector) {
        result => status(result) shouldBe FORBIDDEN
      }
    }

    "handle an unexpected error appropriately" in new Setup {
      when(mockAuthConnector.getUserDetails[UserDetailsModel](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(userDetailsModel))

      when(mockEnrolmentsService.hasBannedRegimes(Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(false))

      when(mockCompanyRegistrationConnector.retrieveOrCreateFootprint()(Matchers.any()))
        .thenReturn(Future.successful(FootprintErrorResponse(new Exception("Stuff happened"))))

      AuthBuilder.showWithAuthorisedUser(controller.postSignIn(None), mockAuthConnector) {
        result => status(result) shouldBe INTERNAL_SERVER_ERROR
      }
    }

  }

  "signOut" should {

    "redirect to the gg sign out url with a continue query string pointing to the questionnaire page" in new Setup {
      val result = controller.signOut()(FakeRequest())
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("http://localhost:9025/gg/sign-out?continue=test-base-url%2Fregister-your-company%2Fquestionnaire")
    }
  }

  "processDeferredHandOff" should {

    val regId = "reg-12345"
    val payload = "testPayload"
    val throttleResponse = ThrottleResponse(regId, created = true, confRefs = false)

    "process a deferred hand off 1 back" in new Setup {
      mockCacheRegistrationID(regId, mockKeystoreConnector)
      val handOffID = "HO1b"
      val result = await(controller.processDeferredHandoff(Some(handOffID), Some(payload), throttleResponse)(Future.successful(Results.Ok)))
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(s"http://localhost:9970/register-your-company/return-to-about-you?request=$payload")
    }

    "process a deferred hand off 2" in new Setup {
      mockCacheRegistrationID(regId, mockKeystoreConnector)
      val handOffID = "HO2"
      val result = await(controller.processDeferredHandoff(Some(handOffID), Some(payload), throttleResponse)(Future.successful(Results.Ok)))
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(s"http://localhost:9970/register-your-company/corporation-tax-details?request=$payload")
    }

    "process a deferred hand off 3 back" in new Setup {
      mockCacheRegistrationID(regId, mockKeystoreConnector)
      val handOffID = "HO3b"
      val result = await(controller.processDeferredHandoff(Some(handOffID), Some(payload), throttleResponse)(Future.successful(Results.Ok)))
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(s"http://localhost:9970/register-your-company/business-activities-back?request=$payload")
    }

    "process a deferred hand off 4" in new Setup {
      mockCacheRegistrationID(regId, mockKeystoreConnector)
      val handOffID = "HO4"
      val result = await(controller.processDeferredHandoff(Some(handOffID), Some(payload), throttleResponse)(Future.successful(Results.Ok)))
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(s"http://localhost:9970/register-your-company/corporation-tax-summary?request=$payload")
    }

    "process a deferred hand off 5 back" in new Setup {
      mockCacheRegistrationID(regId, mockKeystoreConnector)
      val handOffID = "HO5b"
      val result = await(controller.processDeferredHandoff(Some(handOffID), Some(payload), throttleResponse)(Future.successful(Results.Ok)))
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(s"http://localhost:9970/register-your-company/return-to-corporation-tax-summary?request=$payload")
    }

    "execute the call by name parameter if a handOffID and payload are both not present" in new Setup {
      mockCacheRegistrationID(regId, mockKeystoreConnector)
      val result = await(controller.processDeferredHandoff(None, None, throttleResponse)(Future.successful(Results.Ok)))
      status(result) shouldBe OK
    }

    "execute the call by name parameter if a handOffID is not present" in new Setup {
      mockCacheRegistrationID(regId, mockKeystoreConnector)
      val result = await(controller.processDeferredHandoff(None, Some(payload), throttleResponse)(Future.successful(Results.Ok)))
      status(result) shouldBe OK
    }

    "execute the call by name parameter if a payload is not present" in new Setup {
      mockCacheRegistrationID(regId, mockKeystoreConnector)
      val handOffID = "HO1b"
      val result = await(controller.processDeferredHandoff(Some(handOffID), None, throttleResponse)(Future.successful(Results.Ok)))
      status(result) shouldBe OK
    }

    "NoSuchElementException if the handOffID is missing from the map" in new Setup {
      mockCacheRegistrationID(regId, mockKeystoreConnector)
      val handOffID = "xxx"
      val ex = intercept[NoSuchElementException](await(controller.processDeferredHandoff(Some(handOffID), Some(payload), throttleResponse)(Future.successful(Results.Ok))))
      ex.getMessage shouldBe s"key not found: $handOffID"
    }
  }
}
