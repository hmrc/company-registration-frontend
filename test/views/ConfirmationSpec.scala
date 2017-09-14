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

package views

import _root_.helpers.SCRSSpec
import builders.AuthBuilder
import controllers.reg.ConfirmationController
import fixtures.CompanyDetailsFixture
import models.connectors.ConfirmationReferences
import models.ConfirmationReferencesSuccessResponse
import org.jsoup.Jsoup
import org.mockito.Matchers
import org.mockito.Mockito._
import play.api.i18n.MessagesApi
import play.api.test.Helpers._
import services.DeskproService

import scala.concurrent.Future

class ConfirmationSpec extends SCRSSpec with CompanyDetailsFixture {

  val mockDeskproService = mock[DeskproService]

  class SetupPage {
    val controller = new ConfirmationController {
      override val authConnector = mockAuthConnector
      override val companyRegistrationConnector = mockCompanyRegistrationConnector
      override val keystoreConnector = mockKeystoreConnector
      override val deskproService = mockDeskproService
      implicit val messagesApi: MessagesApi = fakeApplication.injector.instanceOf[MessagesApi]
    }
  }

  "Confirmation.show" should {
    "make sure that the confirmation page has the correct elements" in new SetupPage {
      mockKeystoreFetchAndGet("registrationID", Some("testRegID"))
      val payment = "pay"
      val paymentRef = "pref"
      val ackref = "ABCD00000000123"
      val txId = "tx123"
      CTRegistrationConnectorMocks.fetchAcknowledgementReference("testRegID", ConfirmationReferencesSuccessResponse(ConfirmationReferences(txId,paymentRef,payment,ackref)))
      when(mockCompanyRegistrationConnector.retrieveCompanyDetails(Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(Some(validCompanyDetailsResponse)))

      AuthBuilder.showWithAuthorisedUser(controller.show, mockAuthConnector) {
        result =>
          val document = Jsoup.parse(contentAsString(result))

          document.title() shouldBe "Application submitted"

          Map(
            "heading-application-submitted" -> "Application submitted",
            "ltd-ref" -> s"applied to set up a limited company (reference number ${txId})",
            "ct-ref" -> s"applied to be registered for Corporation Tax (reference number ${ackref})",
            "payment-ref" -> s"made a payment of £${payment} (reference number ${paymentRef})",
            "next-steps" -> "What happens next?"
          ) foreach { case (element, message) =>
            document.getElementById(element).text() shouldBe message
          }
      }
    }
  }
}
