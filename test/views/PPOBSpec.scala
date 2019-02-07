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

package views

import _root_.connectors.BusinessRegistrationConnector
import _root_.helpers.SCRSSpec
import builders.AuthBuilder
import controllers.reg.PPOBController
import fixtures.PPOBFixture
import mocks.NavModelRepoMock
import models.{CHROAddress, NewAddress, PPOBChoice}
import org.jsoup.Jsoup
import org.mockito.Matchers
import org.mockito.Mockito.when
import play.api.i18n.MessagesApi
import play.api.test.Helpers._
import services.AddressLookupFrontendService
import uk.gov.hmrc.play.test.WithFakeApplication
import utils.JweCommon

import scala.concurrent.Future

class PPOBSpec extends SCRSSpec with PPOBFixture with NavModelRepoMock with WithFakeApplication with AuthBuilder {
  val mockNavModelRepoObj = mockNavModelRepo
  val mockBusinessRegConnector = mock[BusinessRegistrationConnector]
  val mockAddressLookupFrontendService = mock[AddressLookupFrontendService]

  class SetupPage {
    val controller = new PPOBController {
      override val authConnector = mockAuthConnector
      override val s4LConnector = mockS4LConnector
      override val addressLookupService = mockAddressLookupService
      override val keystoreConnector = mockKeystoreConnector
      override val compRegConnector = mockCompanyRegistrationConnector
      override val pPOBService = mockPPOBService
      override val handOffService = mockHandOffService
      override val businessRegConnector = mockBusinessRegConnector
      override val addressLookupFrontendService = mockAddressLookupFrontendService
      implicit val messagesApi: MessagesApi = fakeApplication.injector.instanceOf[MessagesApi]
      override val appConfig = mockAppConfig
      override val jwe: JweCommon = mockJweCommon
    }
  }

  "PPOBController.show" should {
    "make sure that PPOB page has the correct elements when an RO address is provided to the view" in new SetupPage {
      CTRegistrationConnectorMocks.retrieveCTRegistration()
      mockKeystoreFetchAndGet("registrationID", Some("12345"))

      when(mockPPOBService.fetchAddressesAndChoice(Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(Some(CHROAddress("38", "line 1", None, "Telford", "UK", None, None, None)), Some(NewAddress("line 1", "line 2", None, None, None, None, None)), PPOBChoice("")))

      showWithAuthorisedUser(controller.show) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "What is the company's 'principal place of business'?"
          document.getElementById("main-heading").text shouldBe "What is the company's 'principal place of business'?"
          document.getElementById("next").attr("value") shouldBe "Save and continue"
      }
    }
    "make sure that PPOB page has the correct elements when an RO address is not provided to the view" in new SetupPage {
      CTRegistrationConnectorMocks.retrieveCTRegistration()
      mockKeystoreFetchAndGet("registrationID", Some("12345"))

      when(mockPPOBService.fetchAddressesAndChoice(Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(None, Some(NewAddress("line 1", "line 2", None, None, None, None, None)), PPOBChoice("")))

      showWithAuthorisedUser(controller.show) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "What is the company's 'principal place of business'?"
          document.getElementById("main-heading").text shouldBe "What is the company's 'principal place of business'?"
          document.getElementById("next").attr("value") shouldBe "Save and continue"
      }
    }
  }
}
