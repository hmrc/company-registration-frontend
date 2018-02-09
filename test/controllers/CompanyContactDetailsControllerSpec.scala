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
import controllers.reg.CompanyContactDetailsController
import fixtures.{CompanyContactDetailsFixture, UserDetailsFixture}
import helpers.SCRSSpec
import mocks.MetricServiceMock
import models.CompanyContactDetailsSuccessResponse
import org.mockito.Matchers.any
import org.mockito.Mockito._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.MetricsService
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Name, ~}
import uk.gov.hmrc.play.test.WithFakeApplication

import scala.concurrent.Future

class CompanyContactDetailsControllerSpec extends SCRSSpec with UserDetailsFixture with CompanyContactDetailsFixture
  with WithFakeApplication with AuthBuilder {

  class Setup {
    val controller = new CompanyContactDetailsController {
      override val authConnector = mockAuthConnector
      override val s4LConnector = mockS4LConnector
      override val companyContactDetailsService = mockCompanyContactDetailsService
      override val metricsService: MetricsService = MetricServiceMock
      override val companyRegistrationConnector = mockCompanyRegistrationConnector
      override val keystoreConnector= mockKeystoreConnector
    }
  }

  val authDetails = new ~(
    Name(Some("myFirstName"), Some("myLastName")),
    Some("fakeEmail")
  )
  val authDetailsAmend = new ~(
    new ~(
      new ~(
        Name(Some("myFirstName"), Some("myLastName")),
        Some("fakeEmail")
      ), Credentials("credID", "provID")
    ), Some("extID")
  )

  "show" should {
    "return a 200 when fetchContactDetails returns a model from S4L" in new Setup {
      CTRegistrationConnectorMocks.retrieveCTRegistration()
      mockKeystoreFetchAndGet("registrationID",Some("1"))
      CompanyContactDetailsServiceMocks.fetchContactDetails(validCompanyContactDetailsModel)

      showWithAuthorisedUserRetrieval(controller.show, authDetails) {
        result =>
          status(result) shouldBe OK
      }
    }

    "return a 200 when fetchContactDetails returns nothing from S4L but returns a model from UserDetailsService" in new Setup {
      CTRegistrationConnectorMocks.retrieveCTRegistration()
      mockKeystoreFetchAndGet("registrationID",Some("1"))
      CompanyContactDetailsServiceMocks.fetchContactDetails(validCompanyContactDetailsModel)

      showWithAuthorisedUserRetrieval(controller.show, authDetails) {
        result =>
          status(result) shouldBe OK
      }
    }
  }

  "submit" should {

    "return a 303 with a valid form and redirect to the correct location" in new Setup {
      when(mockKeystoreConnector.fetchAndGet[String](any())(any(), any()))
        .thenReturn(Future.successful(Some("test")))
      CompanyContactDetailsServiceMocks.updateContactDetails(CompanyContactDetailsSuccessResponse(validCompanyContactDetailsResponse))
      when(mockCompanyContactDetailsService.checkIfAmendedDetails(any(), any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(false))
      when(mockCompanyContactDetailsService.updatePrePopContactDetails(any(), any())(any()))
        .thenReturn(Future.successful(true))

      val request = FakeRequest().withFormUrlEncodedBody(validCompanyContactDetailsFormData: _*)

      submitWithAuthorisedUserRetrieval(controller.submit, request, authDetailsAmend) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/register-your-company/when-start-business")
      }
    }

    "return a 400 with an invalid form" in new Setup {
      when(mockKeystoreConnector.fetchAndGet[String](any())(any(), any()))
        .thenReturn(Future.successful(Some("test")))
      when(mockCompanyContactDetailsService.updatePrePopContactDetails(any(), any())(any()))
        .thenReturn(Future.successful(true))

      val request = FakeRequest().withFormUrlEncodedBody(invalidCompanyContactDetailsNameFormData: _*)

      submitWithAuthorisedUserRetrieval(controller.submit, request, authDetailsAmend) {
        result =>
          status(result) shouldBe BAD_REQUEST
      }
    }
  }
}
