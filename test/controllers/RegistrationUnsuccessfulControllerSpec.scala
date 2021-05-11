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
import controllers.reg.RegistrationUnsuccessfulController
import helpers.SCRSSpec
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.MessagesApi
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class RegistrationUnsuccessfulControllerSpec extends SCRSSpec with GuiceOneAppPerSuite with AuthBuilder {

  class Setup {
    val controller = new RegistrationUnsuccessfulController {
      override val controllerComponents = app.injector.instanceOf[MessagesControllerComponents]
      override val keystoreConnector = mockKeystoreConnector
      override val authConnector = mockAuthConnector
      override val deleteSubService = mockDeleteSubmissionService
      override val compRegConnector = mockCompanyRegistrationConnector
      implicit val appConfig: FrontendAppConfig = app.injector.instanceOf[FrontendAppConfig]
      override val registerCompanyGOVUKLink: String = "foobar"
      override val messagesApi = app.injector.instanceOf[MessagesApi]
      implicit val ec: ExecutionContext = global
    }
  }

  "show" should {
    "return a 200 when the address type is RO" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      when(mockDeleteSubmissionService.deleteSubmission(ArgumentMatchers.eq("12345"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(true))

      showWithAuthorisedUser(controller.show) {
        result =>
          status(result) shouldBe OK
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
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/register-your-company/post-sign-in")
      }
    }
  }

  "rejectionShow" should {
    "return 200" in new Setup {
      showWithAuthorisedUser(controller.rejectionShow) {
        result =>
          status(result) shouldBe OK
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
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("foobar")
      }
    }
    "return a 500 if delete submission returns false" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      when(mockDeleteSubmissionService.deleteSubmission(ArgumentMatchers.eq("12345"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(false))

      submitWithAuthorisedUser(controller.rejectionSubmit, FakeRequest().withFormUrlEncodedBody(Nil: _*)) {
        result =>
          status(result) shouldBe 500
      }
    }
    "return a exception if delete submission returns an exception" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      when(mockDeleteSubmissionService.deleteSubmission(ArgumentMatchers.eq("12345"))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.failed(new Exception("")))

      intercept[Exception](submitWithAuthorisedUser(controller.rejectionSubmit, FakeRequest().withFormUrlEncodedBody(Nil: _*)) {
        result => 1 shouldBe 0

      })
    }
  }
}