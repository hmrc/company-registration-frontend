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
import config.FrontendAuthConnector
import controllers.reg.ConfirmationController
import fixtures.CompanyDetailsFixture
import helpers.SCRSSpec
import models.connectors.ConfirmationReferences
import models.{ConfirmationReferencesErrorResponse, ConfirmationReferencesSuccessResponse}
import org.mockito.Matchers
import play.api.test.FakeRequest
import play.api.test.Helpers._
import org.mockito.Mockito._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class ConfirmationControllerSpec extends SCRSSpec with CompanyDetailsFixture {

  class Setup {
    val controller = new ConfirmationController {
      override val authConnector = mockAuthConnector
      override val compRegConnector = mockCompanyRegistrationConnector
      override val keystoreConnector = mockKeystoreConnector
    }
  }

  "ConfirmationController" should {
    "use the correct AuthConnector" in {
      ConfirmationController.authConnector shouldBe FrontendAuthConnector
    }
  }

  "show" should {

    val regId = "reg12345"
    implicit val hc = HeaderCarrier()

    "Return a 200 and display the Confirmation page" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("testRegID"))
      CTRegistrationConnectorMocks.fetchAcknowledgementReference("testRegID", ConfirmationReferencesSuccessResponse(ConfirmationReferences("a","b","c","ABCDEFG0000")))
      when(mockCompanyRegistrationConnector.retrieveCompanyDetails(Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(Some(validCompanyDetailsResponse)))

      AuthBuilder.showWithAuthorisedUser(controller.show, mockAuthConnector){
        result =>
          status(result) shouldBe OK
      }
    }

    "return a 500 when an acknowledgement ref could not be found" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("testRegID"))
      CTRegistrationConnectorMocks.fetchAcknowledgementReference("testRegID", ConfirmationReferencesErrorResponse)

      AuthBuilder.showWithAuthorisedUser(controller.show, mockAuthConnector){
        result =>
          status(result) shouldBe INTERNAL_SERVER_ERROR
      }
    }
  }

  "submit" should {
    "Return a 200" in new Setup {

      AuthBuilder.submitWithAuthorisedUser(controller.submit, mockAuthConnector, FakeRequest().withFormUrlEncodedBody(Nil: _*)) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/register-your-company/dashboard")
      }
    }
  }
}
