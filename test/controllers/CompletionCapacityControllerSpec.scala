/*
 * Copyright 2018 HM Revenue & Customs
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
import connectors.{BusinessRegistrationConnector, BusinessRegistrationSuccessResponse}
import controllers.reg.CompletionCapacityController
import fixtures.BusinessRegistrationFixture
import helpers.SCRSSpec
import mocks.MetricServiceMock
import models.{AboutYouChoiceForm, BusinessRegistration}
import org.scalatest.mockito.MockitoSugar
import org.mockito.Mockito._
import org.mockito.Matchers
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.{MetaDataService, MetricsService}
import uk.gov.hmrc.play.test.WithFakeApplication

import scala.concurrent.Future
import uk.gov.hmrc.http.{ HeaderCarrier, HttpReads }

class CompletionCapacityControllerSpec extends SCRSSpec with WithFakeApplication with MockitoSugar with BusinessRegistrationFixture {

  val mockBusinessRegConnector = mock[BusinessRegistrationConnector]
  val mockMetaDataService = mock[MetaDataService]

  class Setup {
    val controller = new CompletionCapacityController {
      val authConnector = mockAuthConnector
      val keystoreConnector = mockKeystoreConnector
      val businessRegConnector = mockBusinessRegConnector
      val metaDataService = mockMetaDataService
      override val companyRegistrationConnector = mockCompanyRegistrationConnector
      override val metricsService: MetricsService = MetricServiceMock
    }

  }
  "The CompletionCapacityController" should {
    "redirect whilst the user is un authorised when sending a GET" in new Setup {
      val result = controller.show()(FakeRequest())
      status(result) shouldBe SEE_OTHER
    }

    "display the page whilst the user is authorised but with no registration id in session" in new Setup {
      mockKeystoreFetchAndGet("registrationID", None)
      when(mockBusinessRegConnector.retrieveMetadata(Matchers.any[HeaderCarrier](), Matchers.any[HttpReads[BusinessRegistration]]()))
        .thenReturn(Future.successful(BusinessRegistrationSuccessResponse(validBusinessRegistrationResponse)))

      AuthBuilder.showWithAuthorisedUser(controller.show(), mockAuthConnector) {
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
      when(mockBusinessRegConnector.retrieveMetadata(Matchers.any[HeaderCarrier](), Matchers.any[HttpReads[BusinessRegistration]]()))
        .thenReturn(Future.successful(BusinessRegistrationSuccessResponse(validBusinessRegistrationResponse)))

      AuthBuilder.showWithAuthorisedUser(controller.show(), mockAuthConnector) {
        result =>
          status(result) shouldBe OK
      }
    }

    "return a 303 if the user has entered valid data" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("foo"))
      when(mockMetaDataService.updateCompletionCapacity(Matchers.eq(AboutYouChoiceForm("director","")))(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(validBusinessRegistrationResponse))

      AuthBuilder.submitWithAuthorisedUser(controller.submit(), mockAuthConnector, FakeRequest().withFormUrlEncodedBody(
        "completionCapacity" -> "director",
        "completionCapacityOther" -> ""
      )) {
        result =>
          status(result) shouldBe SEE_OTHER
      }
    }


    "return a 303 if the user has no entry in keystore but has valid data" in new Setup {
      mockKeystoreFetchAndGet("registrationID", None)
      when(mockMetaDataService.updateCompletionCapacity(Matchers.eq(AboutYouChoiceForm("director","")))(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(validBusinessRegistrationResponse))

      AuthBuilder.submitWithAuthorisedUser(controller.submit(), mockAuthConnector, FakeRequest().withFormUrlEncodedBody(
        "completionCapacity" -> "director",
        "completionCapacityOther" -> ""
      )) {
        result =>
          status(result) shouldBe SEE_OTHER
      }
    }
    "return a 400 if the user has entered invalid data" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("foo"))
      AuthBuilder.submitWithAuthorisedUser(controller.submit(), mockAuthConnector, FakeRequest().withFormUrlEncodedBody(
        "complete" -> "director"
      )) {
        result =>
          status(result) shouldBe BAD_REQUEST
      }
    }
  }
  "return a 400 if the user has entered other and ` for data" in new Setup {
    mockKeystoreFetchAndGet("registrationID", Some("foo"))
    AuthBuilder.submitWithAuthorisedUser(controller.submit(), mockAuthConnector, FakeRequest().withFormUrlEncodedBody(
      "completionCapacity" -> "other",
      "completionCapacityOther" -> "`"
    )) {
      result =>
        status(result) shouldBe BAD_REQUEST
    }
  }

}

