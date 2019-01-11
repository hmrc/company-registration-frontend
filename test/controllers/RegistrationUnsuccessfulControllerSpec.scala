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
import connectors.KeystoreConnector
import controllers.reg.RegistrationUnsuccessfulController
import controllers.reg.RegistrationUnsuccessfulController.getConfString
import helpers.SCRSSpec
import org.mockito.Matchers
import org.mockito.Mockito._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.DeleteSubmissionService
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.test.WithFakeApplication

import scala.concurrent.Future


class RegistrationUnsuccessfulControllerSpec extends SCRSSpec with WithFakeApplication with AuthBuilder {


  class Setup {
    val controller = new RegistrationUnsuccessfulController {
      override val keystoreConnector = mockKeystoreConnector
      override val authConnector = mockAuthConnector
      override val deleteSubService = mockDeleteSubmissionService
      override val companyRegistrationConnector = mockCompanyRegistrationConnector
      override val appConfig = mockAppConfig
      override val registerCompanyGOVUKLink: String = "foobar"

    }
  }

  "RegistrationUnsuccessful Controller" should {
    "use the correct AuthConnector" in new Setup {
      controller.authConnector shouldBe a[AuthConnector]
    }

    "use the correct keystore Connector" in new Setup {
      controller.keystoreConnector shouldBe a[KeystoreConnector]
    }

    "use the correct Delete Submission service" in new Setup {
      controller.deleteSubService shouldBe a[DeleteSubmissionService]
    }
  }

  "show" should {
    "return a 200 when the address type is RO" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      when(mockDeleteSubmissionService.deleteSubmission(Matchers.eq("12345"))(Matchers.any[HeaderCarrier]()))
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
      when(mockDeleteSubmissionService.deleteSubmission(Matchers.eq("12345"))(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(true))
      when(mockKeystoreConnector.remove()(Matchers.any()))
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
      when(mockDeleteSubmissionService.deleteSubmission(Matchers.eq("12345"))(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(true))
      when(mockKeystoreConnector.remove()(Matchers.any()))
        .thenReturn(Future.successful(HttpResponse(200)))

      submitWithAuthorisedUser(controller.rejectionSubmit, FakeRequest().withFormUrlEncodedBody(Nil: _*)) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("foobar")
      }
    }
    "return a 500 if delete submission returns false" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      when(mockDeleteSubmissionService.deleteSubmission(Matchers.eq("12345"))(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(false))

      submitWithAuthorisedUser(controller.rejectionSubmit, FakeRequest().withFormUrlEncodedBody(Nil: _*)) {
        result =>
          status(result) shouldBe 500
      }
    }
    "return a exception if delete submission returns an exception" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      when(mockDeleteSubmissionService.deleteSubmission(Matchers.eq("12345"))(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.failed(new Exception("")))

      intercept[Exception](submitWithAuthorisedUser(controller.rejectionSubmit, FakeRequest().withFormUrlEncodedBody(Nil: _*)) {
        result => 1 shouldBe 0

      })
    }
  }
}