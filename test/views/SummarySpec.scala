/*
 * Copyright 2018 HM Revenue & Customs
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
import play.api.test.Helpers._
import services.MetaDataService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.WithFakeApplication

import scala.concurrent.Future

class SummarySpec extends SCRSSpec with SCRSFixtures with AccountingDetailsFixture
  with CorporationTaxFixture with NavModelRepoMock with WithFakeApplication with AuthBuilder {

  implicit val hcWithExtraHeaders: HeaderCarrier = HeaderCarrier().withExtraHeaders("Accept" -> "application/vnd.hmrc.1.0+json")

  val applicantData = AboutYouChoice("Director")
  val mockNavModelRepoObj = mockNavModelRepo

  class SetupPage {
    val controller = new SummaryController {
      val authConnector = mockAuthConnector
      val s4LConnector = mockS4LConnector
      val companyRegistrationConnector = mockCompanyRegistrationConnector
      val keystoreConnector = mockKeystoreConnector
      val metaDataService = mockMetaDataService
      val handOffService = mockHandOffService
      val navModelMongo = mockNavModelRepoObj
      override val appConfig = mockAppConfig
    }
  }

  "show" should {

    val corporationTaxModel = buildCorporationTaxModel()

    "make sure that the Summary page has the correct elements" in new SetupPage {

      mockKeystoreFetchAndGet("registrationID", Some("12345"))

      when(mockMetaDataService.getApplicantData(Matchers.any())(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(applicantData))

      CTRegistrationConnectorMocks.retrieveCompanyDetails(Some(validCompanyDetailsResponse))
      CTRegistrationConnectorMocks.retrieveTradingDetails(Some(TradingDetails("false")))
      CTRegistrationConnectorMocks.retrieveContactDetails(CompanyContactDetailsSuccessResponse(validCompanyContactDetailsResponse))
      CTRegistrationConnectorMocks.retrieveAccountingDetails(validAccountingResponse)

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
            "companyContactDaytimeTelephoneNumber" -> "0123456789",
            "companycontactMobileNumber" -> "0123456789",
            "companyContactEmail" -> "foo@bar.wibble",
            "startDate" -> "10/06/2020",
            "tradingDetails" -> "No"
          ) foreach { case (element, message) =>
            document.getElementById(element).text() shouldBe message
          }
      }
    }

    "make sure that the Summary page has the correct elements when the PPOB addess is not same as RO address" in new SetupPage {

      mockKeystoreFetchAndGet("registrationID", Some("12345"))

      when(mockMetaDataService.getApplicantData(Matchers.any())(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(applicantData))

      CTRegistrationConnectorMocks.retrieveTradingDetails(Some(TradingDetails("true")))
      CTRegistrationConnectorMocks.retrieveContactDetails(CompanyContactDetailsSuccessResponse(validCompanyContactDetailsResponse))
      CTRegistrationConnectorMocks.retrieveCompanyDetails(Some(validCompanyDetailsResponseDifferentAddresses))
      CTRegistrationConnectorMocks.retrieveAccountingDetails(validAccountingResponse)
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
            "companyContactDaytimeTelephoneNumber" -> "0123456789",
            "companycontactMobileNumber" -> "0123456789",
            "companyContactEmail" -> "foo@bar.wibble",
            "startDate" -> "10/06/2020",
            "tradingDetails" -> "Yes"
          ) foreach { case (element, message) =>
            document.getElementById(element).text() shouldBe message
          }
      }
    }
  }
}
