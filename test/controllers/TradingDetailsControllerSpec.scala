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

import java.util.UUID

import builders.AuthBuilder
import config.AppConfig
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import controllers.reg.{ControllerErrorHandler, TradingDetailsController}
import fixtures.TradingDetailsFixtures
import helpers.SCRSSpec
import mocks.MetricServiceMock
import models.TradingDetails
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.Format
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.TradingDetailsService
import uk.gov.hmrc.http.HeaderCarrier
import views.html.reg.TradingDetailsView
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class TradingDetailsControllerSpec extends SCRSSpec with GuiceOneAppPerSuite with AuthBuilder with TradingDetailsFixtures {

  val mockTradingDetailsService = mock[TradingDetailsService]
  val mockKeyStoreConnector = mock[KeystoreConnector]
  lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]
  lazy val mockControllerComponents = app.injector.instanceOf[MessagesControllerComponents]
  lazy val mockControllerErrorHandler = app.injector.instanceOf[ControllerErrorHandler]
  lazy val mockTradingDetailsView = app.injector.instanceOf[TradingDetailsView]
  val regID = UUID.randomUUID.toString

  class Setup {
    reset(mockCompanyRegistrationConnector)
    val  testController = new TradingDetailsController (
      mockAuthConnector,
      mockTradingDetailsService,
      MetricServiceMock,
      mockCompanyRegistrationConnector,
      mockKeystoreConnector,
      mockControllerComponents,
      mockControllerErrorHandler,
      mockTradingDetailsView
    )(
      appConfig,
      global
    )

  }

  "Sending a GET request to the TradingDetailsController" should {
    "return a 200 whilst requesting with an authorised user" in new Setup {
      CTRegistrationConnectorMocks.retrieveCTRegistration(buildCorporationTaxModel(rid = regID))

      mockKeystoreFetchAndGet("registrationID", Some(regID))

      when(mockTradingDetailsService.fetchRegistrationID(ArgumentMatchers.any[HeaderCarrier]())).thenReturn(Future.successful(regID))

      mockHttpGet[Option[TradingDetails]]("testUrl", Some(tradingDetailsTrue))

      when(mockCompanyRegistrationConnector.retrieveTradingDetails(ArgumentMatchers.eq(regID))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(tradingDetailsTrue)))

      when(mockTradingDetailsService.retrieveTradingDetails(ArgumentMatchers.eq(regID))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(TradingDetails("true")))

      showWithAuthorisedUser(testController.show) {
        result =>
          status(result) shouldBe OK
      }
    }

    "return a 303 whilst requesting a with an unauthorised user" in new Setup {
      showWithUnauthorisedUser(testController.show) {
        result => status(result) shouldBe SEE_OTHER
      }
    }
  }

  "POSTing the TradingDetailsController" should {
    "return a 303" when {
      "posting with valid data" in new Setup {
        when(mockKeyStoreConnector.fetchAndGet[String](ArgumentMatchers.eq("registrationID"))(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[Format[String]]()))
          .thenReturn(Future.successful(Some(regID)))

        when(mockTradingDetailsService.fetchRegistrationID(ArgumentMatchers.any[HeaderCarrier]())).thenReturn(Future.successful(regID))

        when(mockCompanyRegistrationConnector.updateTradingDetails(ArgumentMatchers.eq(regID), ArgumentMatchers.eq(tradingDetailsTrue))(ArgumentMatchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(tradingDetailsSuccessResponseTrue))

        when(mockTradingDetailsService.updateCompanyInformation(ArgumentMatchers.eq(tradingDetailsTrue))(ArgumentMatchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(tradingDetailsSuccessResponseTrue))
        mockKeystoreFetchAndGet("registrationID", Some(regID))
        submitWithAuthorisedUser(testController.submit, FakeRequest().withFormUrlEncodedBody("regularPayments" -> "true")) {
          result =>
            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some("/register-your-company/business-activities")
        }
      }
    }

    "posting with no keystore entry" in new Setup {
      mockKeystoreFetchAndGet("registrationID", None)
      submitWithAuthorisedUser(testController.submit, FakeRequest().withFormUrlEncodedBody("regularPayments" -> "true")) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/register-your-company/post-sign-in")
      }
    }

    "return a 400" when {
      "posting with valid data but there was a problem" in new Setup {
        when(mockKeyStoreConnector.fetchAndGet[String](ArgumentMatchers.eq("registrationID"))(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[Format[String]]()))
          .thenReturn(Future.successful(Some(regID)))

        when(mockTradingDetailsService.fetchRegistrationID(ArgumentMatchers.any[HeaderCarrier]())).thenReturn(Future.successful(regID))

        when(mockCompanyRegistrationConnector.updateTradingDetails(ArgumentMatchers.eq(regID), ArgumentMatchers.eq(tradingDetailsTrue))(ArgumentMatchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(tradingDetailsErrorResponse))

        when(mockTradingDetailsService.updateCompanyInformation(ArgumentMatchers.eq(tradingDetailsTrue))(ArgumentMatchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(tradingDetailsErrorResponse))
        mockKeystoreFetchAndGet("registrationID", Some(regID))
        submitWithAuthorisedUser(testController.submit, FakeRequest().withFormUrlEncodedBody("regularPayments" -> "true")) {
          result =>
            status(result) shouldBe BAD_REQUEST
        }
      }

      "posting with valid data but the resource wasn't found" in new Setup {
        when(mockKeyStoreConnector.fetchAndGet[String](ArgumentMatchers.eq("registrationID"))(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[Format[String]]()))
          .thenReturn(Future.successful(Some(regID)))

        when(mockTradingDetailsService.fetchRegistrationID(ArgumentMatchers.any[HeaderCarrier]())).thenReturn(Future.successful(regID))

        when(mockCompanyRegistrationConnector.updateTradingDetails(ArgumentMatchers.eq(regID), ArgumentMatchers.eq(tradingDetailsTrue))(ArgumentMatchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(tradingDetailsNotFoundResponse))

        when(mockTradingDetailsService.updateCompanyInformation(ArgumentMatchers.eq(tradingDetailsTrue))(ArgumentMatchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(tradingDetailsNotFoundResponse))
        mockKeystoreFetchAndGet("registrationID", Some(regID))
        submitWithAuthorisedUser(testController.submit, FakeRequest().withFormUrlEncodedBody("regularPayments" -> "true")) {
          result =>
            status(result) shouldBe BAD_REQUEST
        }
      }

      "posting with valid data but the user wasn't authorised to access the resource" in new Setup {
        when(mockKeyStoreConnector.fetchAndGet[String](ArgumentMatchers.eq("registrationID"))(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[Format[String]]()))
          .thenReturn(Future.successful(Some(regID)))

        when(mockTradingDetailsService.fetchRegistrationID(ArgumentMatchers.any[HeaderCarrier]())).thenReturn(Future.successful(regID))

        when(mockCompanyRegistrationConnector.updateTradingDetails(ArgumentMatchers.eq(regID), ArgumentMatchers.eq(tradingDetailsTrue))(ArgumentMatchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(tradingDetailsForbiddenResponse))

        when(mockTradingDetailsService.updateCompanyInformation(ArgumentMatchers.eq(tradingDetailsTrue))(ArgumentMatchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(tradingDetailsForbiddenResponse))
        mockKeystoreFetchAndGet("registrationID", Some(regID))
        submitWithAuthorisedUser(testController.submit, FakeRequest().withFormUrlEncodedBody("regularPayments" -> "true")) {
          result =>
            status(result) shouldBe BAD_REQUEST
        }
      }

      "posting with invalid data" in new Setup {
        mockKeystoreFetchAndGet("registrationID", Some("foo"))
        submitWithAuthorisedUser(testController.submit, FakeRequest().withFormUrlEncodedBody("regularPayments" -> "")) {
          result =>
            status(result) shouldBe BAD_REQUEST
        }
      }
    }
  }
}