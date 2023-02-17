/*
 * Copyright 2023 HM Revenue & Customs
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
import controllers.handoff.HandOffUtils
import controllers.reg.{ControllerErrorHandler, PPOBController}
import fixtures.PPOBFixture
import mocks.NavModelRepoMock
import models.{CHROAddress, NewAddress, PPOBChoice}
import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.{Lang, Langs, Messages}
import play.api.mvc.MessagesControllerComponents
import play.api.test.Helpers._
import repositories.NavModelRepo
import services.AddressLookupFrontendService
import utils.SCRSFeatureSwitches
import views.html.reg.{PrinciplePlaceOfBusiness => PrinciplePlaceOfBusinessView}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PPOBSpec()(implicit lang: Lang) extends SCRSSpec with PPOBFixture with NavModelRepoMock with GuiceOneAppPerSuite with AuthBuilder {
  lazy val mockNavModelRepoObj = mock[NavModelRepo]
  lazy val mockBusinessRegConnector = mock[BusinessRegistrationConnector]
  lazy val mockAddressLookupFrontendService = mock[AddressLookupFrontendService]
  override lazy val mockSCRSFeatureSwitches = mock[SCRSFeatureSwitches]
  lazy val mockMessages = app.injector.instanceOf[Messages]
  implicit val langs = app.injector.instanceOf[Langs]

  class SetupPage {
    val controller = new PPOBController(
      mockAuthConnector,
      mockS4LConnector,
      mockKeystoreConnector,
      mockCompanyRegistrationConnector,
      mockHandOffService,
      mockBusinessRegConnector,
      mockNavModelRepoObj,
      mockJweCommon,
      mockAddressLookupFrontendService,
      mockPPOBService,
      mockSCRSFeatureSwitches,
      app.injector.instanceOf[MessagesControllerComponents],
      app.injector.instanceOf[ControllerErrorHandler],
      app.injector.instanceOf[HandOffUtils],
      app.injector.instanceOf[PrinciplePlaceOfBusinessView]

    )
    (mockAppConfig,
      global
    )
  }

  "PPOBController.show" should {
    "make sure that the PPOB page has the correct elements when only an RO address is provided to the view" in new SetupPage {
      CTRegistrationConnectorMocks.retrieveCTRegistration()
      mockKeystoreFetchAndGet("registrationID", Some("12345"))

      when(mockPPOBService.fetchAddressesAndChoice(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(
          (
            Some(CHROAddress("38", "line 1", Some("line 2"), "Telford", "UK", None, Some("ZZ1 1ZZ"), None)),
            None,
            PPOBChoice("")
          )
        ))


      showWithAuthorisedUser(controller.show) {
        result =>
          val document = Jsoup.parse(contentAsString(result))

          document.title must include("What is the company's 'principal place of business'?")
          document.getElementsByTag("h1").first().text mustBe "What is the company's 'principal place of business'?"
          document.getElementById("next").attr("value") mustBe "Save and continue"

          document.getElementById("addressChoice").getElementsByAttributeValue("for","addressChoice-ro").text() mustBe "38 line 1, line 2, Telford ZZ1 1ZZ, UK"
          document.getElementById("addressChoice").getElementsByAttributeValue("for","addressChoice-ppob") mustBe empty
          document.getElementById("addressChoice").getElementsByAttributeValue("for","addressChoice-other").text() mustBe "A different address"
      }
    }
    "make sure that the PPOB page has the correct elements when only an RO and different PPOB address is provided to the view" in new SetupPage {
      CTRegistrationConnectorMocks.retrieveCTRegistration()
      mockKeystoreFetchAndGet("registrationID", Some("12345"))

      when(mockPPOBService.fetchAddressesAndChoice(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(
          (
            Some(CHROAddress("38", "line 1", Some("line 2"), "Telford", "UK", None, Some("ZZ1 1ZZ"), None)),
            Some(NewAddress("line 1", "line 2", None, None, Some("ZZ2 2ZZ"), None, None)),
            PPOBChoice("PPOB")
          )
        ))

      showWithAuthorisedUser(controller.show) {
        result =>
          val document = Jsoup.parse(contentAsString(result))

          document.title must include("What is the company's 'principal place of business'?")
          document.getElementsByTag("h1").first().text mustBe "What is the company's 'principal place of business'?"
          document.getElementById("next").attr("value") mustBe "Save and continue"

          document.getElementById("addressChoice").getElementsByAttributeValue("for","addressChoice-ro").text() mustBe "38 line 1, line 2, Telford ZZ1 1ZZ, UK"
          document.getElementById("addressChoice").getElementsByAttributeValue("for","addressChoice-ppob").text() mustBe "line 1, line 2, ZZ2 2ZZ"
          document.getElementById("addressChoice").getElementsByAttributeValue("for","addressChoice-other").text() mustBe "A different address"
      }
    }

    "make sure that the PPOB page has the correct elements when the RO address was selected before" in new SetupPage {
      CTRegistrationConnectorMocks.retrieveCTRegistration()
      mockKeystoreFetchAndGet("registrationID", Some("12345"))

      when(mockPPOBService.fetchAddressesAndChoice(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(
          (
            Some(CHROAddress("14", "St Test Walk", Some("Testley"), "Testford", "UK", None, Some("TE1 1ST"), Some("Testshire"))),
            Some(NewAddress("14 St Test Walk", "Testley", Some("Testford"), Some("Testshire"), Some("TE1 1ST"), Some("UK"), Some("txid"))),
            PPOBChoice("RO")
          )
        ))

      showWithAuthorisedUser(controller.show) {
        result =>
          val document = Jsoup.parse(contentAsString(result))

          document.title must include("What is the company's 'principal place of business'?")
          document.getElementsByTag("h1").first().text mustBe "What is the company's 'principal place of business'?"
          document.getElementById("next").attr("value") mustBe "Save and continue"

          document.getElementById("addressChoice").getElementsByAttributeValue("for","addressChoice-ro").text() mustBe "14 St Test Walk, Testley, Testford, Testshire, TE1 1ST, UK"
          document.getElementById("addressChoice").getElementsByAttributeValue("for","addressChoice-ppob") mustBe empty
          document.getElementById("addressChoice").getElementsByAttributeValue("for","addressChoice-other").text() mustBe "A different address"
      }
    }

    "make sure that PPOB page has the correct elements when an RO address is not provided to the view" in new SetupPage {
      CTRegistrationConnectorMocks.retrieveCTRegistration()
      mockKeystoreFetchAndGet("registrationID", Some("12345"))

      when(mockPPOBService.fetchAddressesAndChoice(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(
          (
            None,
            Some(NewAddress("line 1", "line 2", None, None, None, None, None)),
            PPOBChoice("")
          )
        ))

      showWithAuthorisedUser(controller.show) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title must include("What is the company's 'principal place of business'?")
          document.getElementsByTag("h1").first.text mustBe "What is the company's 'principal place of business'?"
          document.getElementById("next").attr("value") mustBe "Save and continue"
      }
    }
  }
}
