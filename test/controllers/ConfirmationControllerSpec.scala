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
import controllers.reg.ConfirmationController
import fixtures.CompanyDetailsFixture
import helpers.SCRSSpec
import models.connectors.ConfirmationReferences
import models.{ConfirmationReferencesSuccessResponse, DESFailureDeskpro, DESFailureRetriable}
import org.mockito.Matchers
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentType, _}
import org.mockito.Mockito._
import play.api.http.Status
import play.api.i18n.MessagesApi
import play.api.mvc.Result
import services.DeskproService
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.WithFakeApplication
import utils.Messages

import scala.concurrent.Future

class ConfirmationControllerSpec extends SCRSSpec with CompanyDetailsFixture with WithFakeApplication {

  val mockDeskproService = mock[DeskproService]

  class Setup {
    val controller = new ConfirmationController {
      override val authConnector = mockAuthConnector
      override val companyRegistrationConnector = mockCompanyRegistrationConnector
      override val keystoreConnector = mockKeystoreConnector
      override val deskproService = mockDeskproService
      implicit val messagesApi: MessagesApi = fakeApplication.injector.instanceOf[MessagesApi]
    }
  }

  val regId = "reg12345"
  val ticketId : Long = 123456789

  "show" should {
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
  }

  "submitTicket" should {
    "return 400 when an empty form is submitted" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("testRegID"))

      val request = FakeRequest().withFormUrlEncodedBody(
        "" -> ""
      )

      AuthBuilder.submitWithAuthorisedUser(controller.submitTicket, mockAuthConnector, request) {
        result =>
          status(result) shouldBe BAD_REQUEST
      }
    }

    "return 400 when an invalid email is entered" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("testRegID"))

      val request = FakeRequest().withFormUrlEncodedBody(
        "name" -> "Michael Mouse",
        "email" -> "************",
        "message" -> "I can't provide a good email address"
      )

      AuthBuilder.submitWithAuthorisedUser(controller.submitTicket, mockAuthConnector, request) {
        result =>
          status(result) shouldBe BAD_REQUEST
      }
    }

    "return 303" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some(regId))

      val request = FakeRequest().withFormUrlEncodedBody(
        "name" -> "Michael Mouse",
        "email" -> "mic@mou.biz",
        "message" -> "I can't provide a good email address"
      )

      when(mockDeskproService.submitTicket(Matchers.eq(regId), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(ticketId))

      AuthBuilder.submitWithAuthorisedUser(controller.submitTicket, mockAuthConnector, request) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/register-your-company/ticket-submitted")
      }
    }
  }

  "submittedTicket" should {
    "return 200" in new Setup {
      AuthBuilder.showWithAuthorisedUser(controller.submittedTicket, mockAuthConnector) {
        (result: Future[Result]) =>
          status(result) shouldBe Status.OK
          contentType(result) shouldBe Some("text/html")
          charset(result) shouldBe Some("utf-8")
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

  "resubmitPage" should {
    "Return a 200" in new Setup {

      AuthBuilder.submitWithAuthorisedUser(controller.resubmitPage, mockAuthConnector, FakeRequest().withFormUrlEncodedBody(Nil: _*)) {
        result =>
          status(result) shouldBe OK
          contentAsString(result) should include(Messages("errorPages.retrySubmission.p2"))
      }
    }
  }

  "deskproPage" should {
    "Return a 200" in new Setup {

      AuthBuilder.submitWithAuthorisedUser(controller.deskproPage, mockAuthConnector, FakeRequest().withFormUrlEncodedBody(Nil: _*)) {
        result =>
          status(result) shouldBe OK
          contentAsString(result) should include(Messages("errorPages.failedSubmission.header"))
      }
    }
  }
}
