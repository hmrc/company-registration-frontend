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

import builders.AuthBuilder
import config.FrontendAppConfig
import controllers.reg.AccountingDatesController
import fixtures.{AccountingDatesFixture, AccountingDetailsFixture, LoginFixture}
import helpers.SCRSSpec
import mocks.MetricServiceMock
import models.{AccountingDetailsBadRequestResponse, AccountingDetailsNotFoundResponse}
import play.api.i18n.MessagesApi
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.{AccountingService, MetricsService, TimeService}
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.test.WithFakeApplication

class AccountingDatesControllerSpec extends SCRSSpec with WithFakeApplication with AccountingDatesFixture with AccountingDetailsFixture
  with LoginFixture with AuthBuilder {

  val cacheMap: CacheMap = CacheMap("", Map("" -> Json.toJson(validAccountingDatesModelCRN)))


  class Setup {
    val controller = new AccountingDatesController {
      override val accountingService = mockAccountingService
      override val authConnector = mockAuthConnector
      override val metricsService: MetricsService = MetricServiceMock
      override val timeService: TimeService = fakeApplication.injector.instanceOf[TimeService]
      override val compRegConnector = mockCompanyRegistrationConnector
      override val keystoreConnector= mockKeystoreConnector
      implicit val appConfig: FrontendAppConfig = fakeApplication.injector.instanceOf[FrontendAppConfig]
      override val messagesApi = fakeApplication.injector.instanceOf[MessagesApi]
    }
  }

  "The AccountingDatesController" should {
    "be using the correct save4later connector" in new Setup {
      controller.accountingService shouldBe a [AccountingService]
    }
  }

  "Sending a GET request to Accounting Dates Controller" should {
    "return a 303 and redirect to the sign in page if unauthenticated" in new Setup {
      showWithUnauthorisedUser(controller.show) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe
            Some(authUrl)
      }
    }

    "return a 200 whilst authorised and have data fetched" in new Setup {
      CTRegistrationConnectorMocks.retrieveCTRegistration()
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      AccountingServiceMocks.fetchAccountingDetails(validAccountingDetailsModel)
      showWithAuthorisedUser(controller.show) {
        result =>
          status(result) shouldBe OK
      }
    }
  }

  "Post to the Accounting Dates Controller" should {
    "return a 303" in new Setup {
      submitWithUnauthorisedUser(controller.show, FakeRequest().withFormUrlEncodedBody()) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe
            Some(authUrl)
      }
    }

    "Redirect to account preparation if the user chooses when registered" in new Setup {
      AccountingServiceMocks.updateAccountingDetails(validAccountingResponse)
      val request = FakeRequest().withFormUrlEncodedBody(whenRegisteredData.toSeq : _*)
      submitWithAuthorisedUser(controller.submit, request)(
        result => {
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/register-your-company/loan-payments-dividends")
        }
      )
    }

    "Redirect to account preparation if the user chooses Future date" in new Setup {
      AccountingServiceMocks.updateAccountingDetails(validAccountingResponse)
      val request = FakeRequest().withFormUrlEncodedBody(futureDateData.toSeq : _*)
      submitWithAuthorisedUser(controller.submit, request)(
        result => {
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/register-your-company/loan-payments-dividends")
        }
      )
    }

    "Redirect to account preparation if the user chooses 'not planning to yet'" in new Setup {
      AccountingServiceMocks.updateAccountingDetails(validAccountingResponse)
      val request = FakeRequest().withFormUrlEncodedBody(notPlanningToYetdata.toSeq : _*)
      submitWithAuthorisedUser(controller.submit, request)(
        result => {
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/register-your-company/loan-payments-dividends")
        }
      )
    }

    "return a 400 when an invalid form is presented" in new Setup {
      val request = FakeRequest().withFormUrlEncodedBody(invalidDateData.toSeq : _*)
      submitWithAuthorisedUser(controller.submit, request){
        result =>
          status(result) shouldBe BAD_REQUEST
      }
    }

    "return a 404 when a not found response is returned from company-registration" in new Setup {
      AccountingServiceMocks.updateAccountingDetails(AccountingDetailsNotFoundResponse)
      val request = FakeRequest().withFormUrlEncodedBody(whenRegisteredData.toSeq : _*)
      submitWithAuthorisedUser(controller.submit, request){
        result =>
          status(result) shouldBe NOT_FOUND
      }
    }

    "return a 400 when a bad request response is returned from company-registration" in new Setup {
      AccountingServiceMocks.updateAccountingDetails(AccountingDetailsBadRequestResponse)
      val request = FakeRequest().withFormUrlEncodedBody(notPlanningToYetdata.toSeq : _*)
      submitWithAuthorisedUser(controller.submit, request){
        result =>
          status(result) shouldBe BAD_REQUEST
      }
    }
  }
}