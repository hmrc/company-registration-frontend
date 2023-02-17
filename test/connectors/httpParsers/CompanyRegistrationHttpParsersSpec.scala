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

package connectors.httpParsers

import ch.qos.logback.classic.Level
import config.LangConstants
import connectors.httpParsers.exceptions.DownstreamExceptions
import helpers.SCRSSpec
import models.AccountingDatesModel.FUTURE_DATE
import models.{AccountingDetails, AccountingDetailsNotFoundResponse, AccountingDetailsSuccessResponse, Address, CHROAddress, CompanyContactDetails, CompanyContactDetailsNotFoundResponse, CompanyContactDetailsSuccessResponse, CompanyDetails, CorporationTaxRegistrationResponse, Email, Language, Links, NewAddress, PPOB, TradingDetails}
import play.api.http.Status.{BAD_REQUEST, INTERNAL_SERVER_ERROR, NOT_FOUND, NO_CONTENT, OK}
import play.api.libs.json.{JsResultException, Json}
import uk.gov.hmrc.http.HttpResponse
import utils.LogCapturingHelper

class CompanyRegistrationHttpParsersSpec extends SCRSSpec with LogCapturingHelper {

  val regId = "reg12345"
  val language = Language(LangConstants.english)

  "CompanyRegistrationHttpParsers" when {

    "calling .updateLanguageHttpReads(regId: String, language: Language)" when {

      val rds = CompanyRegistrationHttpParsers.updateLanguageHttpReads(regId, language)

      s"HttpResponse is NO_CONTENT ($NO_CONTENT)" must {

        "return true (and log debug message)" in {

          withCaptureOfLoggingFrom(CompanyRegistrationHttpParsers.logger) { logs =>
            rds.read("PUT", "/language", HttpResponse(NO_CONTENT, "")) mustBe true
            logs.containsMsg(Level.DEBUG, s"[CompanyRegistrationHttpParsers][updateLanguageHttpReads] Updated language to: '${language.code}' for regId: '$regId'")
          }
        }
      }

      s"HttpResponse is NOT_FOUND ($NOT_FOUND)" must {

        "return false (and log warn message)" in {

          withCaptureOfLoggingFrom(CompanyRegistrationHttpParsers.logger) { logs =>
            rds.read("PUT", "/language", HttpResponse(NOT_FOUND, "")) mustBe false
            logs.containsMsg(Level.WARN, s"[CompanyRegistrationHttpParsers][updateLanguageHttpReads] No document was found ($NOT_FOUND) when attempting to update language to: '${language.code}' for regId: '$regId'")
          }
        }
      }

      s"HttpResponse is any other unexpected status e.g. INTERNAL_SERVER_ERROR ($INTERNAL_SERVER_ERROR)" must {

        "return false (and log error message)" in {

          withCaptureOfLoggingFrom(CompanyRegistrationHttpParsers.logger) { logs =>
            rds.read("PUT", "/language", HttpResponse(INTERNAL_SERVER_ERROR, "")) mustBe false
            logs.containsMsg(Level.ERROR, s"[CompanyRegistrationHttpParsers][updateLanguageHttpReads] An unexpected status of '$INTERNAL_SERVER_ERROR' was returned when attempting to update language to: '${language.code}' for regId: '$regId'")
          }
        }
      }
    }

    "calling .retrieveEmailHttpReads(regId: String)" when {
      val emailRetrieved = Some(Email("verified@email", "GG", linkSent = true, verified = true, returnLinkEmailSent = true))

      val emailResponseJson = Json.obj(
        "address" -> "verified@email",
        "type" -> "GG",
        "link-sent" -> true,
        "verified" -> true,
        "return-link-email-sent" -> true,
      )

      "response is OK and JSON is valid" must {
        "return the email" in {
          CompanyRegistrationHttpParsers.retrieveEmailHttpReads(regId).read("", "",
            HttpResponse(OK, json = emailResponseJson, Map())
          ) mustBe emailRetrieved
        }
      }

      "response is OK, JSON is valid BUT Json is missing" must {

        "throw a DownstreamExceptions.ConfirmationRefsNotFoundException and log an error message" in {

          withCaptureOfLoggingFrom(CompanyRegistrationHttpParsers.logger) { logs =>
            intercept[JsResultException](
              CompanyRegistrationHttpParsers.retrieveEmailHttpReads(regId).read("", "", HttpResponse(OK, json = Json.obj(), Map()))
            )
          }
        }
      }

      "response is NOT_FOUND" must {

        "return a None and log an info message" in {

          withCaptureOfLoggingFrom(CompanyRegistrationHttpParsers.logger) { logs =>
            CompanyRegistrationHttpParsers.retrieveEmailHttpReads(regId).read("", "", HttpResponse(NOT_FOUND, "")) mustBe None
            logs.containsMsg(Level.INFO, s"[retrieveEmailHttpReads] A call was made to company reg and a NotFound response was returned for regId: $regId")
          }
        }
      }

      "response is any other status, e.g ISE" must {

        "return an Upstream Error response and log an error" in {

          withCaptureOfLoggingFrom(CompanyRegistrationHttpParsers.logger) { logs =>
            intercept[DownstreamExceptions.CompanyRegistrationException](CompanyRegistrationHttpParsers.retrieveEmailHttpReads(regId).read("", "", HttpResponse(INTERNAL_SERVER_ERROR, "")))
            logs.containsMsg(Level.ERROR, s"[retrieveEmailHttpReads] Calling url: '' returned unexpected status: '$INTERNAL_SERVER_ERROR' for regId: '$regId'")
          }
        }
      }
    }

    "calling .updateAccountingDetailsHttpReads(regId: String)" when {
      val accountingDetails = AccountingDetails("1", Some("1-2-3"), Links(None, None))

      val accountingDetailsResponseJson = Json.obj(
        "accountingDateStatus" -> "1",
        "startDateOfBusiness" -> "1-2-3",
        "links" -> Json.obj(),
      )

      "response is OK and JSON is valid" must {
        "return the email" in {
          CompanyRegistrationHttpParsers.updateAccountingDetailsHttpReads(regId).read("", "",
            HttpResponse(OK, json = accountingDetailsResponseJson, Map())
          ) mustBe accountingDetails
        }
      }

      "response is OK, JSON is valid BUT Json is missing" must {

        "throw a DownstreamExceptions.ConfirmationRefsNotFoundException and log an error message" in {

          withCaptureOfLoggingFrom(CompanyRegistrationHttpParsers.logger) { logs =>
            intercept[JsResultException](
              CompanyRegistrationHttpParsers.updateAccountingDetailsHttpReads(regId).read("", "", HttpResponse(OK, json = Json.obj(), Map()))
            )
          }
        }
      }

      "response is any other status, e.g ISE" must {

        "return an Upstream Error response and log an error" in {

          withCaptureOfLoggingFrom(CompanyRegistrationHttpParsers.logger) { logs =>
            intercept[DownstreamExceptions.CompanyRegistrationException](CompanyRegistrationHttpParsers.updateAccountingDetailsHttpReads(regId).read("", "", HttpResponse(INTERNAL_SERVER_ERROR, "")))
            logs.containsMsg(Level.ERROR, s"[updateAccountingDetailsHttpReads] Calling url: '' returned unexpected status: '$INTERNAL_SERVER_ERROR' for regId: '$regId'")
          }
        }
      }
    }

    "calling .updateContactDetailsHttpReads(regId: String)" when {
      val contactDetails = CompanyContactDetails(Some("afoo@bar.wibble"), Some(""), Some(""), Links(None, None))

      val contactDetailsResponseJson = Json.obj(
        "contactEmail" -> "afoo@bar.wibble",
        "contactDaytimeTelephoneNumber" -> "",
        "contactMobileNumber" -> "",
        "links" -> Json.obj(),
      )

      "response is OK and JSON is valid" must {
        "return the email" in {
          CompanyRegistrationHttpParsers.updateContactDetailsHttpReads(regId).read("", "",
            HttpResponse(OK, json = contactDetailsResponseJson, Map())
          ) mustBe contactDetails
        }
      }

      "response is OK, JSON is valid BUT Json is missing" must {

        "throw a DownstreamExceptions.ConfirmationRefsNotFoundException and log an error message" in {

          withCaptureOfLoggingFrom(CompanyRegistrationHttpParsers.logger) { logs =>
            intercept[JsResultException](
              CompanyRegistrationHttpParsers.updateContactDetailsHttpReads(regId).read("", "", HttpResponse(OK, json = Json.obj(), Map()))
            )
          }
        }
      }

      "response is any other status, e.g ISE" must {

        "return an Upstream Error response and log an error" in {

          withCaptureOfLoggingFrom(CompanyRegistrationHttpParsers.logger) { logs =>
            intercept[DownstreamExceptions.CompanyRegistrationException](CompanyRegistrationHttpParsers.updateAccountingDetailsHttpReads(regId).read("", "", HttpResponse(INTERNAL_SERVER_ERROR, "")))
            logs.containsMsg(Level.ERROR, s"[updateAccountingDetailsHttpReads] Calling url: '' returned unexpected status: '$INTERNAL_SERVER_ERROR' for regId: '$regId'")
          }
        }
      }
    }

    "calling .updateTradingDetailsHttpReads(regId: String)" when {
      val tradingDetails = TradingDetails("test")

      val tradingDetailsResponseJson = Json.obj(
        "regularPayments" -> "test"
      )

      "response is OK and JSON is valid" must {
        "return the email" in {
          CompanyRegistrationHttpParsers.updateTradingDetailsHttpReads(regId).read("", "",
            HttpResponse(OK, json = tradingDetailsResponseJson, Map())
          ) mustBe tradingDetails
        }
      }

      "response is OK, JSON is valid BUT Json is missing" must {

        "throw a DownstreamExceptions.CompanyRegistrationException and log an error message" in {

          withCaptureOfLoggingFrom(CompanyRegistrationHttpParsers.logger) { logs =>
            intercept[JsResultException](
              CompanyRegistrationHttpParsers.updateTradingDetailsHttpReads(regId).read("", "", HttpResponse(OK, json = Json.obj(), Map()))
            )
          }
        }
      }

      "response is any other status, e.g ISE" must {

        "return an Upstream Error response and log an error" in {

          withCaptureOfLoggingFrom(CompanyRegistrationHttpParsers.logger) { logs =>
            intercept[DownstreamExceptions.CompanyRegistrationException](CompanyRegistrationHttpParsers.updateTradingDetailsHttpReads(regId).read("", "", HttpResponse(INTERNAL_SERVER_ERROR, "")))
            logs.containsMsg(Level.ERROR, s"[updateTradingDetailsHttpReads] Calling url: '' returned unexpected status: '$INTERNAL_SERVER_ERROR' for regId: '$regId'")
          }
        }
      }
    }

    "calling .updateCompanyDetailsHttpReads(regId: String)" when {
      val companyDetails = CompanyDetails(
        "Company Name Ltd",
        CHROAddress("premises", "BusinessAddressLine1", Some("BusinessAddressLine2"), "locality", "testCountry", None, Some("TE1 1ST"), None),
        PPOB("RO", Some(Address(
          None,
          "14 St Test Walk",
          "Testley",
          Some("Testford"),
          Some("Testshire"),
          Some("TE1 1ST"),
          Some("UK"),
          txid = "93cf1cfc-75fd-4ac0-96ac-5f0018c70a8f"
        ))),
        "ENGLAND_AND_WALES"
      )

      val companyDetailsResponseJson = Json.obj(
        "companyName" -> "Company Name Ltd",
        "cHROAddress" -> Json.obj(
          "premises" -> "premises",
          "address_line_1" -> "BusinessAddressLine1",
          "address_line_2" -> "BusinessAddressLine2",
          "locality" -> "locality",
          "country" -> "testCountry",
          "postal_code" -> "TE1 1ST"
        ),
        "pPOBAddress" -> Json.obj(
          "addressType" -> "RO",
          "address" -> Json.obj(
          "addressLine1" -> "14 St Test Walk",
          "addressLine2" -> "Testley",
          "addressLine3" -> "Testford",
          "addressLine4" -> "Testshire",
          "postCode" -> "TE1 1ST",
          "country" -> "UK",
          "txid" -> "93cf1cfc-75fd-4ac0-96ac-5f0018c70a8f"
        )
      ),
        "jurisdiction" -> "ENGLAND_AND_WALES"
      )

      "response is OK and JSON is valid" must {
        "return the email" in {
          CompanyRegistrationHttpParsers.updateCompanyDetailsHttpReads(regId).read("", "",
            HttpResponse(OK, json = companyDetailsResponseJson, Map())
          ) mustBe companyDetails
        }
      }

      "response is OK, JSON is valid BUT Json is missing" must {

        "throw a DownstreamExceptions.CompanyRegistrationException and log an error message" in {

          withCaptureOfLoggingFrom(CompanyRegistrationHttpParsers.logger) { logs =>
            intercept[JsResultException](
              CompanyRegistrationHttpParsers.updateCompanyDetailsHttpReads(regId).read("", "", HttpResponse(OK, json = Json.obj(), Map()))
            )
          }
        }
      }

      "response is any other status, e.g ISE" must {

        "return an Upstream Error response and log an error" in {

          withCaptureOfLoggingFrom(CompanyRegistrationHttpParsers.logger) { logs =>
            intercept[DownstreamExceptions.CompanyRegistrationException](CompanyRegistrationHttpParsers.updateCompanyDetailsHttpReads(regId).read("", "", HttpResponse(INTERNAL_SERVER_ERROR, "")))
            logs.containsMsg(Level.ERROR, s"[updateCompanyDetailsHttpReads] Calling url: '' returned unexpected status: '$INTERNAL_SERVER_ERROR' for regId: '$regId'")
          }
        }
      }
    }

    "calling .validateRegisteredOfficeAddressHttpReads(regId: String)" when {
      val newAddressResponseJson = Json.obj()

      "response is OK and JSON is valid" must {
        "return the registered office address" in {
          CompanyRegistrationHttpParsers.validateRegisteredOfficeAddressHttpReads(regId).read("PUT", "/company-registration/corporation-tax-registration/check-return-business-address",
            HttpResponse(OK, json = newAddressResponseJson, Map())
          ) mustBe None
        }
      }

      "response is BAD_REQUEST" must {
        "return a None and log an info message" in {
          withCaptureOfLoggingFrom(CompanyRegistrationHttpParsers.logger) { logs =>
            CompanyRegistrationHttpParsers.validateRegisteredOfficeAddressHttpReads(regId).read("PUT", "/company-registration/corporation-tax-registration/check-return-business-address", HttpResponse(BAD_REQUEST, "")) mustBe None
          }
        }
      }

      "response is any other status, e.g ISE" must {

        "return an Upstream Error response and log an error" in {
          withCaptureOfLoggingFrom(CompanyRegistrationHttpParsers.logger) { logs =>
            intercept[DownstreamExceptions.CompanyRegistrationException](CompanyRegistrationHttpParsers.validateRegisteredOfficeAddressHttpReads(regId).read("", "", HttpResponse(INTERNAL_SERVER_ERROR, "")))
            logs.containsMsg(Level.ERROR, s"[validateRegisteredOfficeAddressHttpReads] Calling url: '' returned unexpected status: '$INTERNAL_SERVER_ERROR' for regId: '$regId'")
          }
        }
      }
    }

    "calling .createCorporationTaxRegistrationDetailsHttpReads(regId: String)" when {
      val createCorporationTax = CorporationTaxRegistrationResponse(
        regId,
        "testTimestamp"
      )

      val createCorporationTaxResponseJson = Json.obj(
        "registrationID" -> regId,
        "formCreationTimestamp" -> "testTimestamp"
      )

      "response is OK and JSON is valid" must {
        "return the CorporationTaxRegistration" in {
          CompanyRegistrationHttpParsers.createCorporationTaxRegistrationDetailsHttpReads(regId).read("", "",
            HttpResponse(OK, json = createCorporationTaxResponseJson, Map())
          ) mustBe createCorporationTax
        }
      }

      "response is OK, JSON is valid BUT Json is missing" must {

        "throw a DownstreamExceptions.CompanyRegistrationException and log an error message" in {

          withCaptureOfLoggingFrom(CompanyRegistrationHttpParsers.logger) { logs =>
            intercept[JsResultException](
              CompanyRegistrationHttpParsers.createCorporationTaxRegistrationDetailsHttpReads(regId).read("", "", HttpResponse(OK, json = Json.obj(), Map()))
            )
          }
        }
      }

      "response is any other status, e.g ISE" must {

        "return an Upstream Error response and log an error" in {

          withCaptureOfLoggingFrom(CompanyRegistrationHttpParsers.logger) { logs =>
            intercept[DownstreamExceptions.CompanyRegistrationException](CompanyRegistrationHttpParsers.createCorporationTaxRegistrationDetailsHttpReads(regId).read("", "", HttpResponse(INTERNAL_SERVER_ERROR, "")))
            logs.containsMsg(Level.ERROR, s"[createCorporationTaxRegistrationDetailsHttpReads] Calling url: '' returned unexpected status: '$INTERNAL_SERVER_ERROR' for regId: '$regId'")
          }
        }
      }
    }

    "calling .corporationTaxRegistrationHttpReads(regId: String)" when {
      val createCorporationTax = CorporationTaxRegistrationResponse(
        regId,
        "testTimestamp"
      )

      val createCorporationTaxResponseJson = Json.obj(
        "registrationID" -> regId,
        "formCreationTimestamp" -> "testTimestamp"
      )

      "response is OK and JSON is valid" must {
        "return the CorporationTaxRegistrationResponse" in {
          CompanyRegistrationHttpParsers.corporationTaxRegistrationHttpReads(regId).read("", "",
            HttpResponse(OK, json = createCorporationTaxResponseJson, Map())
          ) mustBe Some(createCorporationTax)
        }
      }

      "response is OK, JSON is valid BUT Json is missing" must {

        "throw a DownstreamExceptions.CompanyRegistrationException and log an error message" in {

          withCaptureOfLoggingFrom(CompanyRegistrationHttpParsers.logger) { logs =>
            intercept[JsResultException](
              CompanyRegistrationHttpParsers.corporationTaxRegistrationHttpReads(regId).read("", "", HttpResponse(OK, json = Json.obj(), Map()))
            )
          }
        }
      }

      "response is NOT_FOUND" must {

        "return a None and log an info message" in {

          withCaptureOfLoggingFrom(CompanyRegistrationHttpParsers.logger) { logs =>
            CompanyRegistrationHttpParsers.corporationTaxRegistrationHttpReads(regId).read("", "", HttpResponse(NOT_FOUND, "")) mustBe None
            logs.containsMsg(Level.INFO, s"[corporationTaxRegistrationHttpReads] A call was made to company reg and a NotFound response was returned for regId: $regId")
          }
        }
      }

      "response is any other status, e.g ISE" must {

        "return an Upstream Error response and log an error" in {

          withCaptureOfLoggingFrom(CompanyRegistrationHttpParsers.logger) { logs =>
            intercept[DownstreamExceptions.CompanyRegistrationException](CompanyRegistrationHttpParsers.corporationTaxRegistrationHttpReads(regId).read("", "", HttpResponse(INTERNAL_SERVER_ERROR, "")))
            logs.containsMsg(Level.ERROR, s"[corporationTaxRegistrationHttpReads] Calling url: '' returned unexpected status: '$INTERNAL_SERVER_ERROR' for regId: '$regId'")
          }
        }
      }
    }

    "calling .accountingDetailsHttpReads(regId: String)" when {
      val accountingDetails = AccountingDetails(
        FUTURE_DATE,
        Some("2016-08-03"),
        Links(None))

      val accountingDetailsResponseJson = Json.obj(
        "accountingDateStatus" -> FUTURE_DATE,
        "startDateOfBusiness" -> "2016-08-03",
        "links" -> Json.obj()
      )

      "response is OK and JSON is valid" must {
        "return the accountingDetails response" in {
          CompanyRegistrationHttpParsers.accountingDetailsHttpReads(regId).read("", "",
            HttpResponse(OK, json = accountingDetailsResponseJson, Map())
          ) mustBe AccountingDetailsSuccessResponse(accountingDetails)
        }
      }

      "response is OK, JSON is valid BUT Json is missing" must {

        "throw a DownstreamExceptions.CompanyRegistrationException and log an error message" in {

          withCaptureOfLoggingFrom(CompanyRegistrationHttpParsers.logger) { logs =>
            intercept[JsResultException](
              CompanyRegistrationHttpParsers.accountingDetailsHttpReads(regId).read("", "", HttpResponse(OK, json = Json.obj(), Map()))
            )
          }
        }
      }

      "response is NOT_FOUND" must {

        "return a AccountingDetailsNotFoundResponse and log an info message" in {

          withCaptureOfLoggingFrom(CompanyRegistrationHttpParsers.logger) { logs =>
            CompanyRegistrationHttpParsers.accountingDetailsHttpReads(regId).read("", "", HttpResponse(NOT_FOUND, "")) mustBe AccountingDetailsNotFoundResponse
            logs.containsMsg(Level.INFO, s"[accountingDetailsHttpReads] A call was made to company reg and a NotFound response was returned for regId: $regId")
          }
        }
      }

      "response is any other status, e.g ISE" must {

        "return an Upstream Error response and log an error" in {

          withCaptureOfLoggingFrom(CompanyRegistrationHttpParsers.logger) { logs =>
            intercept[DownstreamExceptions.CompanyRegistrationException](CompanyRegistrationHttpParsers.accountingDetailsHttpReads(regId).read("", "", HttpResponse(INTERNAL_SERVER_ERROR, "")))
            logs.containsMsg(Level.ERROR, s"[accountingDetailsHttpReads] Calling url: '' returned unexpected status: '$INTERNAL_SERVER_ERROR' for regId: '$regId'")
          }
        }
      }
    }


    "calling .contactDetailsHttpReads(regId: String)" when {
      val contactDetails = CompanyContactDetails(Some("afoo@bar.wibble"), Some(""), Some(""), Links(None, None))

      val contactDetailsResponseJson = Json.obj(
        "contactEmail" -> "afoo@bar.wibble",
        "contactDaytimeTelephoneNumber" -> "",
        "contactMobileNumber" -> "",
        "links" -> Json.obj(),
      )

      "response is OK and JSON is valid" must {
        "return the contactDetails response" in {
          CompanyRegistrationHttpParsers.contactDetailsHttpReads(regId).read("", "",
            HttpResponse(OK, json = contactDetailsResponseJson, Map())
          ) mustBe CompanyContactDetailsSuccessResponse(contactDetails)
        }
      }

      "response is OK, JSON is valid BUT Json is missing" must {

        "throw a DownstreamExceptions.CompanyRegistrationException and log an error message" in {

          withCaptureOfLoggingFrom(CompanyRegistrationHttpParsers.logger) { logs =>
            intercept[JsResultException](
              CompanyRegistrationHttpParsers.contactDetailsHttpReads(regId).read("", "", HttpResponse(OK, json = Json.obj(), Map()))
            )
          }
        }
      }

      "response is NOT_FOUND" must {

        "return a CompanyContactDetailsNotFoundResponse and log an info message" in {

          withCaptureOfLoggingFrom(CompanyRegistrationHttpParsers.logger) { logs =>
            CompanyRegistrationHttpParsers.contactDetailsHttpReads(regId).read("", "", HttpResponse(NOT_FOUND, "")) mustBe CompanyContactDetailsNotFoundResponse
            logs.containsMsg(Level.INFO, s"[contactDetailsHttpReads] A call was made to company reg and a NotFound response was returned for regId: $regId")
          }
        }
      }

      "response is any other status, e.g ISE" must {

        "return an Upstream Error response and log an error" in {

          withCaptureOfLoggingFrom(CompanyRegistrationHttpParsers.logger) { logs =>
            intercept[DownstreamExceptions.CompanyRegistrationException](CompanyRegistrationHttpParsers.contactDetailsHttpReads(regId).read("", "", HttpResponse(INTERNAL_SERVER_ERROR, "")))
            logs.containsMsg(Level.ERROR, s"[contactDetailsHttpReads] Calling url: '' returned unexpected status: '$INTERNAL_SERVER_ERROR' for regId: '$regId'")
          }
        }
      }
    }

    "calling .tradingDetailsHttpReads(regId: String)" when {
      val tradingDetails = TradingDetails("test")

      val tradingDetailsResponseJson = Json.obj(
        "regularPayments" -> "test"
      )

      "response is OK and JSON is valid" must {
        "return the tradingDetails response" in {
          CompanyRegistrationHttpParsers.tradingDetailsHttpReads(regId).read("", "",
            HttpResponse(OK, json = tradingDetailsResponseJson, Map())
          ) mustBe Some(tradingDetails)
        }
      }

      "response is OK, JSON is valid BUT Json is missing" must {

        "throw a DownstreamExceptions.CompanyRegistrationException and log an error message" in {

          withCaptureOfLoggingFrom(CompanyRegistrationHttpParsers.logger) { logs =>
            intercept[JsResultException](
              CompanyRegistrationHttpParsers.tradingDetailsHttpReads(regId).read("", "", HttpResponse(OK, json = Json.obj(), Map()))
            )
          }
        }
      }

      "response is NOT_FOUND" must {

        "return a CompanyContactDetailsNotFoundResponse and log an info message" in {

          withCaptureOfLoggingFrom(CompanyRegistrationHttpParsers.logger) { logs =>
            CompanyRegistrationHttpParsers.tradingDetailsHttpReads(regId).read("", "", HttpResponse(NOT_FOUND, "")) mustBe None
            logs.containsMsg(Level.INFO, s"[tradingDetailsHttpReads] A call was made to company reg and a NotFound response was returned for regId: $regId")
          }
        }
      }

      "response is any other status, e.g ISE" must {

        "return an Upstream Error response and log an error" in {

          withCaptureOfLoggingFrom(CompanyRegistrationHttpParsers.logger) { logs =>
            intercept[DownstreamExceptions.CompanyRegistrationException](CompanyRegistrationHttpParsers.tradingDetailsHttpReads(regId).read("", "", HttpResponse(INTERNAL_SERVER_ERROR, "")))
            logs.containsMsg(Level.ERROR, s"[tradingDetailsHttpReads] Calling url: '' returned unexpected status: '$INTERNAL_SERVER_ERROR' for regId: '$regId'")
          }
        }
      }
    }

    "calling .companyRegistrationDetailsHttpReads(regId: String)" when {
      val companyDetails = CompanyDetails(
        "Company Name Ltd",
        CHROAddress("premises", "BusinessAddressLine1", Some("BusinessAddressLine2"), "locality", "testCountry", None, Some("TE1 1ST"), None),
        PPOB("RO", Some(Address(
          None,
          "14 St Test Walk",
          "Testley",
          Some("Testford"),
          Some("Testshire"),
          Some("TE1 1ST"),
          Some("UK"),
          txid = "93cf1cfc-75fd-4ac0-96ac-5f0018c70a8f"
        ))),
        "ENGLAND_AND_WALES"
      )

      val companyDetailsResponseJson = Json.obj(
        "companyName" -> "Company Name Ltd",
        "cHROAddress" -> Json.obj(
          "premises" -> "premises",
          "address_line_1" -> "BusinessAddressLine1",
          "address_line_2" -> "BusinessAddressLine2",
          "locality" -> "locality",
          "country" -> "testCountry",
          "postal_code" -> "TE1 1ST"
        ),
        "pPOBAddress" -> Json.obj(
          "addressType" -> "RO",
          "address" -> Json.obj(
            "addressLine1" -> "14 St Test Walk",
            "addressLine2" -> "Testley",
            "addressLine3" -> "Testford",
            "addressLine4" -> "Testshire",
            "postCode" -> "TE1 1ST",
            "country" -> "UK",
            "txid" -> "93cf1cfc-75fd-4ac0-96ac-5f0018c70a8f"
          )
        ),
        "jurisdiction" -> "ENGLAND_AND_WALES"
      )

      "response is OK and JSON is valid" must {
        "return the companyRegistration response" in {
          CompanyRegistrationHttpParsers.companyRegistrationDetailsHttpReads(regId).read("", "",
            HttpResponse(OK, json = companyDetailsResponseJson, Map())
          ) mustBe Some(companyDetails)
        }
      }

      "response is OK, JSON is valid BUT Json is missing" must {

        "throw a DownstreamExceptions.CompanyRegistrationException and log an error message" in {

          withCaptureOfLoggingFrom(CompanyRegistrationHttpParsers.logger) { logs =>
            intercept[JsResultException](
              CompanyRegistrationHttpParsers.companyRegistrationDetailsHttpReads(regId).read("", "", HttpResponse(OK, json = Json.obj(), Map()))
            )
          }
        }
      }

      "response is NOT_FOUND" must {

        "return a CompanyContactDetailsNotFoundResponse and log an info message" in {

          withCaptureOfLoggingFrom(CompanyRegistrationHttpParsers.logger) { logs =>
            CompanyRegistrationHttpParsers.companyRegistrationDetailsHttpReads(regId).read("", "", HttpResponse(NOT_FOUND, "")) mustBe None
            logs.containsMsg(Level.INFO, s"[companyRegistrationDetailsHttpReads] A call was made to company reg and a NotFound response was returned for regId: $regId")
          }
        }
      }

      "response is any other status, e.g ISE" must {

        "return an Upstream Error response and log an error" in {

          withCaptureOfLoggingFrom(CompanyRegistrationHttpParsers.logger) { logs =>
            intercept[DownstreamExceptions.CompanyRegistrationException](CompanyRegistrationHttpParsers.companyRegistrationDetailsHttpReads(regId).read("", "", HttpResponse(INTERNAL_SERVER_ERROR, "")))
            logs.containsMsg(Level.ERROR, s"[companyRegistrationDetailsHttpReads] Calling url: '' returned unexpected status: '$INTERNAL_SERVER_ERROR' for regId: '$regId'")
          }
        }
      }
    }

  }
}
