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

package controllers

import java.util.UUID

import builders.AuthBuilder
import config.FrontendAppConfig
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import controllers.reg.TradingDetailsController
import fixtures.TradingDetailsFixtures
import helpers.SCRSSpec
import mocks.MetricServiceMock
import models.TradingDetails
import org.mockito.Matchers
import org.mockito.Mockito._
import play.api.i18n.MessagesApi
import play.api.libs.json.Format
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.{MetricsService, TradingDetailsService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.WithFakeApplication

import scala.concurrent.Future

class TradingDetailsControllerSpec extends SCRSSpec with WithFakeApplication with AuthBuilder with TradingDetailsFixtures {

  val mockTradingDetailsService = mock[TradingDetailsService]
  val mockKeyStoreConnector = mock[KeystoreConnector]
  val mockCompRegConnector = mock[CompanyRegistrationConnector]


  val regID = UUID.randomUUID.toString

  class Setup {
    object TestController extends TradingDetailsController {
      val authConnector = mockAuthConnector
      val tradingDetailsService = mockTradingDetailsService
      override val compRegConnector = mockCompanyRegistrationConnector
      override val keystoreConnector= mockKeystoreConnector
      override val metricsService: MetricsService = MetricServiceMock
      implicit val appConfig: FrontendAppConfig = fakeApplication.injector.instanceOf[FrontendAppConfig]
      override val messagesApi = fakeApplication.injector.instanceOf[MessagesApi]
    }
  }

  "Sending a GET request to the TradingDetailsController" should {
    "return a 200 whilst requesting with an authorised user" in new Setup {
      CTRegistrationConnectorMocks.retrieveCTRegistration(buildCorporationTaxModel(rid = regID))

      mockKeystoreFetchAndGet("registrationID", Some(regID))

      when(mockTradingDetailsService.fetchRegistrationID(Matchers.any[HeaderCarrier]())).thenReturn(Future.successful(regID))

      mockHttpGet[Option[TradingDetails]]("testUrl", Some(tradingDetailsTrue))

      when(mockCompRegConnector.retrieveTradingDetails(Matchers.eq(regID))(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(tradingDetailsTrue)))

      when(mockTradingDetailsService.retrieveTradingDetails(Matchers.eq(regID))(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(TradingDetails("true")))

      showWithAuthorisedUser(TestController.show()) {
        result =>
          status(result) shouldBe OK
      }
    }

    "return a 303 whilst requesting a with an unauthorised user" in new Setup {
      showWithUnauthorisedUser(TestController.show()) {
        result => status(result) shouldBe SEE_OTHER
      }
    }
  }

  "POSTing the TradingDetailsController" should {
    "return a 303" when {
      "posting with valid data" in new Setup {
        when(mockKeyStoreConnector.fetchAndGet[String](Matchers.eq("registrationID"))(Matchers.any[HeaderCarrier](), Matchers.any[Format[String]]()))
          .thenReturn(Future.successful(Some(regID)))

        when(mockTradingDetailsService.fetchRegistrationID(Matchers.any[HeaderCarrier]())).thenReturn(Future.successful(regID))

        when(mockCompRegConnector.updateTradingDetails(Matchers.eq(regID), Matchers.eq(tradingDetailsTrue))(Matchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(tradingDetailsSuccessResponseTrue))

        when(mockTradingDetailsService.updateCompanyInformation(Matchers.eq(tradingDetailsTrue))(Matchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(tradingDetailsSuccessResponseTrue))
        mockKeystoreFetchAndGet("registrationID", Some(regID))
        submitWithAuthorisedUser(TestController.submit, FakeRequest().withFormUrlEncodedBody("regularPayments" -> "true")) {
          result =>
            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some("/register-your-company/business-activities")
        }
      }
    }

    "posting with no keystore entry" in new Setup {
      mockKeystoreFetchAndGet("registrationID", None)
      submitWithAuthorisedUser(TestController.submit, FakeRequest().withFormUrlEncodedBody("regularPayments" -> "true")) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/register-your-company/post-sign-in")
      }
    }

    "return a 400" when {
      "posting with valid data but there was a problem" in new Setup {
        when(mockKeyStoreConnector.fetchAndGet[String](Matchers.eq("registrationID"))(Matchers.any[HeaderCarrier](), Matchers.any[Format[String]]()))
          .thenReturn(Future.successful(Some(regID)))

        when(mockTradingDetailsService.fetchRegistrationID(Matchers.any[HeaderCarrier]())).thenReturn(Future.successful(regID))

        when(mockCompRegConnector.updateTradingDetails(Matchers.eq(regID), Matchers.eq(tradingDetailsTrue))(Matchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(tradingDetailsErrorResponse))

        when(mockTradingDetailsService.updateCompanyInformation(Matchers.eq(tradingDetailsTrue))(Matchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(tradingDetailsErrorResponse))
        mockKeystoreFetchAndGet("registrationID", Some(regID))
        submitWithAuthorisedUser(TestController.submit, FakeRequest().withFormUrlEncodedBody("regularPayments" -> "true")) {
          result =>
            status(result) shouldBe BAD_REQUEST
        }
      }

      "posting with valid data but the resource wasn't found" in new Setup {
        when(mockKeyStoreConnector.fetchAndGet[String](Matchers.eq("registrationID"))(Matchers.any[HeaderCarrier](), Matchers.any[Format[String]]()))
          .thenReturn(Future.successful(Some(regID)))

        when(mockTradingDetailsService.fetchRegistrationID(Matchers.any[HeaderCarrier]())).thenReturn(Future.successful(regID))

        when(mockCompRegConnector.updateTradingDetails(Matchers.eq(regID), Matchers.eq(tradingDetailsTrue))(Matchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(tradingDetailsNotFoundResponse))

        when(mockTradingDetailsService.updateCompanyInformation(Matchers.eq(tradingDetailsTrue))(Matchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(tradingDetailsNotFoundResponse))
        mockKeystoreFetchAndGet("registrationID", Some(regID))
        submitWithAuthorisedUser(TestController.submit, FakeRequest().withFormUrlEncodedBody("regularPayments" -> "true")) {
          result =>
            status(result) shouldBe BAD_REQUEST
        }
      }

      "posting with valid data but the user wasn't authorised to access the resource" in new Setup {
        when(mockKeyStoreConnector.fetchAndGet[String](Matchers.eq("registrationID"))(Matchers.any[HeaderCarrier](), Matchers.any[Format[String]]()))
          .thenReturn(Future.successful(Some(regID)))

        when(mockTradingDetailsService.fetchRegistrationID(Matchers.any[HeaderCarrier]())).thenReturn(Future.successful(regID))

        when(mockCompRegConnector.updateTradingDetails(Matchers.eq(regID), Matchers.eq(tradingDetailsTrue))(Matchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(tradingDetailsForbiddenResponse))

        when(mockTradingDetailsService.updateCompanyInformation(Matchers.eq(tradingDetailsTrue))(Matchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(tradingDetailsForbiddenResponse))
        mockKeystoreFetchAndGet("registrationID", Some(regID))
        submitWithAuthorisedUser(TestController.submit, FakeRequest().withFormUrlEncodedBody("regularPayments" -> "true")) {
          result =>
            status(result) shouldBe BAD_REQUEST
        }
      }

      "posting with invalid data" in new Setup {
        mockKeystoreFetchAndGet("registrationID", Some("foo"))
        submitWithAuthorisedUser(TestController.submit, FakeRequest().withFormUrlEncodedBody("regularPayments" -> "")) {
          result =>
            status(result) shouldBe BAD_REQUEST
        }
      }
    }
  }
}