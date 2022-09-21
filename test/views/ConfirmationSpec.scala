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

package views

import _root_.helpers.SCRSSpec
import builders.AuthBuilder
import controllers.reg.{ConfirmationController, ControllerErrorHandler}
import fixtures.CompanyDetailsFixture
import models.ConfirmationReferencesSuccessResponse
import models.connectors.ConfirmationReferences
import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.mvc.MessagesControllerComponents
import play.api.test.Helpers._
import services.DeskproService
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import views.html.reg.{Confirmation => ConfirmationView}
import views.html.errors.{submissionFailed => submissionFailedView}
import views.html.errors.{deskproSubmitted => deskproSubmittedView}

class ConfirmationSpec extends SCRSSpec with CompanyDetailsFixture with GuiceOneAppPerSuite with AuthBuilder {

  val mockDeskproService = mock[DeskproService]
  lazy val mockControllerComponents = app.injector.instanceOf[MessagesControllerComponents]
  lazy val mockControllerErrorHandler = app.injector.instanceOf[ControllerErrorHandler]
  lazy val mockConfirmationView = app.injector.instanceOf[ConfirmationView]
  lazy val mockSubmissionFailedView = app.injector.instanceOf[submissionFailedView]
  lazy val mockDeskproSubmittedView = app.injector.instanceOf[deskproSubmittedView]

  class SetupPage {
    val controller = new ConfirmationController (
      mockAuthConnector,
      mockCompanyRegistrationConnector,
      mockKeystoreConnector,
      mockDeskproService,
      mockControllerComponents,
      mockControllerErrorHandler,
      mockConfirmationView,
      mockSubmissionFailedView,
      mockDeskproSubmittedView
    )
    (
      mockAppConfig,
      global
    )

  }

  "Confirmation.show" should {
    "make sure that the confirmation page has the correct elements" in new SetupPage {
      mockKeystoreFetchAndGet("registrationID", Some("testRegID"))
      val payment = "pay"
      val paymentRef = "pref"
      val ackref = "ABCD00000000123"
      val txId = "tx123"
      CTRegistrationConnectorMocks.fetchAcknowledgementReference("testRegID", ConfirmationReferencesSuccessResponse(ConfirmationReferences(txId,Some(paymentRef),Some(payment),ackref)))
      when(mockCompanyRegistrationConnector.retrieveCompanyDetails(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(validCompanyDetailsResponse)))

      showWithAuthorisedUser(controller.show) {
        result =>
          val document = Jsoup.parse(contentAsString(result))

          document.title should include("Application submitted")

          Map(
            "next-steps" -> "What happens next"
          ) foreach { case (element, message) =>
            document.getElementById(element).text() shouldBe message
          }
      }
    }
  }
}
