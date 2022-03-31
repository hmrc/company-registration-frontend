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

import builders.AuthBuilder
import config.AppConfig
import connectors.{BusinessRegistrationConnector, BusinessRegistrationSuccessResponse}
import controllers.reg.{CompletionCapacityController, ControllerErrorHandler}
import fixtures.BusinessRegistrationFixture
import helpers.SCRSSpec
import mocks.MetricServiceMock
import models.{AboutYouChoiceForm, BusinessRegistration}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads}
import views.html.reg.{CompletionCapacity => CompletionCapacityView}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CompletionCapacityControllerSpec extends SCRSSpec with GuiceOneAppPerSuite with MockitoSugar with BusinessRegistrationFixture with AuthBuilder {

  lazy val mockBusinessRegConnector = mock[BusinessRegistrationConnector]
  lazy val mockMcc = app.injector.instanceOf[MessagesControllerComponents]
  lazy val mockControllerErrorHandler = app.injector.instanceOf[ControllerErrorHandler]
  lazy val mockCompletionCapacityView = app.injector.instanceOf[CompletionCapacityView]
  override lazy val mockAppConfig = app.injector.instanceOf[AppConfig]

  class Setup {
    val controller = new CompletionCapacityController(

      mockAuthConnector,
      mockKeystoreConnector,
      mockBusinessRegConnector,
      MetricServiceMock,
      mockMetaDataService,
      mockCompanyRegistrationConnector,
      mockMcc,
      mockCompletionCapacityView
    )(
      global,
      mockAppConfig
    )

  }

  "The CompletionCapacityController" should {
    "redirect whilst the user is un authorised when sending a GET" in new Setup {
      showWithUnauthorisedUser(controller.show) {
        result => {
          val response = await(result)
          status(response) shouldBe SEE_OTHER
        }
      }
    }

    "display the page whilst the user is authorised but with no registration id in session" in new Setup {
      mockKeystoreFetchAndGet("registrationID", None)
      when(mockBusinessRegConnector.retrieveMetadata(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[HttpReads[BusinessRegistration]]()))
        .thenReturn(Future.successful(BusinessRegistrationSuccessResponse(validBusinessRegistrationResponse)))

      showWithAuthorisedUser(controller.show) {
        result => {
          val response = await(result)
          status(response) shouldBe SEE_OTHER
          response.header.headers("Location") shouldBe "/register-your-company/post-sign-in"
        }
      }
    }

    "display the page whilst the user is authorised" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks.retrieveCTRegistration()
      when(mockBusinessRegConnector.retrieveMetadata(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[HttpReads[BusinessRegistration]]()))
        .thenReturn(Future.successful(BusinessRegistrationSuccessResponse(validBusinessRegistrationResponse)))

      showWithAuthorisedUser(controller.show) {
        result =>
          status(result) shouldBe OK
      }
    }

    "return a 303 if the user has entered valid data" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("foo"))
      when(mockMetaDataService.updateCompletionCapacity(ArgumentMatchers.eq(AboutYouChoiceForm("director", "")))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(validBusinessRegistrationResponse))

      submitWithAuthorisedUser(controller.submit, FakeRequest().withFormUrlEncodedBody(
        "completionCapacity" -> "director",
        "completionCapacityOther" -> ""
      )) {
        result =>
          status(result) shouldBe SEE_OTHER
      }
    }


    "return a 303 if the user has no entry in keystore but has valid data" in new Setup {
      mockKeystoreFetchAndGet("registrationID", None)
      when(mockMetaDataService.updateCompletionCapacity(ArgumentMatchers.eq(AboutYouChoiceForm("director", "")))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(validBusinessRegistrationResponse))

      submitWithAuthorisedUser(controller.submit, FakeRequest().withFormUrlEncodedBody(
        "completionCapacity" -> "director",
        "completionCapacityOther" -> ""
      )) {
        result =>
          status(result) shouldBe SEE_OTHER
      }
    }
    "return a 400 if the user has entered invalid data" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("foo"))
      submitWithAuthorisedUser(controller.submit, FakeRequest().withFormUrlEncodedBody(
        "complete" -> "director"
      )) {
        result =>
          status(result) shouldBe BAD_REQUEST
      }
    }
  }
  "return a 400 if the user has entered other and ` for data" in new Setup {
    mockKeystoreFetchAndGet("registrationID", Some("foo"))
    submitWithAuthorisedUser(controller.submit, FakeRequest().withFormUrlEncodedBody(
      "completionCapacity" -> "other",
      "completionCapacityOther" -> "`"
    )) {
      result =>
        status(result) shouldBe BAD_REQUEST
    }
  }
}