/*
 * Copyright 2021 HM Revenue & Customs
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
import controllers.reg.CompanyContactDetailsController
import fixtures.{CompanyContactDetailsFixture, UserDetailsFixture}
import helpers.SCRSSpec
import mocks.MetricServiceMock
import models.CompanyContactDetailsSuccessResponse
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.MessagesApi
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.MetricsService
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Name, ~}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class CompanyContactDetailsControllerSpec extends SCRSSpec with UserDetailsFixture with CompanyContactDetailsFixture
  with GuiceOneAppPerSuite with AuthBuilder {

  class Setup {
    val controller = new CompanyContactDetailsController {
      override lazy val controllerComponents = app.injector.instanceOf[MessagesControllerComponents]
      override val authConnector = mockAuthConnector
      override val s4LConnector = mockS4LConnector
      override val companyContactDetailsService = mockCompanyContactDetailsService
      override val metricsService: MetricsService = MetricServiceMock
      override val compRegConnector = mockCompanyRegistrationConnector
      override val keystoreConnector = mockKeystoreConnector
      implicit lazy val appConfig: FrontendAppConfig = app.injector.instanceOf[FrontendAppConfig]
      override lazy val messagesApi = app.injector.instanceOf[MessagesApi]
      override val scrsFeatureSwitches = mockSCRSFeatureSwitches
      implicit val ec: ExecutionContext = global
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
      when(mockCompanyRegistrationConnector.fetchCompanyName(any())(any())).thenReturn(Future.successful("foo"))
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CompanyContactDetailsServiceMocks.fetchContactDetails(validCompanyContactDetailsModel)

      showWithAuthorisedUser(controller.show) {
        result =>
          status(result) shouldBe OK
      }
    }

    "return a 200 when fetchContactDetails returns nothing from S4L but returns a model from UserDetailsService" in new Setup {
      CTRegistrationConnectorMocks.retrieveCTRegistration()
      when(mockCompanyRegistrationConnector.fetchCompanyName(any())(any())).thenReturn(Future.successful("foo"))
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CompanyContactDetailsServiceMocks.fetchContactDetails(validCompanyContactDetailsModel)

      showWithAuthorisedUser(controller.show) {
        result =>
          status(result) shouldBe OK
      }
    }
    "return exception if name is missing even though it is not used on the page anymore" in new Setup {
      val AuthDetailsNoName = new ~(
        Name(None, None),
        Some("fakeEmail")
      )
      intercept[Exception](showWithAuthorisedUserRetrieval(controller.show, AuthDetailsNoName) {
        result =>
          status(result) shouldBe OK
      })
    }
  }

  "submit" should {

    "return a 303 with a valid form and redirect to the accounting dates controller when the takeover feature switch is disabled" in new Setup {
      when(mockKeystoreConnector.fetchAndGet[String](any())(any(), any()))
        .thenReturn(Future.successful(Some("test")))
      CompanyContactDetailsServiceMocks.updateContactDetails(CompanyContactDetailsSuccessResponse(validCompanyContactDetailsResponse))
      when(mockCompanyContactDetailsService.checkIfAmendedDetails(any(), any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(false))
      when(mockCompanyContactDetailsService.updatePrePopContactDetails(any(), any())(any()))
        .thenReturn(Future.successful(true))
      mockTakeoversFeatureSwitch(isEnabled = false)

      val request = FakeRequest().withFormUrlEncodedBody(validCompanyContactDetailsFormData: _*)

      submitWithAuthorisedUserRetrieval(controller.submit, request, authDetailsAmend) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/register-your-company/when-start-business")
      }
    }

    "return a 303 with a valid form and redirect to the accounting dates controller when the takeover feature switch is enabled" in new Setup {
      when(mockKeystoreConnector.fetchAndGet[String](any())(any(), any()))
        .thenReturn(Future.successful(Some("test")))
      CompanyContactDetailsServiceMocks.updateContactDetails(CompanyContactDetailsSuccessResponse(validCompanyContactDetailsResponse))
      when(mockCompanyContactDetailsService.checkIfAmendedDetails(any(), any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(false))
      when(mockCompanyContactDetailsService.updatePrePopContactDetails(any(), any())(any()))
        .thenReturn(Future.successful(true))
      mockTakeoversFeatureSwitch(isEnabled = true)

      val request = FakeRequest().withFormUrlEncodedBody(validCompanyContactDetailsFormData: _*)

      submitWithAuthorisedUserRetrieval(controller.submit, request, authDetailsAmend) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(controllers.takeovers.routes.ReplacingAnotherBusinessController.show().url)
      }
    }

    "return a 400 with an invalid form" in new Setup {
      when(mockCompanyRegistrationConnector.fetchCompanyName(any())(any())).thenReturn(Future.successful("foo"))
      CTRegistrationConnectorMocks.retrieveCTRegistration()
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
    "return exception if name is missing even though it is not used on the page anymore" in new Setup {
      val authDetailsAmendNoName = new ~(
        new ~(
          new ~(
            Name(None, None),
            Some("fakeEmail")
          ), Credentials("credID", "provID")
        ), Some("extID")
      )
      intercept[Exception](submitWithAuthorisedUserRetrieval(controller.submit, FakeRequest().withFormUrlEncodedBody(invalidCompanyContactDetailsNameFormData: _*), authDetailsAmendNoName) {
        result =>
          status(result) shouldBe BAD_REQUEST
      })
    }
  }
}