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

package controllers

import java.time.LocalDate

import builders.AuthBuilder
import config.AppConfig
import connectors._
import controllers.reg.{ControllerErrorHandler, SignInOutController}
import fixtures._
import helpers.SCRSSpec
import mocks.MetricServiceMock
import models.{Email, ThrottleResponse}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.Json
import play.api.mvc.{MessagesControllerComponents, Results}
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentType, _}
import services.{EnrolmentsService, VerifiedEmail}
import uk.gov.hmrc.auth.core.retrieve.{Credentials, ~}
import uk.gov.hmrc.auth.core.{AffinityGroup, Enrolments}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.binders.ContinueUrl
import views.html.{timeout => timeoutView}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SignInOutControllerSpec extends SCRSSpec
  with UserDetailsFixture with PayloadFixture with PPOBFixture with BusinessRegistrationFixture with CompanyDetailsFixture with GuiceOneAppPerSuite
  with AuthBuilder {

  val mockEnrolmentsService = mock[EnrolmentsService]
  lazy val mockControllerErrorHandler = app.injector.instanceOf[ControllerErrorHandler]
  lazy val mockMcc = app.injector.instanceOf[MessagesControllerComponents]
  lazy val mockTimeoutView = app.injector.instanceOf[timeoutView]
  override lazy val mockAppConfig = app.injector.instanceOf[AppConfig]

  class Setup(val corsHost: Option[String] = None) {

    val controller = new SignInOutController (
      mockAuthConnector,
      mockCompanyRegistrationConnector,
      mockHandOffService,
      mockEmailService,
      MetricServiceMock,
      mockKeystoreConnector,
      mockEnrolmentsService,
      mockControllerErrorHandler,
      mockMcc,
      mockTimeoutView
    )(
      mockAppConfig,
      global
    ){
      override lazy val corsRenewHost = corsHost
      override lazy val cRFEBaseUrl = "test-base-url"
    }
  }

  val cacheMap = CacheMap("", Map("" -> Json.toJson("")))
  val authDetails = new ~(
    new ~(
      new ~(
        new ~(
          Some(AffinityGroup.Organisation),
          Enrolments(Set())
        ), Some("test")
      ), Some("test")
    ), Credentials("test", "test")
  )

  "postSignIn" should {

    val registrationID = "12345"

    "return a 303 if accessing without authorisation" in new Setup {
      showWithUnauthorisedUser(controller.postSignIn(None)) {
        result => status(result) mustBe SEE_OTHER
      }
    }

    "return a 303 if accessing with authorisation for a new journey" in new Setup {
      val expected = ThrottleResponse("12345", true, false, false, Some(Email("email", "GG", false, false, false)))

      when(mockEnrolmentsService.hasBannedRegimes(ArgumentMatchers.any()))
        .thenReturn(Future.successful(false))

      when(mockCompanyRegistrationConnector.retrieveOrCreateFootprint()(ArgumentMatchers.any()))
        .thenReturn(Future.successful(FootprintFound(expected)))

      when(mockEmailService.checkEmailStatus(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(VerifiedEmail()))

      when(mockCompanyRegistrationConnector.fetchRegistrationStatus(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(None))

      when(mockHandOffService.cacheRegistrationID(ArgumentMatchers.eq(registrationID))(ArgumentMatchers.any()))
        .thenReturn(Future.successful(cacheMap))

      showWithAuthorisedUserRetrieval(controller.postSignIn(None), authDetails) {
        result =>
          status(result) mustBe SEE_OTHER
          redirectLocation(result) mustBe Some("/register-your-company/relationship-to-company")
      }
    }
    "return a 303 to 'no email' page if email does not exist in auth" in new Setup {
      val authDetailsNoEmail = new ~(
        new ~(
          new ~(
            new ~(
              Some(AffinityGroup.Organisation),
              Enrolments(Set())
            ), None
          ), Some("foo")
        ), Credentials("test", "test")
      )

      showWithAuthorisedUserRetrieval(controller.postSignIn(None), authDetailsNoEmail) {
        result =>
          status(result) mustBe SEE_OTHER
          redirectLocation(result).get mustBe controllers.verification.routes.EmailVerificationController.createShow.url
      }

    }
    "return 500 if incorrect details are returned from auth" in new Setup {
      val authDetailsNoEmailAndNoOrgType = new ~(
        new ~(
          new ~(
            new ~(
              None,
              Enrolments(Set())
            ), Some("test")
          ), None
        ), Credentials("test", "test")
      )

      showWithAuthorisedUserRetrieval(controller.postSignIn(None), authDetailsNoEmailAndNoOrgType) {
        result =>
          status(result) mustBe 500
      }
    }

    "return a 303 if accessing with authorisation for an existing journey" in new Setup {
      val expected = ThrottleResponse("12345", false, false, false, Some(Email("email", "GG", false, false, false)))

      when(mockCompanyRegistrationConnector.retrieveOrCreateFootprint()(ArgumentMatchers.any()))
        .thenReturn(Future.successful(FootprintFound(expected)))

      when(mockCompanyRegistrationConnector.fetchRegistrationStatus(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(None))

      when(mockHandOffService.cacheRegistrationID(ArgumentMatchers.eq(registrationID))(ArgumentMatchers.any()))
        .thenReturn(Future.successful(cacheMap))

      when(mockEmailService.checkEmailStatus(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(VerifiedEmail()))

      showWithAuthorisedUserRetrieval(controller.postSignIn(None), authDetails) {
        result =>
          status(result) mustBe SEE_OTHER
          redirectLocation(result) mustBe Some("/register-your-company/relationship-to-company")
      }
    }

    "return a 303 if accessing with authorisation for an existing journey that has been as far as HO5 and redirect to HO1" in new Setup {

      import constants.RegistrationProgressValues.HO5

      val expected = ThrottleResponse("12345", false, false, false, registrationProgress = Some(HO5))

      when(mockCompanyRegistrationConnector.retrieveOrCreateFootprint()(ArgumentMatchers.any()))
        .thenReturn(Future.successful(FootprintFound(expected)))

      when(mockCompanyRegistrationConnector.fetchRegistrationStatus(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(None))

      when(mockHandOffService.cacheRegistrationID(ArgumentMatchers.eq(registrationID))(ArgumentMatchers.any()))
        .thenReturn(Future.successful(cacheMap))

      mockKeystoreCache(registrationID, registrationID, cacheMap)
      when(mockKeystoreConnector.cache(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(cacheMap))

      showWithAuthorisedUserRetrieval(controller.postSignIn(None), authDetails) {
        result =>
          status(result) mustBe SEE_OTHER
          redirectLocation(result) mustBe Some("/register-your-company/basic-company-details")
      }
    }

    "return a 303 if accessing with authorisation for an existing journey that has locked status and redirect to HO1" in new Setup {

      import constants.RegistrationProgressValues.HO5

      val expected = ThrottleResponse("12345", false, true, false, registrationProgress = Some(HO5))

      when(mockCompanyRegistrationConnector.retrieveOrCreateFootprint()(ArgumentMatchers.any()))
        .thenReturn(Future.successful(FootprintFound(expected)))

      when(mockCompanyRegistrationConnector.fetchRegistrationStatus(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some("locked")))

      when(mockHandOffService.cacheRegistrationID(ArgumentMatchers.eq(registrationID))(ArgumentMatchers.any()))
        .thenReturn(Future.successful(cacheMap))

      showWithAuthorisedUserRetrieval(controller.postSignIn(None), authDetails) {
        result =>
          status(result) mustBe SEE_OTHER
          redirectLocation(result) mustBe Some("/register-your-company/basic-company-details")
      }
    }

    "return a 303 if accessing with authorisation for an existing journey that has been as far as HO5.1 and redirect to HO1" in new Setup {

      import constants.RegistrationProgressValues.HO5

      val expected = ThrottleResponse("12345", false, true, false, registrationProgress = Some(HO5))

      when(mockCompanyRegistrationConnector.retrieveOrCreateFootprint()(ArgumentMatchers.any()))
        .thenReturn(Future.successful(FootprintFound(expected)))

      when(mockCompanyRegistrationConnector.fetchRegistrationStatus(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some("held")))

      when(mockHandOffService.cacheRegistrationID(ArgumentMatchers.eq(registrationID))(ArgumentMatchers.any()))
        .thenReturn(Future.successful(cacheMap))

      showWithAuthorisedUserRetrieval(controller.postSignIn(None), authDetails) {
        result =>
          status(result) mustBe SEE_OTHER
          redirectLocation(result) mustBe Some("/register-your-company/basic-company-details")
      }
    }

    "return a 303 if accessing with authorisation for a complete journey" in new Setup {
      val expected = ThrottleResponse("12345", false, true, true)

      when(mockCompanyRegistrationConnector.retrieveOrCreateFootprint()(ArgumentMatchers.any()))
        .thenReturn(Future.successful(FootprintFound(expected)))

      when(mockCompanyRegistrationConnector.fetchRegistrationStatus(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some("held")))

      when(mockHandOffService.cacheRegistrationID(ArgumentMatchers.eq(registrationID))(ArgumentMatchers.any()))
        .thenReturn(Future.successful(cacheMap))

      showWithAuthorisedUserRetrieval(controller.postSignIn(None), authDetails) {
        result =>
          status(result) mustBe SEE_OTHER
          redirectLocation(result) mustBe Some("/register-your-company/company-registration-overview")
      }
    }

    "return a 303 if accessing with authorised but that account has restricted enrolments" in new Setup {
      val expected = ThrottleResponse("12345", true, false, false)

      when(mockEnrolmentsService.hasBannedRegimes(ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      when(mockCompanyRegistrationConnector.retrieveOrCreateFootprint()(ArgumentMatchers.any()))
        .thenReturn(Future.successful(FootprintFound(expected)))

      when(mockEmailService.checkEmailStatus(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(VerifiedEmail()))

      when(mockCompanyRegistrationConnector.fetchRegistrationStatus(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(None))

      when(mockHandOffService.cacheRegistrationID(ArgumentMatchers.eq(registrationID))(ArgumentMatchers.any()))
        .thenReturn(Future.successful(cacheMap))

      showWithAuthorisedUserRetrieval(controller.postSignIn(None), authDetails) {
        result =>
          status(result) mustBe SEE_OTHER
          redirectLocation(result) mustBe Some("/register-your-company/incorrect-service")
      }
    }

    "handle the too many requests case appropriately" in new Setup {
      when(mockCompanyRegistrationConnector.retrieveOrCreateFootprint()(ArgumentMatchers.any()))
        .thenReturn(Future.successful(FootprintTooManyRequestsResponse))

      showWithAuthorisedUserRetrieval(controller.postSignIn(None), authDetails) {
        result => status(result) mustBe SEE_OTHER
      }
    }

    "handle the forbidden error appropriately" in new Setup {
      when(mockEnrolmentsService.hasBannedRegimes(ArgumentMatchers.any()))
        .thenReturn(Future.successful(false))

      when(mockCompanyRegistrationConnector.retrieveOrCreateFootprint()(ArgumentMatchers.any()))
        .thenReturn(Future.successful(FootprintForbiddenResponse))

      showWithAuthorisedUserRetrieval(controller.postSignIn(None), authDetails) {
        result => status(result) mustBe FORBIDDEN
      }
    }

    "handle an unexpected error appropriately" in new Setup {
      when(mockEnrolmentsService.hasBannedRegimes(ArgumentMatchers.any()))
        .thenReturn(Future.successful(false))

      when(mockCompanyRegistrationConnector.retrieveOrCreateFootprint()(ArgumentMatchers.any()))
        .thenReturn(Future.successful(FootprintErrorResponse(new Exception("Stuff happened"))))

      showWithAuthorisedUserRetrieval(controller.postSignIn(None), authDetails) {
        result => status(result) mustBe INTERNAL_SERVER_ERROR
      }
    }

  }

  "signOut" should {
    def encodeURL(url: String) = java.net.URLEncoder.encode(url, "UTF-8")

    "redirect to the gg sign out url with a continue query string pointing to the questionnaire page" in new Setup {
      val result = controller.signOut()(FakeRequest())
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("http://localhost:9553/bas-gateway/sign-out-without-state?continue=test-base-url%2Fregister-your-company%2Fquestionnaire")
    }

    "redirect to the gg sign out url with a continue query string pointing to the specified relative page" in new Setup {
      val continueUrl = "/wibble"
      val result = controller.signOut(Some(ContinueUrl(continueUrl)))(FakeRequest())
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(s"http://localhost:9553/bas-gateway/sign-out-without-state?continue=${encodeURL(continueUrl)}")
    }

    "redirect to the gg sign out url with a continue query string pointing to the specified absolute page" in new Setup {
      val continueUrl = "https://foo.gov.uk/wibble"
      val result = controller.signOut(Some(ContinueUrl(continueUrl)))(FakeRequest())
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(s"http://localhost:9553/bas-gateway/sign-out-without-state?continue=${encodeURL(continueUrl)}")
    }

    "NOT redirect if the url starts with //" in new Setup {
      val continueUrl = "//foo.gov.uk/wibble"
      intercept[IllegalArgumentException] {
        controller.signOut(Some(ContinueUrl(continueUrl)))(FakeRequest())
      }
    }
  }

  "processDeferredHandOff" should {

    val regId = "reg-12345"
    val payload = "testPayload"
    val throttleResponse = ThrottleResponse(regId, created = true, confRefs = false, paymentRefs = false)

    "process a deferred hand off 1 back" in new Setup {
      mockCacheRegistrationID(regId, mockKeystoreConnector)
      val handOffID = "HO1b"
      val result = await(controller.processDeferredHandoff(Some(handOffID), Some(payload), throttleResponse)(Future.successful(Results.Ok)))
      status(result) mustBe 303
      redirectLocation(result) mustBe Some(s"http://localhost:9970/register-your-company/return-to-about-you?request=$payload")
    }

    "process a deferred hand off 2" in new Setup {
      mockCacheRegistrationID(regId, mockKeystoreConnector)
      val handOffID = "HO2"
      val result = await(controller.processDeferredHandoff(Some(handOffID), Some(payload), throttleResponse)(Future.successful(Results.Ok)))
      status(result) mustBe 303
      redirectLocation(result) mustBe Some(s"http://localhost:9970/register-your-company/corporation-tax-details?request=$payload")
    }

    "process a deferred hand off 3 back" in new Setup {
      mockCacheRegistrationID(regId, mockKeystoreConnector)
      val handOffID = "HO3b"
      val result = await(controller.processDeferredHandoff(Some(handOffID), Some(payload), throttleResponse)(Future.successful(Results.Ok)))
      status(result) mustBe 303
      redirectLocation(result) mustBe Some(s"http://localhost:9970/register-your-company/business-activities-back?request=$payload")
    }

    "process a deferred hand off 3-1" in new Setup {
      mockCacheRegistrationID(regId, mockKeystoreConnector)
      val handOffID = "HO3-1"
      val result = await(controller.processDeferredHandoff(Some(handOffID), Some(payload), throttleResponse)(Future.successful(Results.Ok)))
      status(result) mustBe 303
      redirectLocation(result) mustBe Some(s"http://localhost:9970/register-your-company/groups-handback?request=$payload")
    }
    "process a deferred hand off 3b-1" in new Setup {
      mockCacheRegistrationID(regId, mockKeystoreConnector)
      val handOffID = "HO3b-1"
      val result = await(controller.processDeferredHandoff(Some(handOffID), Some(payload), throttleResponse)(Future.successful(Results.Ok)))
      status(result) mustBe 303
      redirectLocation(result) mustBe Some(s"http://localhost:9970/register-your-company/groups-back-handback?request=$payload")
    }

    "process a deferred hand off 4" in new Setup {
      mockCacheRegistrationID(regId, mockKeystoreConnector)
      val handOffID = "HO4"
      val result = await(controller.processDeferredHandoff(Some(handOffID), Some(payload), throttleResponse)(Future.successful(Results.Ok)))
      status(result) mustBe 303
      redirectLocation(result) mustBe Some(s"http://localhost:9970/register-your-company/corporation-tax-summary?request=$payload")
    }

    "process a deferred hand off 5 back" in new Setup {
      mockCacheRegistrationID(regId, mockKeystoreConnector)
      val handOffID = "HO5b"
      val result = await(controller.processDeferredHandoff(Some(handOffID), Some(payload), throttleResponse)(Future.successful(Results.Ok)))
      status(result) mustBe 303
      redirectLocation(result) mustBe Some(s"http://localhost:9970/register-your-company/return-to-corporation-tax-summary?request=$payload")
    }

    "execute the call by name parameter if a handOffID and payload are both not present" in new Setup {
      mockCacheRegistrationID(regId, mockKeystoreConnector)
      val result = await(controller.processDeferredHandoff(None, None, throttleResponse)(Future.successful(Results.Ok)))
      status(result) mustBe OK
    }

    "execute the call by name parameter if a handOffID is not present" in new Setup {
      mockCacheRegistrationID(regId, mockKeystoreConnector)
      val result = await(controller.processDeferredHandoff(None, Some(payload), throttleResponse)(Future.successful(Results.Ok)))
      status(result) mustBe OK
    }

    "execute the call by name parameter if a payload is not present" in new Setup {
      mockCacheRegistrationID(regId, mockKeystoreConnector)
      val handOffID = "HO1b"
      val result = await(controller.processDeferredHandoff(Some(handOffID), None, throttleResponse)(Future.successful(Results.Ok)))
      status(result) mustBe OK
    }

    "NoSuchElementException if the handOffID is missing from the map" in new Setup {
      mockCacheRegistrationID(regId, mockKeystoreConnector)
      val handOffID = "xxx"
      val ex = intercept[NoSuchElementException](await(controller.processDeferredHandoff(Some(handOffID), Some(payload), throttleResponse)(Future.successful(Results.Ok))))
      ex.getMessage mustBe s"key not found: $handOffID"
    }
  }

  "renewSession" should {
    "return 200 when hit with Authorised User" in new Setup {
      when(mockKeystoreConnector.cache(ArgumentMatchers.contains("lastActionTimestamp"), ArgumentMatchers.any[LocalDate]())(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.successful(cacheMap))

      showWithAuthorisedUser(controller.renewSession()) { a =>
        status(a) mustBe 200
        contentType(a) mustBe Some("image/jpeg")
        await(a).header.headers.toString().contains("""renewSession.jpg""") mustBe true
        header("Access-Control-Allow-Origin", a) mustBe None
        header("Access-Control-Allow-Credentials", a) mustBe None
      }
    }

    "return CORS headers when a cors host is supplied" in new Setup(Some("http://localhost:12345")) {
      when(mockKeystoreConnector.cache(ArgumentMatchers.contains("lastActionTimestamp"), ArgumentMatchers.any[LocalDate]())(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.successful(cacheMap))

      showWithAuthorisedUser(controller.renewSession()) { a =>
        status(a) mustBe 200
        header("Access-Control-Allow-Origin", a) mustBe Some("http://localhost:12345")
        header("Access-Control-Allow-Credentials", a) mustBe Some("true")
      }
    }
  }

  "destroySession" should {
    "return redirect to timeout show and get rid of headers" in new Setup {
      val fr = FakeRequest().withHeaders(("playFoo", "no more"))
      val res = await(controller.destroySession()(fr))
      status(res) mustBe 303
      headers(res).contains("playFoo") mustBe false

      redirectLocation(res) mustBe Some(controllers.reg.routes.SignInOutController.timeoutShow.url)
    }
  }
  "timeoutShow" should {
    "return 200" in new Setup {
      val res = await(controller.timeoutShow()(FakeRequest()))
      status(res) mustBe 200
    }
  }
}
