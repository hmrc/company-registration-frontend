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
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.{Lang, Langs, Messages}
import play.api.mvc.MessagesControllerComponents
import play.api.test.Helpers._
import services.AddressLookupFrontendService
import utils.JweCommon

import scala.concurrent.Future

class PPOBSpec()(implicit lang: Lang) extends SCRSSpec with PPOBFixture with NavModelRepoMock with GuiceOneAppPerSuite with AuthBuilder {
  val mockNavModelRepoObj = mockNavModelRepo
  val mockBusinessRegConnector = mock[BusinessRegistrationConnector]
  val mockAddressLookupFrontendService = mock[AddressLookupFrontendService]
  val mockMessages = app.injector.instanceOf[Messages]
  implicit val langs = app.injector.instanceOf[Langs]

  class SetupPage {
    val controller = new PPOBController {
      override val controllerComponents = app.injector.instanceOf[MessagesControllerComponents]
      override val authConnector = mockAuthConnector
      override val s4LConnector = mockS4LConnector
      override val keystoreConnector = mockKeystoreConnector
      override val compRegConnector = mockCompanyRegistrationConnector
      override val pPOBService = mockPPOBService
      override val handOffService = mockHandOffService
      override val businessRegConnector = mockBusinessRegConnector
      override val addressLookupFrontendService = mockAddressLookupFrontendService
      override val appConfig = mockAppConfig
      override val jwe: JweCommon = mockJweCommon
      implicit val messages = mockMessages
    }
  }

  "PPOBController.show" should {
    "make sure that the PPOB page has the correct elements when only an RO address is provided to the view" in new SetupPage {
      CTRegistrationConnectorMocks.retrieveCTRegistration()
      mockKeystoreFetchAndGet("registrationID", Some("12345"))

      when(mockPPOBService.fetchAddressesAndChoice(Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(Some(CHROAddress("38", "line 1", Some("line 2"), "Telford", "UK", None, Some("ZZ1 1ZZ"), None)), None, PPOBChoice("")))


      showWithAuthorisedUser(controller.show) {
        result =>
          val document = Jsoup.parse(contentAsString(result))

          document.title should include("What is the company's 'principal place of business'?")
          document.getElementsByTag("h1").first().text shouldBe "What is the company's 'principal place of business'?"
          document.getElementById("next").attr("value") shouldBe "Save and continue"

          document.getElementById("addressChoice").getElementsByAttributeValue("for","addressChoice-ro").text() shouldBe "38 line 1, line 2, Telford ZZ1 1ZZ, UK"
          document.getElementById("addressChoice").getElementsByAttributeValue("for","addressChoice-ppob") shouldBe empty
          document.getElementById("addressChoice").getElementsByAttributeValue("for","addressChoice-other").text() shouldBe "A different address"
      }
    }
    "make sure that the PPOB page has the correct elements when only an RO and different PPOB address is provided to the view" in new SetupPage {
      CTRegistrationConnectorMocks.retrieveCTRegistration()
      mockKeystoreFetchAndGet("registrationID", Some("12345"))

      when(mockPPOBService.fetchAddressesAndChoice(Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(Some(CHROAddress("38", "line 1", Some("line 2"), "Telford", "UK", None, Some("ZZ1 1ZZ"), None)),
                                           Some(NewAddress("line 1", "line 2", None, None, Some("ZZ2 2ZZ"), None, None)),
                                           PPOBChoice("PPOB")))

      showWithAuthorisedUser(controller.show) {
        result =>
          val document = Jsoup.parse(contentAsString(result))

          document.title should include("What is the company's 'principal place of business'?")
          document.getElementsByTag("h1").first().text shouldBe "What is the company's 'principal place of business'?"
          document.getElementById("next").attr("value") shouldBe "Save and continue"

          document.getElementById("addressChoice").getElementsByAttributeValue("for","addressChoice-ro").text() shouldBe "38 line 1, line 2, Telford ZZ1 1ZZ, UK"
          document.getElementById("addressChoice").getElementsByAttributeValue("for","addressChoice-ppob").text() shouldBe "line 1, line 2, ZZ2 2ZZ"
          document.getElementById("addressChoice").getElementsByAttributeValue("for","addressChoice-other").text() shouldBe "A different address"
      }
    }

    "make sure that the PPOB page has the correct elements when the RO address was selected before" in new SetupPage {
      CTRegistrationConnectorMocks.retrieveCTRegistration()
      mockKeystoreFetchAndGet("registrationID", Some("12345"))

      when(mockPPOBService.fetchAddressesAndChoice(Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(Some(CHROAddress("14", "St Test Walk", Some("Testley"), "Testford", "UK", None, Some("TE1 1ST"), Some("Testshire"))),
          Some(NewAddress("14 St Test Walk", "Testley", Some("Testford"), Some("Testshire"), Some("TE1 1ST"), Some("UK"), Some("txid"))),
          PPOBChoice("RO")))

      showWithAuthorisedUser(controller.show) {
        result =>
          val document = Jsoup.parse(contentAsString(result))

          document.title should include("What is the company's 'principal place of business'?")
          document.getElementsByTag("h1").first().text shouldBe "What is the company's 'principal place of business'?"
          document.getElementById("next").attr("value") shouldBe "Save and continue"

          document.getElementById("addressChoice").getElementsByAttributeValue("for","addressChoice-ro").text() shouldBe "14 St Test Walk, Testley, Testford, Testshire, TE1 1ST, UK"
          document.getElementById("addressChoice").getElementsByAttributeValue("for","addressChoice-ppob") shouldBe empty
          document.getElementById("addressChoice").getElementsByAttributeValue("for","addressChoice-other").text() shouldBe "A different address"
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
          document.title should include("What is the company's 'principal place of business'?")
          document.getElementsByTag("h1").first.text shouldBe "What is the company's 'principal place of business'?"
          document.getElementById("next").attr("value") shouldBe "Save and continue"
      }
    }
  }
}
