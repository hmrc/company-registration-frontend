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
import controllers.reg.{CompanyContactDetailsController, ControllerErrorHandler}
import fixtures.{CompanyContactDetailsFixture, UserDetailsFixture}
import helpers.SCRSSpec
import mocks.MetricServiceMock
import models.CompanyContactDetailsSuccessResponse
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Name, ~}
import views.html.reg.{CompanyContactDetails => CompanyContactDetailsView}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CompanyContactDetailsControllerSpec extends SCRSSpec with UserDetailsFixture with CompanyContactDetailsFixture
  with GuiceOneAppPerSuite with AuthBuilder {

  lazy val mockMcc = app.injector.instanceOf[MessagesControllerComponents]
  lazy val mockControllerErrorHandler = app.injector.instanceOf[ControllerErrorHandler]
  lazy val mockCompanyContactDetailsView = app.injector.instanceOf[CompanyContactDetailsView]
  lazy implicit val appConfig = app.injector.instanceOf[AppConfig]

  class Setup {
    val controller = new CompanyContactDetailsController (
      mockAuthConnector,
      mockS4LConnector,
      MetricServiceMock,
      mockCompanyRegistrationConnector,
      mockKeystoreConnector,
      mockCompanyContactDetailsService,
      mockSCRSFeatureSwitches,
      mockMcc,
      mockControllerErrorHandler,
      mockCompanyContactDetailsView
    )
    (
      appConfig,
      global
    )
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
      ), Some(Credentials("credID", "provID"))
    ), Some("extID")
  )

  "show" should {
    "return a 200 when fetchContactDetails returns a model from S4L" in new Setup {
      CTRegistrationConnectorMocks.retrieveCTRegistration()
      when(mockCompanyRegistrationConnector.fetchCompanyName(any())(any(), any())).thenReturn(Future.successful("foo"))
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CompanyContactDetailsServiceMocks.fetchContactDetails(validCompanyContactDetailsModel)

      showWithAuthorisedUser(controller.show) {
        result =>
          status(result) mustBe OK
      }
    }

    "return a 200 when fetchContactDetails returns nothing from S4L but returns a model from UserDetailsService" in new Setup {
      CTRegistrationConnectorMocks.retrieveCTRegistration()
      when(mockCompanyRegistrationConnector.fetchCompanyName(any())(any(), any())).thenReturn(Future.successful("foo"))
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CompanyContactDetailsServiceMocks.fetchContactDetails(validCompanyContactDetailsModel)

      showWithAuthorisedUser(controller.show) {
        result =>
          status(result) mustBe OK
      }
    }
    "return exception if name is missing even though it is not used on the page anymore" in new Setup {
      val AuthDetailsNoName = new ~(
        Name(None, None),
        Some("fakeEmail")
      )
      intercept[Exception](showWithAuthorisedUserRetrieval(controller.show, AuthDetailsNoName) {
        result =>
          status(result) mustBe OK
      })
    }
  }

  "submit" should {
    "return a 303 with a valid form and redirect to the accounting dates controller" in new Setup {
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
          status(result) mustBe SEE_OTHER
          redirectLocation(result) mustBe Some(controllers.takeovers.routes.ReplacingAnotherBusinessController.show.url)
      }
    }

    "return a 400 with an invalid form" in new Setup {
      when(mockCompanyRegistrationConnector.fetchCompanyName(any())(any(), any())).thenReturn(Future.successful("foo"))
      CTRegistrationConnectorMocks.retrieveCTRegistration()
      when(mockKeystoreConnector.fetchAndGet[String](any())(any(), any()))
        .thenReturn(Future.successful(Some("test")))
      when(mockCompanyContactDetailsService.updatePrePopContactDetails(any(), any())(any()))
        .thenReturn(Future.successful(true))

      val request = FakeRequest().withFormUrlEncodedBody(invalidCompanyContactDetailsNameFormData: _*)

      submitWithAuthorisedUserRetrieval(controller.submit, request, authDetailsAmend) {
        result =>
          status(result) mustBe BAD_REQUEST
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
          status(result) mustBe BAD_REQUEST
      })
    }
  }
}