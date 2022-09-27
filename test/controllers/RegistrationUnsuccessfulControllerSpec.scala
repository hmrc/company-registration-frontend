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
import controllers.reg.RegistrationUnsuccessfulController
import helpers.SCRSSpec
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import views.html.reg.{RegistrationUnsuccessful => RegistrationUnsuccessfulView}
import views.html.errors.{incorporationRejected => IncorporationRejectedView}

class RegistrationUnsuccessfulControllerSpec extends SCRSSpec with GuiceOneAppPerSuite with AuthBuilder {
  lazy val mockMcc = app.injector.instanceOf[MessagesControllerComponents]
  lazy val mockRegistrationUnsuccessfulView = app.injector.instanceOf[RegistrationUnsuccessfulView]
  lazy val mockIncorporationRejectedView = app.injector.instanceOf[IncorporationRejectedView]
  override lazy val mockAppConfig = app.injector.instanceOf[AppConfig]

  lazy val registerCompanyGOVUKLink: String = "https://www.gov.uk/limited-company-formation/register-your-company"

  class Setup {

    val controller = new RegistrationUnsuccessfulController (
      mockAuthConnector,
      mockKeystoreConnector,
      mockCompanyRegistrationConnector,
      mockDeleteSubmissionService,
      mockMcc,
      mockRegistrationUnsuccessfulView,
      mockIncorporationRejectedView
    )(
      mockAppConfig,
      global
    )
  }
  "show" should {
    "return a 200 when the address type is RO" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      when(mockDeleteSubmissionService.deleteSubmission(ArgumentMatchers.eq("12345"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(true))

      showWithAuthorisedUser(controller.show) {
        result =>
          status(result) mustBe OK
      }
    }
  }

  "submit" should {
    "return a 303" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      when(mockDeleteSubmissionService.deleteSubmission(ArgumentMatchers.eq("12345"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(true))
      when(mockKeystoreConnector.remove()(ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(200)))

      submitWithAuthorisedUser(controller.submit, FakeRequest().withFormUrlEncodedBody(Nil: _*)) {
        result =>
          status(result) mustBe SEE_OTHER
          redirectLocation(result) mustBe Some("/register-your-company/post-sign-in")
      }
    }
  }

  "rejectionShow" should {
    "return 200" in new Setup {
      showWithAuthorisedUser(controller.rejectionShow) {
        result =>
          status(result) mustBe OK
      }
    }
  }
  "rejectionSubmit" should {
    "return a 303" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      when(mockDeleteSubmissionService.deleteSubmission(ArgumentMatchers.eq("12345"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(true))
      when(mockKeystoreConnector.remove()(ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(200)))

      submitWithAuthorisedUser(controller.rejectionSubmit, FakeRequest().withFormUrlEncodedBody(Nil: _*)) {
        result =>
          status(result) mustBe SEE_OTHER
          redirectLocation(result) mustBe Some(registerCompanyGOVUKLink)
      }
    }
    "return a 500 if delete submission returns false" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      when(mockDeleteSubmissionService.deleteSubmission(ArgumentMatchers.eq("12345"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(false))

      submitWithAuthorisedUser(controller.rejectionSubmit, FakeRequest().withFormUrlEncodedBody(Nil: _*)) {
        result =>
          status(result) mustBe 500
      }
    }
    "return a exception if delete submission returns an exception" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      when(mockDeleteSubmissionService.deleteSubmission(ArgumentMatchers.eq("12345"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.failed(new Exception("")))

      intercept[Exception](submitWithAuthorisedUser(controller.rejectionSubmit, FakeRequest().withFormUrlEncodedBody(Nil: _*)) {
        result => 1 mustBe 0

      })
    }
  }
}