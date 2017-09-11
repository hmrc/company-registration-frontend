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

package connectors

import config.WSHttp
import models._
import models.connectors.ConfirmationReferences
import play.api.Logger
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http._
import play.api.http.Status._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object CompanyRegistrationConnector extends CompanyRegistrationConnector with ServicesConfig {
  val companyRegUrl = baseUrl("company-registration")
  val http = WSHttp
}

sealed trait FootprintResponse
case class FootprintFound(throttleResponse: ThrottleResponse) extends FootprintResponse
case object FootprintForbiddenResponse extends FootprintResponse
case object FootprintTooManyRequestsResponse extends FootprintResponse
case class FootprintErrorResponse(err: Exception) extends FootprintResponse


trait CompanyRegistrationConnector {

  val companyRegUrl: String
  val http: HttpGet with HttpPut with HttpPost with HttpDelete

  def retrieveCorporationTaxRegistration(registrationID: String)(implicit hc: HeaderCarrier): Future[JsValue] = {
    http.GET[JsValue](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/corporation-tax-registration") recover {
        case ex: BadRequestException =>
          Logger.error(s"[CompanyRegistrationConnector] [retrieveCorporationTaxRegistration] for RegId: $registrationID - ${ex.responseCode} ${ex.message}")
          throw ex
        case ex: NotFoundException =>
          Logger.info(s"[CompanyRegistrationConnector] [retrieveCorporationTaxRegistration] for RegId: $registrationID - ${ex.responseCode} ${ex.message}")
          throw ex
        case ex: Upstream4xxResponse =>
          Logger.error(s"[CompanyRegistrationConnector] [retrieveCorporationTaxRegistration] for RegId: $registrationID - ${ex.upstreamResponseCode} ${ex.message}")
          throw ex
        case ex: Upstream5xxResponse =>
          Logger.error(s"[CompanyRegistrationConnector] [retrieveCorporationTaxRegistration] for RegId: $registrationID - ${ex.upstreamResponseCode} ${ex.message}")
          throw ex
        case ex: Exception =>
          Logger.error(s"[CompanyRegistrationConnector] [retrieveCorporationTaxRegistration] for RegId: $registrationID - Unexpected error occured: $ex")
          throw ex
      }
  }

  def retrieveOrCreateFootprint()(implicit hc: HeaderCarrier): Future[FootprintResponse] = {

    http.GET[ThrottleResponse](s"$companyRegUrl/company-registration/throttle/check-user-access") map {
      response => {
        Logger.debug(s"[CompanyRegistrationConnector] [retrieveOrCreateFootprint] response is $response")
        FootprintFound(response)
      }
    } recover {
      case e: ForbiddenException =>
        Logger.error(s"[CompanyRegistrationConnector] [retrieveOrCreateFootprint] - Received a Forbidden status code when expecting footprint")
        FootprintForbiddenResponse

      case e: Upstream4xxResponse =>
        Logger.error(s"[CompanyRegistrationConnector] [retrieveOrCreateFootprint] - Received a 4xx status code ${e.upstreamResponseCode} when expecting footprint")
        e.upstreamResponseCode match {
          case 429 => FootprintTooManyRequestsResponse
          case _ => FootprintErrorResponse(e)
        }

      case e: Exception =>
        Logger.error(s"[CompanyRegistrationConnector] [retrieveOrCreateFootprint] - Received an error when expecting footprint - ${e.getMessage}")
        FootprintErrorResponse(e)
    }

  }

  def createCorporationTaxRegistrationDetails(regId: String)(implicit hc: HeaderCarrier): Future[CorporationTaxRegistrationResponse] = {
    val json = Json.toJson[CorporationTaxRegistrationRequest](CorporationTaxRegistrationRequest("en"))
    http.PUT[JsValue, CorporationTaxRegistrationResponse](s"$companyRegUrl/company-registration/corporation-tax-registration/$regId", json) recover {
      case ex: BadRequestException =>
        Logger.error(s"[CompanyRegistrationConnector] [createCorporationTaxRegistrationDetails] for RegId: $regId - ${ex.responseCode} ${ex.message}")
        throw ex
      case ex: NotFoundException =>
        Logger.info(s"[CompanyRegistrationConnector] [createCorporationTaxRegistrationDetails] for RegId: $regId - ${ex.responseCode} ${ex.message}")
        throw ex
      case ex: Upstream4xxResponse =>
        Logger.error(s"[CompanyRegistrationConnector] [createCorporationTaxRegistrationDetails] for RegId: $regId - ${ex.upstreamResponseCode} ${ex.message}")
        throw ex
      case ex: Upstream5xxResponse =>
        Logger.error(s"[CompanyRegistrationConnector] [createCorporationTaxRegistrationDetails] for RegId: $regId - ${ex.upstreamResponseCode} ${ex.message}")
        throw ex
      case ex: Exception =>
        Logger.error(s"[CompanyRegistrationConnector] [createCorporationTaxRegistrationDetails] for RegId: $regId - Unexpected error occured: $ex")
        throw ex
    }
  }

  def retrieveCorporationTaxRegistrationDetails(registrationID: String)
                                               (implicit hc: HeaderCarrier, rds: HttpReads[CorporationTaxRegistrationResponse]): Future[Option[CorporationTaxRegistrationResponse]] = {
    http.GET[Option[CorporationTaxRegistrationResponse]](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID") recover {
      case ex: BadRequestException =>
        Logger.error(s"[CompanyRegistrationConnector] [retrieveCorporationTaxRegistrationDetails] for RegId: $registrationID - ${ex.responseCode} ${ex.message}")
        None
      case ex: NotFoundException =>
        Logger.info(s"[CompanyRegistrationConnector] [retrieveCorporationTaxRegistrationDetails] for RegId: $registrationID - ${ex.responseCode} ${ex.message}")
        None
      case ex: Upstream4xxResponse =>
        Logger.error(s"[CompanyRegistrationConnector] [retrieveCorporationTaxRegistrationDetails] for RegId: $registrationID - ${ex.upstreamResponseCode} ${ex.message}")
        None
      case ex: Upstream5xxResponse =>
        Logger.error(s"[CompanyRegistrationConnector] [retrieveCorporationTaxRegistrationDetails] for RegId: $registrationID - ${ex.upstreamResponseCode} ${ex.message}")
        None
      case ex: Exception =>
        Logger.error(s"[CompanyRegistrationConnector] [retrieveCorporationTaxRegistrationDetails] for RegId: $registrationID - Unexpected error occured: $ex")
        None
    }
  }

  def updateCompanyDetails(registrationID: String, companyDetails: CompanyDetails)(implicit hc: HeaderCarrier): Future[CompanyDetails] = {
    val json = Json.toJson[CompanyDetails](companyDetails)
    http.PUT[JsValue, CompanyDetails](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/company-details", json) recover {
      case ex: BadRequestException =>
        Logger.error(s"[CompanyRegistrationConnector] [updateCompanyDetails] for RegId: $registrationID - ${ex.responseCode} ${ex.message}")
        throw ex
      case ex: NotFoundException =>
        Logger.info(s"[CompanyRegistrationConnector] [updateCompanyDetails] for RegId: $registrationID - ${ex.responseCode} ${ex.message}")
        throw ex
      case ex: Upstream4xxResponse =>
        Logger.error(s"[CompanyRegistrationConnector] [updateCompanyDetails] for RegId: $registrationID - ${ex.upstreamResponseCode} ${ex.message}")
        throw ex
      case ex: Upstream5xxResponse =>
        Logger.error(s"[CompanyRegistrationConnector] [updateCompanyDetails] for RegId: $registrationID - ${ex.upstreamResponseCode} ${ex.message}")
        throw ex
      case ex: Exception =>
        Logger.error(s"[CompanyRegistrationConnector] [updateCompanyDetails] for RegId: $registrationID - Unexpected error occured: $ex")
        throw ex
    }
  }

  def retrieveCompanyDetails(registrationID: String)(implicit hc: HeaderCarrier): Future[Option[CompanyDetails]] = {
    http.GET[Option[CompanyDetails]](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/company-details") recover {
      case ex: BadRequestException =>
        Logger.error(s"[CompanyRegistrationConnector] [retrieveCompanyDetails] for RegId: $registrationID - ${ex.responseCode} ${ex.message}")
        None
      case ex: NotFoundException =>
        Logger.info(s"[CompanyRegistrationConnector] [retrieveCompanyDetails] for RegId: $registrationID - ${ex.responseCode} ${ex.message}")
        None
      case ex: Upstream4xxResponse =>
        Logger.error(s"[CompanyRegistrationConnector] [retrieveCompanyDetails] for RegId: $registrationID - ${ex.upstreamResponseCode} ${ex.message}")
        None
      case ex: Upstream5xxResponse =>
        Logger.error(s"[CompanyRegistrationConnector] [retrieveCompanyDetails] for RegId: $registrationID - ${ex.upstreamResponseCode} ${ex.message}")
        None
      case ex: Exception =>
        Logger.error(s"[CompanyRegistrationConnector] [retrieveCompanyDetails] for RegId: $registrationID - Unexpected error occured: $ex")
        None
    }
  }

  def retrieveTradingDetails(registrationID: String)(implicit hc: HeaderCarrier): Future[Option[TradingDetails]] = {
    http.GET[Option[TradingDetails]](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/trading-details") recover {
      case ex: BadRequestException =>
        Logger.error(s"[CompanyRegistrationConnector] [retrieveTradingDetails] for RegId: $registrationID - ${ex.responseCode} ${ex.message}")
        None
      case ex: NotFoundException =>
        Logger.info(s"[CompanyRegistrationConnector] [retrieveTradingDetails] for RegId: $registrationID - ${ex.responseCode} ${ex.message}")
        None
      case ex: Upstream4xxResponse =>
        Logger.error(s"[CompanyRegistrationConnector] [retrieveTradingDetails] for RegId: $registrationID - ${ex.upstreamResponseCode} ${ex.message}")
        None
      case ex: Upstream5xxResponse =>
        Logger.error(s"[CompanyRegistrationConnector] [retrieveTradingDetails] for RegId: $registrationID - ${ex.upstreamResponseCode} ${ex.message}")
        None
      case ex: Exception =>
        Logger.error(s"[CompanyRegistrationConnector] [retrieveTradingDetails] for RegId: $registrationID - Unexpected error occured: $ex")
        None
    }
  }

  def updateTradingDetails(registrationID: String, tradingDetails: TradingDetails)(implicit hc: HeaderCarrier): Future[TradingDetailsResponse] = {
    http.PUT[JsValue, TradingDetails](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/trading-details", Json.toJson[TradingDetails](tradingDetails))
      .map {
        tradingDetailsResp =>
          TradingDetailsSuccessResponse(tradingDetailsResp)
      } recover {
      case ex: BadRequestException =>
        Logger.error(s"[CompanyRegistrationConnector] [updateTradingDetails] for RegId: $registrationID - ${ex.responseCode} ${ex.message}")
        TradingDetailsNotFoundResponse
      case ex: NotFoundException =>
        Logger.info(s"[CompanyRegistrationConnector] [updateTradingDetails] for RegId: $registrationID - ${ex.responseCode} ${ex.message}")
        TradingDetailsNotFoundResponse
      case ex: Upstream4xxResponse =>
        Logger.error(s"[CompanyRegistrationConnector] [updateTradingDetails] for RegId: $registrationID - ${ex.upstreamResponseCode} ${ex.message}")
        TradingDetailsNotFoundResponse
      case ex: Upstream5xxResponse =>
        Logger.error(s"[CompanyRegistrationConnector] [updateTradingDetails] for RegId: $registrationID - ${ex.upstreamResponseCode} ${ex.message}")
        TradingDetailsNotFoundResponse
      case ex: Exception =>
        Logger.error(s"[CompanyRegistrationConnector] [updateTradingDetails] for RegId: $registrationID - Unexpected error occured: $ex")
        TradingDetailsNotFoundResponse
    }
  }

  def retrieveContactDetails(registrationID: String)(implicit hc: HeaderCarrier): Future[CompanyContactDetailsResponse] = {
    http.GET[CompanyContactDetails](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/contact-details") map {
      response => CompanyContactDetailsSuccessResponse(response)
    } recover {
      case e: BadRequestException =>
        Logger.error(s"[CompanyRegistrationConnector] [retrieveContactDetails] - Received a Bad Request status code when expecting contact details from Company-Registration")
        CompanyContactDetailsBadRequestResponse
      case e: NotFoundException =>
        Logger.info(s"[CompanyRegistrationConnector] [retrieveContactDetails] - Received a Not Found status code when expecting contact details from Company-Registration")
        CompanyContactDetailsNotFoundResponse
      case e: ForbiddenException =>
        Logger.error(s"[CompanyRegistrationConnector] [retrieveContactDetails] - Received a Forbidden status code when expecting contact details from Company-Registration")
        CompanyContactDetailsForbiddenResponse
      case e: Exception =>
        Logger.error(s"[CompanyRegistrationConnector] [retrieveContactDetails] - Received an error when expecting contact details from Company-Registration - ${e.getMessage}")
        CompanyContactDetailsBadRequestResponse
    }
  }

  def updateContactDetails(registrationID: String, contactDetails: CompanyContactDetailsMongo)(implicit hc: HeaderCarrier): Future[CompanyContactDetailsResponse] = {
    val json = Json.toJson(contactDetails)
    http.PUT[JsValue, CompanyContactDetails](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/contact-details", json) map {
      response => CompanyContactDetailsSuccessResponse(response)
    } recover {
      case e: BadRequestException =>
        Logger.error(s"[CompanyRegistrationConnector] [updateContactDetails] - Received a Bad Request status code when expecting contact details from Company-Registration")
        CompanyContactDetailsBadRequestResponse
      case e: NotFoundException =>
        Logger.info(s"[CompanyRegistrationConnector] [updateContactDetails] - Received a Not Found status code when expecting contact details from Company-Registration")
        CompanyContactDetailsNotFoundResponse
      case e: ForbiddenException =>
        Logger.error(s"[CompanyRegistrationConnector] [updateContactDetails] - Received a Forbidden status code when expecting contact details from Company-Registration")
        CompanyContactDetailsForbiddenResponse
      case e: Exception =>
        Logger.error(s"[CompanyRegistrationConnector] [updateContactDetails] - Received an error when expecting contact details from Company-Registration - ${e.getMessage}")
        CompanyContactDetailsBadRequestResponse
    }
  }

  def retrieveAccountingDetails(registrationID: String)(implicit hc: HeaderCarrier): Future[AccountingDetailsResponse] = {
    http.GET[AccountingDetails](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/accounting-details") map {
      response => AccountingDetailsSuccessResponse(response)
    } recover {
      case e: BadRequestException =>
        Logger.error(s"[CompanyRegistrationConnector] [retrieveAccountingDetails] - Received a Bad Request status code when expecting accounting details from Company-Registration")
        AccountingDetailsBadRequestResponse
      case e: NotFoundException =>
        Logger.info(s"[CompanyRegistrationConnector] [retrieveAccountingDetails] - Received a Not Found status code when expecting accounting details from Company-Registration")
        AccountingDetailsNotFoundResponse
      case e: Exception =>
        Logger.error(s"[CompanyRegistrationConnector] [retrieveAccountingDetails] - Received an error when expecting accounting details from Company-Registration - ${e.getMessage}")
        AccountingDetailsBadRequestResponse
    }
  }

  def updateAccountingDetails(registrationID: String, accountingDetails: AccountingDetailsRequest)(implicit hc: HeaderCarrier): Future[AccountingDetailsResponse] = {
    val json = Json.toJson(accountingDetails)
    http.PUT[JsValue, AccountingDetails](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/accounting-details", json) map {
      response => AccountingDetailsSuccessResponse(response)
    } recover {
      case e: BadRequestException =>
        Logger.error(s"[CompanyRegistrationConnector] [updateAccountingDetails] - Received a Bad Request status code when expecting accounting details from Company-Registration")
        AccountingDetailsBadRequestResponse
      case e: NotFoundException =>
        Logger.info(s"[CompanyRegistrationConnector] [updateAccountingDetails] - Received a Not Found status code when expecting accounting details from Company-Registration")
        AccountingDetailsNotFoundResponse
      case e: Exception =>
        Logger.error(s"[CompanyRegistrationConnector] [updateAccountingDetails] - Received an error when expecting accounting details from Company-Registration - ${e.getMessage}")
        AccountingDetailsBadRequestResponse
    }
  }

  def fetchConfirmationReferences(registrationID: String)(implicit hc: HeaderCarrier): Future[ConfirmationReferencesResponse] = {
    http.GET[ConfirmationReferences](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/confirmation-references") map {
      res => ConfirmationReferencesSuccessResponse(res)
    } recover {
      case e: BadRequestException =>
        Logger.error(s"[CompanyRegistrationConnector] [fetchConfirmationReferences] - Received a Bad Request status code when expecting acknowledgement ref from Company-Registration")
        ConfirmationReferencesBadRequestResponse
      case e: NotFoundException =>
        Logger.info(s"[CompanyRegistrationConnector] [fetchConfirmationReferences] - Received a Not Found status code when expecting acknowledgement ref from Company-Registration")
        ConfirmationReferencesNotFoundResponse
      case e: Exception =>
        Logger.error(s"[CompanyRegistrationConnector] [fetchConfirmationReferences] - Received an error when expecting acknowledgement ref from Company-Registration - ${e.getMessage}")
        ConfirmationReferencesErrorResponse
    }
  }

  def updateReferences(registrationID : String, payload: ConfirmationReferences)(implicit hc: HeaderCarrier): Future[ConfirmationReferencesResponse] = {
    http.PUT[JsValue, ConfirmationReferences](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/confirmation-references", Json.toJson(payload)) map {
      res => ConfirmationReferencesSuccessResponse(res)
    } recover {
      case e: Upstream4xxResponse =>
        Logger.error(s"[CompanyRegistrationConnector] [updateReferences] - Received a upstream 4xx code when expecting acknowledgement ref from Company-Registration")
        DESFailureDeskpro
      case e: Upstream5xxResponse =>
        Logger.info(s"[CompanyRegistrationConnector] [updateReferences] - Received a upstream 5xx status code when expecting acknowledgement ref from Company-Registration")
        DESFailureRetriable
      case e: Exception =>
        Logger.error(s"[CompanyRegistrationConnector] [updateReferences] - Received an unknown error when expecting acknowledgement ref from Company-Registration - ${e.getMessage}")
        DESFailureDeskpro
    }
  }

  def checkAndProcessNextSubmission(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    http.GET[HttpResponse](s"$companyRegUrl/company-registration/test-only/submission-check")
      .map(res => res)
      .recover {
      case ex: BadRequestException =>
        Logger.error(s"[CompanyRegistrationConnector] [checkAndProcessNextSubmission] - ${ex.responseCode} ${ex.message}")
        HttpResponse(BAD_REQUEST, responseString = Some(ex.getMessage))
      case ex: NotFoundException =>
        Logger.error(s"[CompanyRegistrationConnector] [checkAndProcessNextSubmission] - ${ex.responseCode} ${ex.message}")
        HttpResponse(NOT_FOUND, responseString = Some(ex.getMessage))
      case ex: Upstream4xxResponse =>
        Logger.error(s"[CompanyRegistrationConnector] [checkAndProcessNextSubmission] - ${ex.upstreamResponseCode} ${ex.message}")
        HttpResponse(ex.upstreamResponseCode, responseString = Some(ex.getMessage))
      case ex: Upstream5xxResponse =>
        Logger.error(s"[CompanyRegistrationConnector] [checkAndProcessNextSubmission] - ${ex.upstreamResponseCode} ${ex.message}")
        HttpResponse(ex.upstreamResponseCode, responseString = Some(ex.getMessage))
      case ex: HttpException =>
        Logger.error(s"[CompanyRegistrationConnector] [checkAndProcessNextSubmission] - Unexpected Http Exception was caught: ${ex.responseCode} ${ex.message}")
        HttpResponse(ex.responseCode, responseString = Some(ex.getMessage))
      case ex: Throwable =>
        Logger.error(s"[CompanyRegistrationConnector] [checkAndProcessNextSubmission] - Unexpected exception was caught: ${ex.getMessage}")
        HttpResponse(INTERNAL_SERVER_ERROR, responseString = Some(ex.getMessage))
    }
  }

  def fetchHeldSubmission(registrationId: String)(implicit hc: HeaderCarrier): Future[Option[JsValue]] = {
    http.GET[JsValue](s"$companyRegUrl/company-registration/test-only/fetch-held-submission/$registrationId") map {
      res => Some(res)
    } recover {
      case ex: BadRequestException =>
        Logger.error(s"[CompanyRegistrationConnector] [fetchHeldSubmission] for RegId: $registrationId - ${ex.responseCode} ${ex.message}")
        None
      case ex: NotFoundException =>
        Logger.info(s"[CompanyRegistrationConnector] [fetchHeldSubmission] for RegId: $registrationId - ${ex.responseCode} ${ex.message}")
        None
      case ex: Upstream4xxResponse =>
        Logger.error(s"[CompanyRegistrationConnector] [fetchHeldSubmission] for RegId: $registrationId - ${ex.upstreamResponseCode} ${ex.message}")
        None
      case ex: Upstream5xxResponse =>
        Logger.error(s"[CompanyRegistrationConnector] [fetchHeldSubmission] for RegId: $registrationId - ${ex.upstreamResponseCode} ${ex.message}")
        None
      case ex: Exception =>
        Logger.error(s"[CompanyRegistrationConnector] [fetchHeldSubmission] for RegId: $registrationId - Unexpected error occured: $ex")
        None
    }
  }

  def fetchHeldSubmissionTime(registrationId: String)(implicit hc: HeaderCarrier): Future[Option[JsValue]] = {
    http.GET[JsValue](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationId/fetch-held-time") map {
      res => Some(res)
    } recover {
      case ex: BadRequestException =>
        Logger.error(s"[CompanyRegistrationConnector] [fetchHeldTime] for RegId: $registrationId - ${ex.responseCode} ${ex.message}")
        None
      case ex: NotFoundException =>
        Logger.info(s"[CompanyRegistrationConnector] [fetchHeldTime] for RegId: $registrationId - ${ex.responseCode} ${ex.message}")
        None
      case ex: Upstream4xxResponse =>
        Logger.error(s"[CompanyRegistrationConnector] [fetchHeldTime] for RegId: $registrationId - ${ex.upstreamResponseCode} ${ex.message}")
        None
      case ex: Upstream5xxResponse =>
        Logger.error(s"[CompanyRegistrationConnector] [fetchHeldTime] for RegId: $registrationId - ${ex.upstreamResponseCode} ${ex.message}")
        None
      case ex: Exception =>
        Logger.error(s"[CompanyRegistrationConnector] [fetchHeldTime] for RegId: $registrationId - Unexpected error occured: $ex")
        None
    }
  }

  def updateTimepoint(timepoint: String)(implicit hc: HeaderCarrier) = {
    http.GET[String](s"$companyRegUrl/company-registration/test-only/update-timepoint/$timepoint")
  }

  def scheduleFeatureFlag(state: String)(implicit hc: HeaderCarrier) = {
    http.GET[JsValue](s"$companyRegUrl/company-registration/test-only/feature-flag/submissionCheck/$state")
  }

  def retrieveEmail(registrationId: String)(implicit hc: HeaderCarrier): Future[Option[Email]] = {
    http.GET[Email](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationId/retrieve-email").map{
      e => Some(e)
    } recover {
      case ex: BadRequestException =>
        Logger.info(s"[CompanyRegistrationConnector] [retrieveEmail] - Could not find a CT document for rid - $registrationId")
        None
    }
  }

  def updateEmail(registrationId: String, email: Email)(implicit hc: HeaderCarrier): Future[Option[Email]] = {
    val json = Json.toJson(email)
    http.PUT[JsValue, Email](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationId/update-email", json).map{
      e => Some(e)
    } recover {
      case ex: BadRequestException =>
        Logger.info(s"[CompanyRegistrationConnector] [updateEmail] - Could not find a CT document for rid - $registrationId")
        None
    }
  }

  def verifyEmail(registrationId: String, email: Email)(implicit hc: HeaderCarrier): Future[JsValue] = {
    val json = Json.toJson(email)
    http.PUT[JsValue, JsValue](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationId/update-email", json)
      .recover {
        case ex: Exception => Json.toJson(ex.getMessage)
      }
  }

  def updateRegistrationProgress(registrationID: String, progress: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val json = Json.obj("registration-progress" -> progress)
    http.PUT[JsValue, HttpResponse](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/progress", json)
  }

  def deleteRegistrationSubmission(registrationId: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    http.DELETE[HttpResponse](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationId/delete-submission")
  }

}
