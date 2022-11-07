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
import controllers.reg.{ConfirmationController, ControllerErrorHandler}
import fixtures.CompanyDetailsFixture
import helpers.SCRSSpec
import models.ConfirmationReferencesSuccessResponse
import models.connectors.ConfirmationReferences
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status
import play.api.i18n.Messages
import play.api.mvc.{MessagesControllerComponents, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentType, _}
import services.DeskproService
import uk.gov.hmrc.http.HeaderCarrier
import views.html.reg.{Confirmation => ConfirmationView}
import views.html.errors.{submissionFailed => submissionFailedView}
import views.html.errors.{deskproSubmitted => deskproSubmittedView}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class ConfirmationControllerSpec(implicit val messages: Messages) extends SCRSSpec with CompanyDetailsFixture with GuiceOneAppPerSuite with AuthBuilder {

  lazy val mockDeskproService = mock[DeskproService]
  lazy val mockMcc = app.injector.instanceOf[MessagesControllerComponents]
  lazy val mockControllerErrorHandler = app.injector.instanceOf[ControllerErrorHandler]
  lazy val mockConfirmationView = app.injector.instanceOf[ConfirmationView]
  lazy val mockSubmissionFailedView = app.injector.instanceOf[submissionFailedView]
  lazy val mockDeskproSubmittedView = app.injector.instanceOf[deskproSubmittedView]

  class Setup {
    val controller = new ConfirmationController (
      mockAuthConnector,
      mockCompanyRegistrationConnector,
      mockKeystoreConnector,
      mockDeskproService,
      mockMcc,
      mockControllerErrorHandler,
      mockConfirmationView,
      mockSubmissionFailedView,
      mockDeskproSubmittedView
    )
    (mockAppConfig,global)
  }

  val regId = "reg12345"
  val ticketId: Long = 123456789
  val testUri = Some("uri")

  "show" should {
    implicit val hc = HeaderCarrier()

    "Return a 200 and display the Confirmation page" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("testRegID"))
      CTRegistrationConnectorMocks.fetchAcknowledgementReference("testRegID", ConfirmationReferencesSuccessResponse(ConfirmationReferences("a", Some("b"), Some("c"), "ABCDEFG0000")))
      when(mockCompanyRegistrationConnector.retrieveCompanyDetails(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(validCompanyDetailsResponse)))

      showWithAuthorisedUser(controller.show) {
        result =>
          status(result) mustBe OK
      }
    }
  }

  "submitTicket" should {
    "return 400 when an empty form is submitted" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("testRegID"))

      val request = FakeRequest().withFormUrlEncodedBody(
        "" -> ""
      )

      submitWithAuthorisedUserRetrieval(controller.submitTicket, request, testUri) {
        result =>
          status(result) mustBe BAD_REQUEST
      }
    }

    "return 400 when an invalid email is entered" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("testRegID"))

      val request = FakeRequest().withFormUrlEncodedBody(
        "name" -> "Michael Mouse",
        "email" -> "************",
        "message" -> "I can't provide a good email address"
      )

      submitWithAuthorisedUserRetrieval(controller.submitTicket, request, testUri) {
        result =>
          status(result) mustBe BAD_REQUEST
      }
    }

    "return 303" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some(regId))

      val request = FakeRequest().withFormUrlEncodedBody(
        "name" -> "Michael Mouse",
        "email" -> "mic@mou.biz",
        "message" -> "I can't provide a good email address"
      )

      when(mockDeskproService.submitTicket(ArgumentMatchers.eq(regId), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(ticketId))

      submitWithAuthorisedUserRetrieval(controller.submitTicket, request, testUri) {
        result =>
          status(result) mustBe SEE_OTHER
          redirectLocation(result) mustBe Some("/register-your-company/ticket-submitted")
      }
    }
  }

  "submittedTicket" should {
    "return 200" in new Setup {
      showWithAuthorisedUser(controller.submittedTicket) {
        (result: Future[Result]) =>
          status(result) mustBe Status.OK
          contentType(result) mustBe Some("text/html")
          charset(result) mustBe Some("utf-8")
      }
    }
  }

  "submit" should {
    "Return a 200" in new Setup {

      submitWithAuthorisedUser(controller.submit, FakeRequest().withFormUrlEncodedBody(Nil: _*)) {
        result =>
          status(result) mustBe SEE_OTHER
          redirectLocation(result) mustBe Some("/register-your-company/company-registration-overview")
      }
    }
  }

  "deskproPage" should {
    "Return a 200" in new Setup {
      pendingUntilFixed {
        submitWithAuthorisedUser(controller.deskproPage, FakeRequest().withFormUrlEncodedBody(Nil: _*)) {
          result =>
            status(result) mustBe OK
            contentAsString(result) must include(messages("errorPages.failedSubmission.header"))
        }
      }
    }
  }
}