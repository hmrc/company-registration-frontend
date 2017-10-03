/*
 * Copyright 2017 HM Revenue & Customs
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
import helpers.SCRSSpec
import models.UserIDs
import org.mockito.Matchers
import play.api.test.FakeRequest
import services.DeleteSubmissionService
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpReads, HttpResponse}
import play.api.test.Helpers._
import org.mockito.Mockito._
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.test.WithFakeApplication

import scala.concurrent.Future

class RegistrationUnsuccessfulControllerSpec extends SCRSSpec with WithFakeApplication {

  class Setup {
    val controller = new RegistrationUnsuccessfulController {
      override val keystoreConnector = mockKeystoreConnector
      override val authConnector = mockAuthConnector
      override val deleteSubService = mockDeleteSubmissionService
      override val companyRegistrationConnector = mockCompanyRegistrationConnector
    }
  }

  "RegistrationUnsuccessful Controller" should {
    "use the correct AuthConnector" in new Setup {
      controller.authConnector shouldBe a [AuthConnector]
    }

    "use the correct keystore Connector" in new Setup {
      controller.keystoreConnector shouldBe a [KeystoreConnector]
    }

    "use the correct Delete Submission service" in new Setup {
      controller.deleteSubService shouldBe a [DeleteSubmissionService]
    }
  }

  "show" should {
    "return a 200 when the address type is RO" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      when(mockAuthConnector.getIds[UserIDs](Matchers.any())(Matchers.any[HeaderCarrier](), Matchers.any[HttpReads[UserIDs]]()))
        .thenReturn(Future.successful(UserIDs("1", "2")))
      when(mockDeleteSubmissionService.deleteSubmission(Matchers.eq("12345"))(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(true))

      AuthBuilder.showWithAuthorisedUser(controller.show, mockAuthConnector){
        result =>
          status(result) shouldBe OK
      }
    }
  }

  "submit" should {
    "return a 303" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      when(mockAuthConnector.getIds[UserIDs](Matchers.any())(Matchers.any[HeaderCarrier](), Matchers.any[HttpReads[UserIDs]]()))
        .thenReturn(Future.successful(UserIDs("1", "2")))
      when(mockDeleteSubmissionService.deleteSubmission(Matchers.eq("12345"))(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(true))
      when(mockKeystoreConnector.remove()(Matchers.any()))
        .thenReturn(Future.successful(HttpResponse(200)))

      AuthBuilder.submitWithAuthorisedUser(controller.submit, mockAuthConnector, FakeRequest().withFormUrlEncodedBody(Nil: _*)){
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/register-your-company/post-sign-in")
      }
    }
  }
}
