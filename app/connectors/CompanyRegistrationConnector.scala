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

package connectors

import connectors.httpParsers.CompanyRegistrationHttpParsers._
import config.AppConfig
import models._
import models.connectors.ConfirmationReferences
import play.api.http.Status.{BAD_REQUEST, OK}
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import utils.Logging

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class CompanyRegistrationConnectorImpl @Inject()(val appConfig: AppConfig,
                                                 val httpClientV2: HttpClientV2) extends CompanyRegistrationConnector {
  lazy val companyRegUrl = appConfig.servicesConfig.baseUrl("company-registration")
}

sealed trait FootprintResponse
case class FootprintFound(throttleResponse: ThrottleResponse) extends FootprintResponse
case object FootprintForbiddenResponse extends FootprintResponse
case object FootprintTooManyRequestsResponse extends FootprintResponse
case class FootprintErrorResponse(err: Exception) extends FootprintResponse

trait CompanyRegistrationConnector extends Logging {

  val companyRegUrl: String
  val httpClientV2: HttpClientV2

  def fetchRegistrationStatus(regId: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] = {
    retrieveCorporationTaxRegistration(regId) map {
      json => (json \ "status").asOpt[String]
    } recover {
      case _: NotFoundException => None
    }
  }

  def fetchCompanyName(regId: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[String] = {
    retrieveCorporationTaxRegistration(regId).map {
      json => (json \ "companyDetails" \ "companyName").validate[String].getOrElse(throw new Exception(s"Missing company Name for regId $regId"))
    }
  }

  def retrieveCorporationTaxRegistration(registrationID: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[JsValue] = {
    httpClientV2
      .get(url"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/corporation-tax-registration")
      .execute[JsValue]
      .recover {
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

  def retrieveOrCreateFootprint()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[FootprintResponse] = {
    httpClientV2
      .get(url"$companyRegUrl/company-registration/throttle/check-user-access")
      .execute[ThrottleResponse]
      .map {
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

  def retrieveCorporationTaxRegistrationDetails(registrationID: String)(implicit hc: HeaderCarrier,
                                                ec: ExecutionContext,
                                                rds: HttpReads[CorporationTaxRegistrationResponse]): Future[Option[CorporationTaxRegistrationResponse]] = {
    httpClientV2.get(url"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID")
    .execute[Option[CorporationTaxRegistrationResponse]]
  }

  def retrieveCompanyDetails(registrationID: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[CompanyDetails]] = {
    // wsHttp.GET[Option[CompanyDetails]](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/company-details")(companyRegistrationDetailsHttpReads(registrationID), hc, ec)
    httpClientV2
      .get(url"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/company-details")
      .execute[Option[CompanyDetails]](companyRegistrationDetailsHttpReads(registrationID), ec)
  }

  def retrieveTradingDetails(registrationID: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[TradingDetails]] = {
    // wsHttp.GET[Option[TradingDetails]](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/trading-details")(tradingDetailsHttpReads(registrationID), hc, ec)
    httpClientV2
      .get(url"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/trading-details")
      .execute[Option[TradingDetails]](tradingDetailsHttpReads(registrationID), ec)
  }

  def retrieveContactDetails(registrationID: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[CompanyContactDetailsResponse] = {
    // wsHttp.GET[CompanyContactDetailsResponse](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/contact-details")(contactDetailsHttpReads(registrationID), hc,ec)
    httpClientV2
      .get(url"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/contact-details")
      .execute[CompanyContactDetailsResponse](contactDetailsHttpReads(registrationID), ec)
  }

  def retrieveAccountingDetails(registrationID: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[AccountingDetailsResponse] = {
    // wsHttp.GET[AccountingDetailsResponse](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/accounting-details")(accountingDetailsHttpReads(registrationID), hc, ec)
    httpClientV2
      .get(url"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/accounting-details")
      .execute[AccountingDetailsResponse](accountingDetailsHttpReads(registrationID), ec)
  }

  def fetchConfirmationReferences(registrationID: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[ConfirmationReferencesResponse] = {
    httpClientV2.get(url"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/confirmation-references")
      .execute[ConfirmationReferences]
      .map {
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

  def fetchHeldSubmissionTime(registrationId: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[JsValue]] = {
    httpClientV2.get(url"$companyRegUrl/company-registration/corporation-tax-registration/$registrationId/fetch-held-time")
      .execute[JsValue]
      .map {
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

  def getGroups(registrationId:String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Groups]] = {
    httpClientV2
      .get(url"$companyRegUrl/company-registration/corporation-tax-registration/$registrationId/groups")
      .execute[HttpResponse]
      .map {
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

  def createCorporationTaxRegistrationDetails(regId: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[CorporationTaxRegistrationResponse] = {
    val json = Json.toJson[CorporationTaxRegistrationRequest](CorporationTaxRegistrationRequest("en"))
    httpClientV2.put(url"$companyRegUrl/company-registration/corporation-tax-registration/$regId")
      .withBody(json)
      .execute(createCorporationTaxRegistrationDetailsHttpReads(regId), ec)
  }

  def checkROValidPPOB(registrationID: String, ro: CHROAddress)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[NewAddress]] = {
    httpClientV2.post(url"$companyRegUrl/company-registration/corporation-tax-registration/check-ro-address")
      .withBody(Json.toJson(ro))
      .execute(rawReads, ec)
      .map { result =>
        result.status match {
          case OK => result.json.asOpt[NewAddress](NewAddress.verifyRoToPPOB)
          case BAD_REQUEST => None
        }
      } recover {
        case _: BadRequestException =>
          None
        case ex: Exception =>
          logger.error(s"[checkROAddress] for RegId: $registrationID")
          throw ex
      }
//    wsHttp.POST[JsValue, HttpResponse](s"$companyRegUrl/company-registration/corporation-tax-registration/check-ro-address", Json.toJson(ro)
//    )(implicitly, rawReads, hc, ec) map { result =>
//      result.status match {
//        case OK => result.json.asOpt[NewAddress](NewAddress.verifyRoToPPOB)
//        case BAD_REQUEST => None
//      }
//    } recover {
//      case _: BadRequestException =>
//        None
//      case ex: Exception =>
//        logger.error(s"[checkROAddress] for RegId: $registrationID")
//        throw ex
//    }
  }

  def validateRegisteredOfficeAddress(registrationID: String, ro: CHROAddress)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[NewAddress]] = {
    httpClientV2
      .post(url"$companyRegUrl/company-registration/corporation-tax-registration/check-return-business-address")
      .withBody(Json.toJson(ro))
      .execute(validateRegisteredOfficeAddressHttpReads(registrationID), ec)
  }

  def updateCompanyDetails(registrationID: String, companyDetails: CompanyDetails)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[CompanyDetails] = {
    httpClientV2
      .put(url"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/company-details")
      .withBody(Json.toJson[CompanyDetails](companyDetails))
      .execute(updateCompanyDetailsHttpReads(registrationID), ec)
  }

  def updateTradingDetails(registrationID: String, tradingDetails: TradingDetails)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[TradingDetailsResponse] = {
    httpClientV2
      .put(url"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/trading-details")
      .withBody(Json.toJson[TradingDetails](tradingDetails))
      .execute(updateTradingDetailsHttpReads(registrationID), ec)
      .map {
        tradingDetailsResp =>
          TradingDetailsSuccessResponse(tradingDetailsResp)
      }
  }

  def updateContactDetails(registrationID: String, contactDetails: CompanyContactDetailsApi)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[CompanyContactDetailsResponse] = {
    httpClientV2
      .put(url"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/contact-details")
      .withBody(Json.toJson(contactDetails))
      .execute(updateContactDetailsHttpReads(registrationID), ec)
      .map {
        response => CompanyContactDetailsSuccessResponse(response)
      }
  }

  def updateAccountingDetails(registrationID: String, accountingDetails: AccountingDetailsRequest)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[AccountingDetailsResponse] = {
    httpClientV2
      .put(url"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/accounting-details")
      .withBody(Json.toJson(accountingDetails))
      .execute[AccountingDetails]
      .map {
        response => AccountingDetailsSuccessResponse(response)
      }
  }

  def updateReferences(registrationID: String, payload: ConfirmationReferences)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[ConfirmationReferencesResponse] = {
    httpClientV2
      .put(url"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/confirmation-references")
      .withBody(Json.toJson(payload))
      .execute[ConfirmationReferences]
      .map {
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

  def updateGroups(registrationId:String, groups: Groups)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Groups] = {
    httpClientV2
      .put(url"$companyRegUrl/company-registration/corporation-tax-registration/$registrationId/groups")
      .withBody(Json.toJson(groups)) // [Groups, HttpResponse]
      .execute[HttpResponse]
      .map {
          res => res.json.validate[Groups].fold[Groups](errors => {
            logger.error(s"[getGroups] could not parse groups json to Groups $registrationId for keys ${errors.map(_._1)}")
            throw new Exception("Update returned invalid json")
          }, identity)
        }
  }

  def deleteGroups(registrationId:String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] = {
//    wsHttp.DELETE[HttpResponse](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationId/groups")(readRaw, hc, ec).map{
//      _ => true
//    }.recoverWith {
//      case e =>
//        logger.error(s"Delete of groups block failed for $registrationId ${e.getMessage}")
//        Future.failed(e)
//    }
    httpClientV2
      .delete(url"$companyRegUrl/company-registration/corporation-tax-registration/$registrationId/groups")
      .execute(readRaw, ec).map{
        _ => true
      }.recoverWith {
        case e =>
          logger.error(s"Delete of groups block failed for $registrationId ${e.getMessage}")
          Future.failed(e)
      }
  }

  def shareholderListValidationEndpoint(listOfShareholders: List[String])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[List[String]] = {
    httpClientV2.post(url"$companyRegUrl/company-registration/corporation-tax-registration/check-list-of-group-names")
      .withBody(Json.toJson(listOfShareholders))
      .execute[HttpResponse] // [List[String], HttpResponse]
      .map { res =>
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

  def retrieveEmail(registrationId: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Email]] = {
    // wsHttp.GET[Option[Email]](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationId/retrieve-email")(retrieveEmailHttpReads(registrationId), hc, ec)
    httpClientV2
      .get(url"$companyRegUrl/company-registration/corporation-tax-registration/$registrationId/retrieve-email")
      .execute(retrieveEmailHttpReads(registrationId), ec)
  }

  def updateEmail(registrationId: String, email: Email)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Email]] = {
//    val json = Json.toJson(email)
//    wsHttp.PUT[JsValue, Email](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationId/update-email", json).map {
//      e => Some(e)
//    } recover {
//      case ex: BadRequestException =>
//        logger.warn(s"[updateEmail] Could not find a CT document for rid - $registrationId")
//        None
//    }
    httpClientV2
      .put(url"$companyRegUrl/company-registration/corporation-tax-registration/$registrationId/update-email")
      .withBody(Json.toJson(email))
      .execute[Email]
      .map {
        e => Some(e)
      } recover {
        case ex: BadRequestException =>
          logger.warn(s"[updateEmail] Could not find a CT document for rid - $registrationId")
          None
      }
  }

  def verifyEmail(registrationId: String, email: Email)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[JsValue] = {
    httpClientV2
      .put(url"$companyRegUrl/company-registration/corporation-tax-registration/$registrationId/update-email")
      .withBody(Json.toJson(email))
      .execute(readJsValue, ec)
      .recover {
        case ex: Exception => Json.toJson(ex.getMessage)
      }
  }

  def updateRegistrationProgress(registrationID: String, progress: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {
//    val json = Json.obj("registration-progress" -> progress)
//    wsHttp.PUT[JsValue, HttpResponse](s"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/progress", json)(implicitly, readRaw, hc, ec)
    httpClientV2
      .put(url"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/progress")
      .withBody(Json.obj("registration-progress" -> progress))
      .execute
  }

  def deleteRegistrationSubmission(registrationId: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {
    httpClientV2
      .delete(url"$companyRegUrl/company-registration/corporation-tax-registration/$registrationId/delete-submission")
      .execute(readRaw, ec)
  }


  def saveTXIDAfterHO2(registrationID: String, txid: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[HttpResponse]] = {
    httpClientV2
      .put(url"$companyRegUrl/company-registration/corporation-tax-registration/$registrationID/handOff2Reference-ackRef-save")
      .withBody(Json.obj("transaction_id" -> txid))
      .execute(readRaw, ec)
      .map  {
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

  def updateLanguage(registrationId: String, language: Language)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] = {
    // [Language, Boolean]
    httpClientV2.put(url"$companyRegUrl/company-registration/corporation-tax-registration/$registrationId/language")
      .withBody(Json.toJson(language))
    .execute(
      updateLanguageHttpReads(registrationId, language), ec
    ) recover {
      case ex: Exception =>
        logger.error(s"[updateLanguage] An unexpected Exception of type '${ex.getClass.getSimpleName}' occurred when trying to update language to: '${language.code}' for regId: '$registrationId'")
        false
    }
  }

}