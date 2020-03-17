/*
 * Copyright 2020 HM Revenue & Customs
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
import controllers.reg.SummaryController
import fixtures.{AccountingDetailsFixture, CorporationTaxFixture, SCRSFixtures}
import mocks.NavModelRepoMock
import models._
import org.jsoup.Jsoup
import org.mockito.Matchers
import org.mockito.Mockito._
import play.api.i18n.MessagesApi
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.WithFakeApplication
import mocks.TakeoverServiceMock
import utils.JweCommon

import scala.concurrent.Future

class SummarySpec extends SCRSSpec with SCRSFixtures with AccountingDetailsFixture
  with CorporationTaxFixture with NavModelRepoMock with WithFakeApplication with AuthBuilder with TakeoverServiceMock {

  implicit val hcWithExtraHeaders: HeaderCarrier = HeaderCarrier().withExtraHeaders("Accept" -> "application/vnd.hmrc.1.0+json")

  val applicantData = AboutYouChoice("Director")
  val mockNavModelRepoObj = mockNavModelRepo
  val testTakeoverDetails = TakeoverDetails(replacingAnotherBusiness = true)
  val testNoTakeoverDetails = TakeoverDetails(replacingAnotherBusiness = false)
  val testBusinessName = TakeoverDetails(replacingAnotherBusiness = true, businessName = Some("ABC Limited"))
  val testTakeOverAddress = TakeoverDetails(replacingAnotherBusiness = true, businessName = Some("ABC Limited"),
                                            Some(NewAddress("line 1","line 2",Some("line 3"),Some("line 4"),Some("ZZ1 1ZZ"),Some("UK"))))
  val testTakeOverAddressNoName = TakeoverDetails(replacingAnotherBusiness = true, businessName = None,
                                            Some(NewAddress("line 1","line 2",Some("line 3"),Some("line 4"),Some("ZZ1 1ZZ"),Some("UK"))))
  //TODO Will eventually contain the full model.
  val testTakeOverFullModel = TakeoverDetails(replacingAnotherBusiness = true, businessName = Some("ABC Limited"),
    Some(NewAddress("line 1","line 2",Some("line 3"),Some("line 4"),Some("ZZ1 1ZZ"),Some("UK"))),
    Some("Agreed Person"),
    Some(NewAddress("line 1","line 2",Some("line 3"),Some("line 4"),Some("ZZ1 1ZZ"),Some("UK")))
  )

  val testRegiId = "12345"

  class SetupPage {
    val controller = new SummaryController {
      val authConnector = mockAuthConnector
      val s4LConnector = mockS4LConnector
      val compRegConnector = mockCompanyRegistrationConnector
      val keystoreConnector = mockKeystoreConnector
      val metaDataService = mockMetaDataService
      val takeoverService = mockTakeoverService
      val handOffService = mockHandOffService
      val navModelMongo = mockNavModelRepoObj
      override val appConfig = mockAppConfig
      override val jwe: JweCommon = mockJweCommon
      override val messagesApi = fakeApplication.injector.instanceOf[MessagesApi]
    }
  }

  "show" should {

    val corporationTaxModel = buildCorporationTaxModel()

    "make sure that the Summary page has the correct elements" in new SetupPage {

      mockKeystoreFetchAndGet("registrationID", Some(testRegiId))

      when(mockMetaDataService.getApplicantData(Matchers.any())(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(applicantData))

      CTRegistrationConnectorMocks.retrieveCompanyDetails(Some(validCompanyDetailsResponse))
      CTRegistrationConnectorMocks.retrieveTradingDetails(Some(TradingDetails("false")))
      CTRegistrationConnectorMocks.retrieveContactDetails(CompanyContactDetailsSuccessResponse(validCompanyContactDetailsResponse))
      CTRegistrationConnectorMocks.retrieveAccountingDetails(validAccountingResponse)

      mockGetTakeoverDetails(testRegiId)(Future.successful(None))

      when(mockCompanyRegistrationConnector.retrieveCorporationTaxRegistration(Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(corporationTaxModel))

      showWithAuthorisedUser(controller.show) {
        result =>
          val document = Jsoup.parse(contentAsString(result))

          document.title() shouldBe "Check and confirm your answers"

          Map (
            "applicantTitle" -> "Applicant",
            "applicant" -> "Director",
            "companyNameTitle" -> "Company details",
            "companyAccountingTitle" -> "Company accounting",
            "companyName" -> "testCompanyName",
            "ROAddress" -> "Premises Line1 Line2 Locality Region FX1 1ZZ Country",
            "PPOBAddress" -> "Registered Office Address",
            "companyContact" -> "0123456789 foo@bar.wibble 0123456789",
            "startDate" -> "10/06/2020",
            "tradingDetails" -> "No"
          ) foreach { case (element, message) =>
            document.getElementById(element).text() shouldBe message
          }
      }
    }

    "make sure that the Summary page has the correct elements when the PPOB address is not same as RO address" in new SetupPage {

      mockKeystoreFetchAndGet("registrationID", Some(testRegiId))

      when(mockMetaDataService.getApplicantData(Matchers.any())(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(applicantData))

      CTRegistrationConnectorMocks.retrieveTradingDetails(Some(TradingDetails("true")))
      CTRegistrationConnectorMocks.retrieveContactDetails(CompanyContactDetailsSuccessResponse(validCompanyContactDetailsResponse))
      CTRegistrationConnectorMocks.retrieveCompanyDetails(Some(validCompanyDetailsResponseDifferentAddresses))
      CTRegistrationConnectorMocks.retrieveAccountingDetails(validAccountingResponse)

      mockGetTakeoverDetails(testRegiId)(Future.successful(None))

      when(mockCompanyRegistrationConnector.retrieveCorporationTaxRegistration(Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(corporationTaxModel))

      showWithAuthorisedUser(controller.show) {
        result =>
          val document = Jsoup.parse(contentAsString(result))

          Map (
            "applicantTitle" -> "Applicant",
            "applicant" -> "Director",
            "companyNameTitle" -> "Company details",
            "companyAccountingTitle" -> "Company accounting",
            "companyName" -> "testCompanyName",
            "ROAddress" -> "Premises Line1 Line2 Locality Region Post Code Country",
            "PPOBAddress" -> "line1 line2 line3 line14 FX1 1ZZ",
            "companyContact" -> "0123456789 foo@bar.wibble 0123456789",
            "startDate" -> "10/06/2020",
            "tradingDetails" -> "Yes"
          ) foreach { case (element, message) =>
            document.getElementById(element).text() shouldBe message
          }
      }
    }

    "make sure that the Summary page has the correct elements when the user is not taking over a business" in new SetupPage {
      mockKeystoreFetchAndGet("registrationID", Some(testRegiId))

      when(mockMetaDataService.getApplicantData(Matchers.any())(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(applicantData))

      CTRegistrationConnectorMocks.retrieveCompanyDetails(Some(validCompanyDetailsResponse))
      CTRegistrationConnectorMocks.retrieveTradingDetails(Some(TradingDetails("false")))
      CTRegistrationConnectorMocks.retrieveContactDetails(CompanyContactDetailsSuccessResponse(validCompanyContactDetailsResponse))
      CTRegistrationConnectorMocks.retrieveAccountingDetails(validAccountingResponse)

      mockGetTakeoverDetails(testRegiId)(Future.successful(Some(testNoTakeoverDetails)))

      when(mockCompanyRegistrationConnector.retrieveCorporationTaxRegistration(Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(corporationTaxModel))

      showWithAuthorisedUser(controller.show) {
        result =>
          val document = Jsoup.parse(contentAsString(result))

          document.title() shouldBe "Check and confirm your answers"

          Map (
            "applicantTitle" -> "Applicant",
            "applicant" -> "Director",
            "companyNameTitle" -> "Company details",
            "companyAccountingTitle" -> "Company accounting",
            "companyName" -> "testCompanyName",
            "ROAddress" -> "Premises Line1 Line2 Locality Region FX1 1ZZ Country",
            "PPOBAddress" -> "Registered Office Address",
            "companyContact" -> "0123456789 foo@bar.wibble 0123456789",
            "startDate" -> "10/06/2020",
            "tradingDetails" -> "No",
            "takeoversTitle" -> "Company takeover",
            "replacingAnotherBusiness" -> "No",
            "replacingAnotherBusinessLabel" -> "Is the new company replacing another business?"
          ) foreach { case (element, message) =>
            document.getElementById(element).text() shouldBe message
          }
      }
    }

    "make sure that the Summary page has the correct elements for the business takeover address when there is no business name" in new SetupPage {
      mockKeystoreFetchAndGet("registrationID", Some(testRegiId))

      when(mockMetaDataService.getApplicantData(Matchers.any())(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(applicantData))

      CTRegistrationConnectorMocks.retrieveCompanyDetails(Some(validCompanyDetailsResponse))
      CTRegistrationConnectorMocks.retrieveTradingDetails(Some(TradingDetails("false")))
      CTRegistrationConnectorMocks.retrieveContactDetails(CompanyContactDetailsSuccessResponse(validCompanyContactDetailsResponse))
      CTRegistrationConnectorMocks.retrieveAccountingDetails(validAccountingResponse)

      mockGetTakeoverDetails(testRegiId)(Future.successful(Some(testTakeOverAddressNoName)))

      when(mockCompanyRegistrationConnector.retrieveCorporationTaxRegistration(Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(corporationTaxModel))

      showWithAuthorisedUser(controller.show) {
        result =>
          val document = Jsoup.parse(contentAsString(result))

          document.title() shouldBe "Check and confirm your answers"

          Map (
            "applicantTitle" -> "Applicant",
            "applicant" -> "Director",
            "companyNameTitle" -> "Company details",
            "companyAccountingTitle" -> "Company accounting",
            "companyName" -> "testCompanyName",
            "ROAddress" -> "Premises Line1 Line2 Locality Region FX1 1ZZ Country",
            "PPOBAddress" -> "Registered Office Address",
            "companyContact" -> "0123456789 foo@bar.wibble 0123456789",
            "startDate" -> "10/06/2020",
            "tradingDetails" -> "No",
            "takeoversTitle" -> "Company takeover",
            "replacingAnotherBusiness" -> "Yes",
            "replacingAnotherBusinessLabel" -> "Is the new company replacing another business?",
            "businessTakeOverAddressLabel" -> "What is the other business's address?",
            "businessTakeOverAddress" -> "line 1 line 2 line 3 line 4 ZZ1 1ZZ UK",
            "change-business-takeover-address" -> "changethe other business's address"
          ) foreach { case (element, message) =>
            document.getElementById(element).text() shouldBe message
          }
      }
    }

    "make sure that the Summary page has the correct elements for a complete takeover model" in new SetupPage {
      mockKeystoreFetchAndGet("registrationID", Some(testRegiId))

      when(mockMetaDataService.getApplicantData(Matchers.any())(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(applicantData))

      CTRegistrationConnectorMocks.retrieveCompanyDetails(Some(validCompanyDetailsResponse))
      CTRegistrationConnectorMocks.retrieveTradingDetails(Some(TradingDetails("false")))
      CTRegistrationConnectorMocks.retrieveContactDetails(CompanyContactDetailsSuccessResponse(validCompanyContactDetailsResponse))
      CTRegistrationConnectorMocks.retrieveAccountingDetails(validAccountingResponse)

      mockGetTakeoverDetails(testRegiId)(Future.successful(Some(testTakeOverFullModel)))

      when(mockCompanyRegistrationConnector.retrieveCorporationTaxRegistration(Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(corporationTaxModel))

      showWithAuthorisedUser(controller.show) {
        result =>
          val document = Jsoup.parse(contentAsString(result))

          document.title() shouldBe "Check and confirm your answers"

          Map (
            "applicantTitle" -> "Applicant",
            "applicant" -> "Director",
            "companyNameTitle" -> "Company details",
            "companyAccountingTitle" -> "Company accounting",
            "companyName" -> "testCompanyName",
            "ROAddress" -> "Premises Line1 Line2 Locality Region FX1 1ZZ Country",
            "PPOBAddress" -> "Registered Office Address",
            "companyContact" -> "0123456789 foo@bar.wibble 0123456789",
            "startDate" -> "10/06/2020",
            "tradingDetails" -> "No",
            "takeoversTitle" -> "Company takeover",
            "replacingAnotherBusiness" -> "Yes",
            "replacingAnotherBusinessLabel" -> "Is the new company replacing another business?",
            "change-replacing-another-business" -> "changewhether the new company is replacing another business",
            "otherBusinessName" -> "ABC Limited",
            "otherBusinessNameLabel" -> "What is the name of the other business?",
            "change-other-business-name" -> "changethe name of the other business",
            "businessTakeOverAddressLabel" -> "What is ABC Limited's address?",
            "businessTakeOverAddress" -> "line 1 line 2 line 3 line 4 ZZ1 1ZZ UK",
            "change-business-takeover-address" -> "changeABC Limited's address",
            "personWhoAgreedTakeoverLabel" -> "Who agreed the takeover?",
            "personWhoAgreedTakeover" -> "Agreed Person",
            "change-who-agreed-takeover" -> "changethe name of who agreed the takeover",
            "previousOwnersAddressLabel" -> "What is Agreed Person's home address?",
            "previousOwnersAddress" -> "line 1 line 2 line 3 line 4 ZZ1 1ZZ UK",
            "change-previous-owners-address" -> "changethe address given"
          ) foreach { case (element, message) =>
            document.getElementById(element).text() shouldBe message
          }

          Map (
            "change-replacing-another-business" -> "/register-your-company/replacing-another-business",
            "change-other-business-name" -> "/register-your-company/other-business-name",
            "change-business-takeover-address" -> "/register-your-company/other-business-address",
            "change-who-agreed-takeover" -> "/register-your-company/who-agreed-takeover",
            "change-previous-owners-address" -> "/register-your-company/home-address"
          ) foreach { case (element, message) =>
            document.getElementById(element).attr("href") shouldBe message
          }
      }
    }


  }
}
