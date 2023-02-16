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

import connectors.httpParsers.exceptions.DownstreamExceptions
import models._
import play.api.http.Status.{BAD_REQUEST, NOT_FOUND, NO_CONTENT, OK}
import uk.gov.hmrc.http.{HttpReads, HttpResponse}

import scala.util.{Failure, Success, Try}

trait CompanyRegistrationHttpParsers extends BaseHttpReads {
  _: BaseConnector =>

  override def unexpectedStatusException(url: String, status: Int, regId: Option[String], txId: Option[String]): Exception =
    new exceptions.DownstreamExceptions.CompanyRegistrationException(s"Calling url: '$url' returned unexpected status: '$status'${logContext(regId, txId)}")

  def companyRegistrationDetailsHttpReads(regId: String): HttpReads[Option[CompanyDetails]] = {
    (_: String, url: String, response: HttpResponse) =>
      response.status match {
        case OK =>
          Try(response.json.as[CompanyDetails](CompanyDetails.formats)) match {
            case Success(profile) => Some(profile)
            case Failure(e: DownstreamExceptions.CompanyRegistrationException) =>
              logger.error(s"[companyRegistrationDetailsHttpReads] Received an error when expecting a Company Registration document for reg id: $regId could not find confirmation references (has user completed Incorp/CT?)")
              throw e
            case Failure(e) =>
              logger.error(s"[companyRegistrationDetailsHttpReads] JSON returned from company-registration could not be parsed to CompanyRegistrationProfile model for reg id: $regId")
              throw e
          }
        case NOT_FOUND => {
          logger.info(s"[companyRegistrationDetailsHttpReads] A call was made to company reg and a NotFound response was returned for regId: $regId")
          None
        }
        case status =>
          unexpectedStatusHandling()("companyRegistrationDetailsHttpReads", url, status, Some(regId))
      }
  }

  def tradingDetailsHttpReads(regId: String): HttpReads[Option[TradingDetails]] = {
    (_: String, url: String, response: HttpResponse) =>
      response.status match {
        case OK =>
          Try(response.json.as[TradingDetails](TradingDetails.format)) match {
            case Success(tradingDetails) => Some(tradingDetails)
            case Failure(e: DownstreamExceptions.CompanyRegistrationException) =>
              logger.error(s"[tradingDetailsHttpReads] Received an error when expecting trading details for reg id: $regId")
              throw e
            case Failure(e) =>
              logger.error(s"[tradingDetailsHttpReads] JSON returned from company-registration could not be parsed to TradingDetails model for reg id: $regId")
              throw e
          }
        case NOT_FOUND => {
          logger.info(s"[tradingDetailsHttpReads] A call was made to company reg and a NotFound response was returned for regId: $regId")
          None
        }
        case status =>
          unexpectedStatusHandling()("tradingDetailsHttpReads", url, status, Some(regId))
      }
  }

  def contactDetailsHttpReads(regId: String): HttpReads[CompanyContactDetailsResponse] = {
    (_: String, url: String, response: HttpResponse) =>
      response.status match {
        case OK =>
          Try(response.json.as[CompanyContactDetails](CompanyContactDetails.formats)) match {
            case Success(contactDetails) => CompanyContactDetailsSuccessResponse(contactDetails)
            case Failure(e: DownstreamExceptions.CompanyRegistrationException) =>
              logger.error(s"[contactDetailsHttpReads] Received an error when expecting contact details for reg id: $regId")
              throw e
            case Failure(e) =>
              logger.error(s"[contactDetailsHttpReads] JSON returned from company-registration could not be parsed to ContactDetails model for reg id: $regId")
              throw e
          }
        case NOT_FOUND => {
          logger.info(s"[contactDetailsHttpReads] A call was made to company reg and a NotFound response was returned for regId: $regId")
          CompanyContactDetailsNotFoundResponse
        }
        case status =>
          unexpectedStatusHandling()("contactDetailsHttpReads", url, status, Some(regId))
      }
  }

  def accountingDetailsHttpReads(regId: String): HttpReads[AccountingDetailsResponse] = {
    (_: String, url: String, response: HttpResponse) =>
      response.status match {
        case OK =>
          Try(response.json.as[AccountingDetails](AccountingDetails.formats)) match {
            case Success(accountingDetails) => AccountingDetailsSuccessResponse(accountingDetails)
            case Failure(e: DownstreamExceptions.CompanyRegistrationException) =>
              logger.error(s"[accountingDetailsHttpReads] Received an error when expecting contact details for reg id: $regId")
              throw e
            case Failure(e) =>
              logger.error(s"[accountingDetailsHttpReads] JSON returned from company-registration could not be parsed to AccountingDetails model for reg id: $regId")
              throw e
          }
        case NOT_FOUND => {
          logger.info(s"[accountingDetailsHttpReads] A call was made to company reg and a NotFound response was returned for regId: $regId")
          AccountingDetailsNotFoundResponse
        }
        case status =>
          unexpectedStatusHandling()("accountingDetailsHttpReads", url, status, Some(regId))
      }
  }

  def corporationTaxRegistrationHttpReads(regId: String): HttpReads[Option[CorporationTaxRegistrationResponse]] = {
    (_: String, url: String, response: HttpResponse) =>
      response.status match {
        case OK =>
          Try(response.json.as[CorporationTaxRegistrationResponse](CorporationTaxRegistrationResponse.formats)) match {
            case Success(corporationTax) => Some(CorporationTaxRegistrationResponse(corporationTax.registrationID, corporationTax.formCreationTimestamp))
            case Failure(e: DownstreamExceptions.CompanyRegistrationException) =>
              logger.error(s"[corporationTaxRegistrationHttpReads] Received an error when expecting contact details for reg id: $regId")
              throw e
            case Failure(e) =>
              logger.error(s"[corporationTaxRegistrationHttpReads] JSON returned from corporation-tax could not be parsed to CorporationTaxRegistrationResponse model for reg id: $regId")
              throw e
          }
        case NOT_FOUND => {
          logger.info(s"[corporationTaxRegistrationHttpReads] A call was made to company reg and a NotFound response was returned for regId: $regId")
          None
        }
        case status =>
          unexpectedStatusHandling()("corporationTaxRegistrationHttpReads", url, status, Some(regId))
      }
  }

  def updateLanguageHttpReads(regId: String, language: Language): HttpReads[Boolean] = (_: String, _: String, response: HttpResponse) => {
    val logContext = s" language to: '${language.code}' for regId: '$regId'"
    response.status match {
      case NO_CONTENT =>
        logger.debug(s"[updateLanguageHttpReads] Updated" + logContext)
        true
      case NOT_FOUND =>
        logger.warn(s"[updateLanguageHttpReads] No document was found ($NOT_FOUND) when attempting to update" + logContext)
        false
      case status =>
        logger.error(s"[updateLanguageHttpReads] An unexpected status of '$status' was returned when attempting to update" + logContext)
        false
    }
  }

  def createCorporationTaxRegistrationDetailsHttpReads(regId: String): HttpReads[CorporationTaxRegistrationResponse]= (_: String, _: String, response: HttpResponse) => {
    response.status match {
      case OK =>
        Try(response.json.as[CorporationTaxRegistrationResponse](CorporationTaxRegistrationResponse.formats)) match {
          case Success(corporationTaxResponse) => corporationTaxResponse
          case Failure(e: DownstreamExceptions.CompanyRegistrationException) =>
            logger.error(s"[createCorporationTaxRegistrationDetails] Received an error when expecting CT registration for reg id: $regId")
            throw e
          case Failure(e) =>
            logger.error(s"[createCorporationTaxRegistrationDetails] JSON returned from corporation-tax could not be parsed to CorporationTaxRegistrationResponse model for reg id: $regId")
            throw e
        }
      case status =>
        unexpectedStatusHandling()("createCorporationTaxRegistrationDetailsHttpReads", "", status, Some(regId))
    }
  }

  def validateRegisteredOfficeAddressHttpReads(regId: String): HttpReads[Option[NewAddress]] = (_: String, _: String, response: HttpResponse) => {
    response.status match {
      case OK => Try(response.json.asOpt[NewAddress](Groups.formatsNewAddressGroups)) match {
        case Success(response) =>  {
          response
        }
        case Failure(e: DownstreamExceptions.CompanyRegistrationException) =>
          logger.error(s"[validateRegisteredOfficeAddressHttpReads] Received an error when expecting NewAddress for reg id: $regId")
          throw e
        case Failure(e) =>
          logger.error(s"[validateRegisteredOfficeAddressHttpReads] JSON returned from validation could not be parsed to NewAddress model for reg id: $regId")
          throw e
      }
      case BAD_REQUEST => None
      case status =>
        unexpectedStatusHandling()("validateRegisteredOfficeAddressHttpReads", "", status, Some(regId))
    }
  }

  def updateCompanyDetailsHttpReads(regId: String): HttpReads[CompanyDetails] = (_: String, _: String, response: HttpResponse) => {
    response.status match {
      case OK => Try(response.json.as[CompanyDetails](CompanyDetails.formats)) match {
        case Success(companyDetails) => companyDetails
        case Failure(e: DownstreamExceptions.CompanyRegistrationException) =>
          logger.error(s"[updateCompanyDetailsHttpReads] Received an error when expecting CompanyDetails for reg id: $regId")
          throw e
        case Failure(e) =>
          logger.error(s"[updateCompanyDetailsHttpReads] JSON returned from company-details could not be parsed to CompanyDetails model for reg id: $regId")
          throw e
      }
      case status =>
        unexpectedStatusHandling()("updateCompanyDetailsHttpReads", "", status, Some(regId))
    }
  }

  def updateTradingDetailsHttpReads(regId: String): HttpReads[TradingDetails] = (_: String, _: String, response: HttpResponse) => {
    response.status match {
      case OK => Try(response.json.as[TradingDetails](TradingDetails.format)) match {
        case Success(tradingDetails) => tradingDetails
        case Failure(e: DownstreamExceptions.CompanyRegistrationException) =>
          logger.error(s"[updateTradingDetailsHttpReads] Received an error when expecting TradingDetails for reg id: $regId")
          throw e
        case Failure(e) =>
          logger.error(s"[updateTradingDetailsHttpReads] JSON returned from trading-details could not be parsed to TradingDetails model for reg id: $regId")
          throw e
      }
      case status =>
        unexpectedStatusHandling()("updateTradingDetailsHttpReads", "", status, Some(regId))
    }
  }

  def updateContactDetailsHttpReads(regId: String): HttpReads[CompanyContactDetails] = (_: String, _: String, response: HttpResponse) => {
    response.status match {
      case OK => Try(response.json.as[CompanyContactDetails](CompanyContactDetails.formats)) match {
        case Success(contactDetails) => contactDetails
        case Failure(e: DownstreamExceptions.CompanyRegistrationException) =>
          logger.error(s"[updateContactDetailsHttpReads] Received an error when expecting CompanyContactDetails for reg id: $regId")
          throw e
        case Failure(e) =>
          logger.error(s"[updateContactDetailsHttpReads] JSON returned from trading-details could not be parsed to CompanyContactDetails model for reg id: $regId")
          throw e
      }
      case status =>
        unexpectedStatusHandling()("updateContactDetailsHttpReads", "", status, Some(regId))
    }
  }

  def updateAccountingDetailsHttpReads(regId: String): HttpReads[AccountingDetails] = (_: String, _: String, response: HttpResponse) => {
    response.status match {
      case OK => Try(response.json.as[AccountingDetails](AccountingDetails.formats)) match {
        case Success(accountingDetails) => accountingDetails
        case Failure(e: DownstreamExceptions.CompanyRegistrationException) =>
          logger.error(s"[updateAccountingDetailsHttpReads] Received an error when expecting AccountingDetails for reg id: $regId")
          throw e
        case Failure(e) =>
          logger.error(s"[updateAccountingDetailsHttpReads] JSON returned from accounting-details could not be parsed to AccountingDetails model for reg id: $regId")
          throw e
      }
      case status =>
        unexpectedStatusHandling()("updateAccountingDetailsHttpReads", "", status, Some(regId))
    }
  }

  def retrieveEmailHttpReads(regId: String): HttpReads[Option[Email]] = (_: String, _: String, response: HttpResponse) => {
    response.status match {
      case OK => Try(response.json.as[Email](Email.reads)) match {
        case Success(emailResponse) => Some(emailResponse)
        case Failure(e: DownstreamExceptions.CompanyRegistrationException) =>
          logger.error(s"[retrieveEmailHttpReads] Received an error when expecting Email for reg id: $regId")
          throw e
        case Failure(e) =>
          logger.error(s"[retrieveEmailHttpReads] JSON returned from email could not be parsed to Email model for reg id: $regId")
          throw e
      }
      case NOT_FOUND =>
        logger.info(s"[retrieveEmailHttpReads] A call was made to company reg and a NotFound response was returned for regId: $regId")
        None
      case status =>
        unexpectedStatusHandling()("retrieveEmailHttpReads", "", status, Some(regId))
    }
  }
}

object CompanyRegistrationHttpParsers extends CompanyRegistrationHttpParsers with BaseConnector
