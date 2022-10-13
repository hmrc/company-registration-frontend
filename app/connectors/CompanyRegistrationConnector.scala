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

package connectors

import javax.inject.Inject

import config.{AppConfig, WSHttp}
import models._
import models.connectors.ConfirmationReferences
import utils.Logging
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CompanyRegistrationConnectorImpl @Inject()(val appConfig: AppConfig,
                                                 val wsHttp: WSHttp) extends CompanyRegistrationConnector {
  lazy val companyRegUrl = appConfig.servicesConfig.baseUrl("company-registration")
}

sealed trait FootprintResponse
case class FootprintFound(throttleResponse: ThrottleResponse) extends FootprintResponse
case object FootprintForbiddenResponse extends FootprintResponse
case object FootprintTooManyRequestsResponse extends FootprintResponse
case class FootprintErrorResponse(err: Exception) extends FootprintResponse

trait CompanyRegistrationConnector extends Logging {

  val companyRegUrl: String
  val wsHttp: CoreGet with CorePost with CorePut with CoreDelete

  def fetchRegistrationStatus(regId: String)(implicit hc: HeaderCarrier): Future[Option[String]] = {
    retrieveCorporationTaxRegistration(regId) map {
      json => (json \ "status").asOpt[String]
    } recover {
      case _: NotFoundException => None
    }
  }

  def fetchCompanyName(regId: String)(implicit hc: HeaderCarrier): Future[String] = {
    retrieveCorporationTaxRegistration(regId).map {
      json => (json \ "companyDetails" \ "companyName").validate[String].getOrElse(throw new Exception(s"Missing company Name for regId $regId"))
    }
  }

  def retrieveCorporationTaxRegistration(registrationID: String)(implicit hc: HeaderCarrier): Future[JsValue] = {
    wsHttp.GET[JsValue](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/corporation-tax-registration") recover {
      case ex: BadRequestException =>
        logger.error(s"[retrieveCorporationTaxRegistration] for RegId: $registrationID - ${ex.responseCode} ${ex.message}")
        throw ex
      case ex: NotFoundException =>
        logger.warn(s"[retrieveCorporationTaxRegistration] for RegId: $registrationID - ${ex.responseCode} ${ex.message}")
        throw ex
      case ex: UpstreamErrorResponse =>
        logger.error(s"[retrieveCorporationTaxRegistration] for RegId: $registrationID - ${ex.statusCode} ${ex.message}")
        throw ex
      case ex: Exception =>
        logger.error(s"[retrieveCorporationTaxRegistration] for RegId: $registrationID - Unexpected error occurred: $ex")
        throw ex
    }
  }

  def checkROValidPPOB(registrationID: String, ro: CHROAddress)(implicit hc: HeaderCarrier): Future[Option[NewAddress]] = {
    implicit val roWrites = CHROAddress.formats
    val json = Json.toJson(ro)

    wsHttp.POST[JsValue, HttpResponse](s"$companyRegUrl/company-registration/corporation-tax-registration/check-ro-address", json) map { result =>
      result.status match {
        case 200 => result.json.asOpt[NewAddress](NewAddress.verifyRoToPPOB)
        case 400 => None
      }
    } recover {
      case _: BadRequestException =>
        None
      case ex: Exception =>
        logger.error(s"[checkROAddress] for RegId: $registrationID")
        throw ex
    }
  }

  def validateRegisteredOfficeAddress(registrationID: String, ro: CHROAddress)(implicit hc: HeaderCarrier): Future[Option[NewAddress]] = {
    implicit val roWrites = CHROAddress.formats
    val json = Json.toJson(ro)

    wsHttp.POST[JsValue, HttpResponse](s"$companyRegUrl/company-registration/corporation-tax-registration/check-return-business-address", json) map {
      _.json.asOpt[NewAddress](Groups.formatsNewAddressGroups)
    } recover {
      case _: BadRequestException =>
        None
      case ex: Exception =>
        logger.error(s"[checkROAddress] for RegId: $registrationID")
        throw ex
    }
  }

  def retrieveOrCreateFootprint()(implicit hc: HeaderCarrier): Future[FootprintResponse] = {

    wsHttp.GET[ThrottleResponse](s"$companyRegUrl/company-registration/throttle/check-user-access") map {
      response => {
        logger.debug(s"[retrieveOrCreateFootprint] response is $response")
        FootprintFound(response)
      }
    } recover {
      case e: ForbiddenException =>
        logger.error(s"[retrieveOrCreateFootprint] Received a Forbidden status code when expecting footprint")
        FootprintForbiddenResponse

      case e: UpstreamErrorResponse if UpstreamErrorResponse.Upstream4xxResponse.unapply(e).isDefined =>
        logger.error(s"[retrieveOrCreateFootprint] Received a 4xx status code ${e.statusCode} when expecting footprint")
        e.statusCode match {
          case 429 => FootprintTooManyRequestsResponse
          case _ => FootprintErrorResponse(e)
        }

      case e: Exception =>
        logger.error(s"[retrieveOrCreateFootprint] Received an error when expecting footprint - ${e.getMessage}")
        FootprintErrorResponse(e)
    }

  }

  def createCorporationTaxRegistrationDetails(regId: String)(implicit hc: HeaderCarrier): Future[CorporationTaxRegistrationResponse] = {
    val json = Json.toJson[CorporationTaxRegistrationRequest](CorporationTaxRegistrationRequest("en"))
    wsHttp.PUT[JsValue, CorporationTaxRegistrationResponse](s"$companyRegUrl/company-registration/corporation-tax-registration/$regId", json) recover {
      case ex: BadRequestException =>
        logger.error(s"[createCorporationTaxRegistrationDetails] for RegId: $regId - ${ex.responseCode} ${ex.message}")
        throw ex
      case ex: NotFoundException =>
        logger.warn(s"[createCorporationTaxRegistrationDetails] for RegId: $regId - ${ex.responseCode} ${ex.message}")
        throw ex
      case ex: UpstreamErrorResponse =>
        logger.error(s"[createCorporationTaxRegistrationDetails] for RegId: $regId - ${ex.statusCode} ${ex.message}")
        throw ex
      case ex: Exception =>
        logger.error(s"[createCorporationTaxRegistrationDetails] for RegId: $regId - Unexpected error occurred: $ex")
        throw ex
    }
  }

  def retrieveCorporationTaxRegistrationDetails(registrationID: String)
                                               (implicit hc: HeaderCarrier, rds: HttpReads[CorporationTaxRegistrationResponse]): Future[Option[CorporationTaxRegistrationResponse]] = {
    wsHttp.GET[Option[CorporationTaxRegistrationResponse]](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID") recover {
      case ex: BadRequestException =>
        logger.error(s"[retrieveCorporationTaxRegistrationDetails] for RegId: $registrationID - ${ex.responseCode} ${ex.message}")
        None
      case ex: NotFoundException =>
        logger.warn(s"[retrieveCorporationTaxRegistrationDetails] for RegId: $registrationID - ${ex.responseCode} ${ex.message}")
        None
      case ex: UpstreamErrorResponse =>
        logger.error(s"[retrieveCorporationTaxRegistrationDetails] for RegId: $registrationID - ${ex.statusCode} ${ex.message}")
        None
      case ex: Exception =>
        logger.error(s"[retrieveCorporationTaxRegistrationDetails] for RegId: $registrationID - Unexpected error occurred: $ex")
        None
    }
  }

  def updateCompanyDetails(registrationID: String, companyDetails: CompanyDetails)(implicit hc: HeaderCarrier): Future[CompanyDetails] = {
    val json = Json.toJson[CompanyDetails](companyDetails)
    wsHttp.PUT[JsValue, CompanyDetails](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/company-details", json) recover {
      case ex: BadRequestException =>
        logger.error(s"[updateCompanyDetails] for RegId: $registrationID - ${ex.responseCode} ${ex.message}")
        throw ex
      case ex: NotFoundException =>
        logger.warn(s"[updateCompanyDetails] for RegId: $registrationID - ${ex.responseCode} ${ex.message}")
        throw ex
      case ex: UpstreamErrorResponse =>
        logger.error(s"[updateCompanyDetails] for RegId: $registrationID - ${ex.statusCode} ${ex.message}")
        throw ex
      case ex: Exception =>
        logger.error(s"[updateCompanyDetails] for RegId: $registrationID - Unexpected error occurred: $ex")
        throw ex
    }
  }

  def retrieveCompanyDetails(registrationID: String)(implicit hc: HeaderCarrier): Future[Option[CompanyDetails]] = {
    wsHttp.GET[Option[CompanyDetails]](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/company-details") recover {
      case ex: BadRequestException =>
        logger.error(s"[retrieveCompanyDetails] for RegId: $registrationID - ${ex.responseCode} ${ex.message}")
        None
      case ex: NotFoundException =>
        logger.warn(s"[retrieveCompanyDetails] for RegId: $registrationID - ${ex.responseCode} ${ex.message}")
        None
      case ex: UpstreamErrorResponse =>
        logger.error(s"[retrieveCompanyDetails] for RegId: $registrationID - ${ex.statusCode} ${ex.message}")
        None
      case ex: Exception =>
        logger.error(s"[retrieveCompanyDetails] for RegId: $registrationID - Unexpected error occurred: $ex")
        None
    }
  }

  def retrieveTradingDetails(registrationID: String)(implicit hc: HeaderCarrier): Future[Option[TradingDetails]] = {
    wsHttp.GET[Option[TradingDetails]](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/trading-details") recover {
      case ex: BadRequestException =>
        logger.error(s"[retrieveTradingDetails] for RegId: $registrationID - ${ex.responseCode} ${ex.message}")
        None
      case ex: NotFoundException =>
        logger.warn(s"[retrieveTradingDetails] for RegId: $registrationID - ${ex.responseCode} ${ex.message}")
        None
      case ex: UpstreamErrorResponse =>
        logger.error(s"[retrieveTradingDetails] for RegId: $registrationID - ${ex.statusCode} ${ex.message}")
        None
      case ex: Exception =>
        logger.error(s"[retrieveTradingDetails] for RegId: $registrationID - Unexpected error occurred: $ex")
        None
    }
  }

  def updateTradingDetails(registrationID: String, tradingDetails: TradingDetails)(implicit hc: HeaderCarrier): Future[TradingDetailsResponse] = {
    wsHttp.PUT[JsValue, TradingDetails](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/trading-details", Json.toJson[TradingDetails](tradingDetails))
      .map {
        tradingDetailsResp =>
          TradingDetailsSuccessResponse(tradingDetailsResp)
      } recover {
      case ex: BadRequestException =>
        logger.error(s"[updateTradingDetails] for RegId: $registrationID - ${ex.responseCode} ${ex.message}")
        TradingDetailsNotFoundResponse
      case ex: NotFoundException =>
        logger.warn(s"[updateTradingDetails] for RegId: $registrationID - ${ex.responseCode} ${ex.message}")
        TradingDetailsNotFoundResponse
      case ex: UpstreamErrorResponse =>
        logger.error(s"[updateTradingDetails] for RegId: $registrationID - ${ex.statusCode} ${ex.message}")
        TradingDetailsNotFoundResponse
      case ex: Exception =>
        logger.error(s"[updateTradingDetails] for RegId: $registrationID - Unexpected error occurred: $ex")
        TradingDetailsNotFoundResponse
    }
  }

  def retrieveContactDetails(registrationID: String)(implicit hc: HeaderCarrier): Future[CompanyContactDetailsResponse] = {
    wsHttp.GET[CompanyContactDetails](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/contact-details") map {
      response => CompanyContactDetailsSuccessResponse(response)
    } recover {
      case e: BadRequestException =>
        logger.error(s"[retrieveContactDetails] Received a Bad Request status code when expecting contact details from Company-Registration")
        CompanyContactDetailsBadRequestResponse
      case e: NotFoundException =>
        logger.warn(s"[retrieveContactDetails] Received a Not Found status code when expecting contact details from Company-Registration")
        CompanyContactDetailsNotFoundResponse
      case e: ForbiddenException =>
        logger.error(s"[retrieveContactDetails] Received a Forbidden status code when expecting contact details from Company-Registration")
        CompanyContactDetailsForbiddenResponse
      case e: Exception =>
        logger.error(s"[retrieveContactDetails] Received an error when expecting contact details from Company-Registration - ${e.getMessage}")
        CompanyContactDetailsBadRequestResponse
    }
  }

  def updateContactDetails(registrationID: String, contactDetails: CompanyContactDetailsApi)(implicit hc: HeaderCarrier): Future[CompanyContactDetailsResponse] = {
    val json = Json.toJson(contactDetails)
    wsHttp.PUT[JsValue, CompanyContactDetails](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/contact-details", json) map {
      response => CompanyContactDetailsSuccessResponse(response)
    } recover {
      case e: BadRequestException =>
        logger.error(s"[updateContactDetails] Received a Bad Request status code when expecting contact details from Company-Registration")
        CompanyContactDetailsBadRequestResponse
      case e: NotFoundException =>
        logger.warn(s"[updateContactDetails] Received a Not Found status code when expecting contact details from Company-Registration")
        CompanyContactDetailsNotFoundResponse
      case e: ForbiddenException =>
        logger.error(s"[updateContactDetails] Received a Forbidden status code when expecting contact details from Company-Registration")
        CompanyContactDetailsForbiddenResponse
      case e: Exception =>
        logger.error(s"[updateContactDetails] Received an error when expecting contact details from Company-Registration - ${e.getMessage}")
        CompanyContactDetailsBadRequestResponse
    }
  }

  def retrieveAccountingDetails(registrationID: String)(implicit hc: HeaderCarrier): Future[AccountingDetailsResponse] = {
    wsHttp.GET[AccountingDetails](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/accounting-details") map {
      response => AccountingDetailsSuccessResponse(response)
    } recover {
      case e: BadRequestException =>
        logger.error(s"[retrieveAccountingDetails] Received a Bad Request status code when expecting accounting details from Company-Registration")
        AccountingDetailsBadRequestResponse
      case e: NotFoundException =>
        logger.warn(s"[retrieveAccountingDetails] Received a Not Found status code when expecting accounting details from Company-Registration")
        AccountingDetailsNotFoundResponse
      case e: Exception =>
        logger.error(s"[retrieveAccountingDetails] Received an error when expecting accounting details from Company-Registration - ${e.getMessage}")
        AccountingDetailsBadRequestResponse
    }
  }

  def updateAccountingDetails(registrationID: String, accountingDetails: AccountingDetailsRequest)(implicit hc: HeaderCarrier): Future[AccountingDetailsResponse] = {
    val json = Json.toJson(accountingDetails)
    wsHttp.PUT[JsValue, AccountingDetails](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/accounting-details", json) map {
      response => AccountingDetailsSuccessResponse(response)
    } recover {
      case e: BadRequestException =>
        logger.error(s"[updateAccountingDetails] Received a Bad Request status code when expecting accounting details from Company-Registration")
        AccountingDetailsBadRequestResponse
      case e: NotFoundException =>
        logger.warn(s"[updateAccountingDetails] Received a Not Found status code when expecting accounting details from Company-Registration")
        AccountingDetailsNotFoundResponse
      case e: Exception =>
        logger.error(s"[updateAccountingDetails] Received an error when expecting accounting details from Company-Registration - ${e.getMessage}")
        AccountingDetailsBadRequestResponse
    }
  }

  def fetchConfirmationReferences(registrationID: String)(implicit hc: HeaderCarrier): Future[ConfirmationReferencesResponse] = {
    wsHttp.GET[ConfirmationReferences](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/confirmation-references") map {
      res => ConfirmationReferencesSuccessResponse(res)
    } recover {
      case e: BadRequestException =>
        logger.error(s"[fetchConfirmationReferences] Received a Bad Request status code when expecting acknowledgement ref from Company-Registration")
        ConfirmationReferencesBadRequestResponse
      case e: NotFoundException =>
        logger.warn(s"[fetchConfirmationReferences] Received a Not Found status code when expecting acknowledgement ref from Company-Registration")
        ConfirmationReferencesNotFoundResponse
      case e: Exception =>
        logger.error(s"[fetchConfirmationReferences] Received an error when expecting acknowledgement ref from Company-Registration - ${e.getMessage}")
        ConfirmationReferencesErrorResponse
    }
  }

  def updateReferences(registrationID: String, payload: ConfirmationReferences)(implicit hc: HeaderCarrier): Future[ConfirmationReferencesResponse] = {
    wsHttp.PUT[JsValue, ConfirmationReferences](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/confirmation-references", Json.toJson(payload)) map {
      res => ConfirmationReferencesSuccessResponse(res)
    } recover {
      case ex: UpstreamErrorResponse =>
        if(UpstreamErrorResponse.Upstream4xxResponse.unapply(ex).isDefined) {
          logger.error(s"[updateReferences] Received a upstream 4xx code when expecting acknowledgement ref from Company-Registration")
          DESFailureDeskpro
        } else {
          logger.warn(s"[updateReferences] Received a upstream 5xx status code when expecting acknowledgement ref from Company-Registration")
          DESFailureRetriable
        }
      case e: Exception =>
        logger.error(s"[updateReferences] Received an unknown error when expecting acknowledgement ref from Company-Registration - ${e.getMessage}")
        DESFailureDeskpro
    }
  }

  def fetchHeldSubmissionTime(registrationId: String)(implicit hc: HeaderCarrier): Future[Option[JsValue]] = {
    wsHttp.GET[JsValue](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationId/fetch-held-time") map {
      res => Some(res)
    } recover {
      case ex: BadRequestException =>
        logger.error(s"[fetchHeldTime] for RegId: $registrationId - ${ex.responseCode} ${ex.message}")
        None
      case ex: NotFoundException =>
        logger.warn(s"[fetchHeldTime] for RegId: $registrationId - ${ex.responseCode} ${ex.message}")
        None
      case ex: UpstreamErrorResponse =>
        logger.error(s"[fetchHeldTime] for RegId: $registrationId - ${ex.statusCode} ${ex.message}")
        None
      case ex: Exception =>
        logger.error(s"[fetchHeldTime] for RegId: $registrationId - Unexpected error occurred: $ex")
        None
    }
  }

  def getGroups(registrationId:String)(implicit hc:HeaderCarrier): Future[Option[Groups]] = {
    wsHttp.GET[HttpResponse](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationId/groups").map {
      res => if (res.status == 200) {
        res.json.validate[Groups].fold[Option[Groups]](errors => {
          logger.error(s"[getGroups] could not parse groups json to Groups $registrationId for keys ${errors.map(_._1)}")
          Option.empty[Groups]
        }, successG => Some(successG))
      } else {
        None
      }
    }
  }


  def updateGroups(registrationId:String, groups: Groups)(implicit hc:HeaderCarrier): Future[Groups] = {
    wsHttp.PUT[Groups, HttpResponse](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationId/groups", groups).map {
          res => res.json.validate[Groups].fold[Groups](errors => {
        logger.error(s"[getGroups] could not parse groups json to Groups $registrationId for keys ${errors.map(_._1)}")
        throw new Exception("Update returned invalid json")
      }, identity)
    }
  }

  def deleteGroups(registrationId:String)(implicit hc:HeaderCarrier): Future[Boolean] = {
    wsHttp.DELETE[HttpResponse](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationId/groups").map{
      _ => true
    }.recoverWith {
      case e =>
        logger.error(s"Delete of groups block failed for $registrationId ${e.getMessage}")
        Future.failed(e)
    }
  }

  def shareholderListValidationEndpoint(listOfShareholders: List[String])(implicit hc:HeaderCarrier): Future[List[String]] = {
    wsHttp.POST[List[String], HttpResponse](s"$companyRegUrl/company-registration/corporation-tax-registration/check-list-of-group-names", listOfShareholders).map { res =>
      if(res.status == 200) {
        res.json.validate[List[String]].getOrElse {
          logger.error(s"[shareholderListValidationEndpoint] returned 200 but the list was unparsable, returning empty list to the user, sessionId: ${hc.sessionId}")
          List.empty[String]
        }
      } else {
        logger.error(s"[shareholderListValidationEndpoint] returned 204 because NONE of the names returned from TxApi pass the des validation, empty list returned, sessionId: ${hc.sessionId}")
        List.empty[String]
      }
    }.recover{
      case e =>
        logger.error(s"[shareholderListValidationEndpoint] Something went wrong when calling CR, returning empty list to user: ${hc.sessionId}, ${e.getMessage}")
        List.empty[String]
    }
  }

  def retrieveEmail(registrationId: String)(implicit hc: HeaderCarrier): Future[Option[Email]] = {
    wsHttp.GET[Email](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationId/retrieve-email").map {
      e => Some(e)
    } recover {
      case e: NotFoundException =>
        None
      case ex: BadRequestException =>
        logger.warn(s"[retrieveEmail] Could not find a CT document for rid - $registrationId")
        None
    }
  }

  def updateEmail(registrationId: String, email: Email)(implicit hc: HeaderCarrier): Future[Option[Email]] = {
    val json = Json.toJson(email)
    wsHttp.PUT[JsValue, Email](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationId/update-email", json).map {
      e => Some(e)
    } recover {
      case ex: BadRequestException =>
        logger.warn(s"[updateEmail] Could not find a CT document for rid - $registrationId")
        None
    }
  }

  def verifyEmail(registrationId: String, email: Email)(implicit hc: HeaderCarrier): Future[JsValue] = {
    val json = Json.toJson(email)
    wsHttp.PUT[JsValue, JsValue](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationId/update-email", json)
      .recover {
        case ex: Exception => Json.toJson(ex.getMessage)
      }
  }

  def updateRegistrationProgress(registrationID: String, progress: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val json = Json.obj("registration-progress" -> progress)
    wsHttp.PUT[JsValue, HttpResponse](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/progress", json)
  }

  def deleteRegistrationSubmission(registrationId: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    wsHttp.DELETE[HttpResponse](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationId/delete-submission")
  }


  def saveTXIDAfterHO2(registrationID: String, txid: String)(implicit hc: HeaderCarrier): Future[Option[HttpResponse]] = {
    val json = Json.obj("transaction_id" -> txid)
    wsHttp.PUT[JsValue, HttpResponse](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/handOff2Reference-ackRef-save", json).map  {
      res => Some(res)} recover {
      case ex: BadRequestException =>
        logger.error(s"[saveTXIDAfterHO2] for RegId: $registrationID - ${ex.responseCode} ${ex.message}")
        None
      case ex: NotFoundException =>
        logger.warn(s"[saveTXIDAfterHO2] for RegId: $registrationID - ${ex.responseCode} ${ex.message}")
        None
      case ex: UpstreamErrorResponse =>
        logger.error(s"[saveTXIDAfterHO2] for RegId: $registrationID - ${ex.statusCode} ${ex.message}")
        None
      case ex: Exception =>
        logger.error(s"[saveTXIDAfterHO2] for RegId: $registrationID - Unexpected error occurred: $ex")
        None
    }
  }

}