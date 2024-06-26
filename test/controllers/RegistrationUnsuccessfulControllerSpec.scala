/*
 * Copyright 2023 HM Revenue & Customs
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

import scala.concurrent.{ExecutionContext, Future}
import views.html.reg.{RegistrationUnsuccessful => RegistrationUnsuccessfulView}

class RegistrationUnsuccessfulControllerSpec extends SCRSSpec with GuiceOneAppPerSuite with AuthBuilder {
  lazy val mockMcc = app.injector.instanceOf[MessagesControllerComponents]
  lazy val mockRegistrationUnsuccessfulView = app.injector.instanceOf[RegistrationUnsuccessfulView]
  override lazy val mockAppConfig = app.injector.instanceOf[AppConfig]

  lazy val registerCompanyGOVUKLink: String = "https://www.gov.uk/limited-company-formation/register-your-company"

  class Setup {

    val controller = new RegistrationUnsuccessfulController (
      mockAuthConnector,
      mockKeystoreConnector,
      mockCompanyRegistrationConnector,
      mockDeleteSubmissionService,
      mockMcc,
      mockRegistrationUnsuccessfulView
    )(
      mockAppConfig,
      ec
    )
  }
  "show" should {
    "return a 200 when the address type is RO" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      when(mockDeleteSubmissionService.deleteSubmission(ArgumentMatchers.eq("12345"))(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[ExecutionContext]()))
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
      when(mockDeleteSubmissionService.deleteSubmission(ArgumentMatchers.eq("12345"))(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[ExecutionContext]()))
        .thenReturn(Future.successful(true))
      when(mockKeystoreConnector.remove()(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(200, "")))

      submitWithAuthorisedUser(controller.submit, FakeRequest().withFormUrlEncodedBody(Nil: _*)) {
        result =>
          status(result) mustBe SEE_OTHER
          redirectLocation(result) mustBe Some("/register-your-company/post-sign-in")
      }
    }
  }
}