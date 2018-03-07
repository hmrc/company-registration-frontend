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

package connectors

import config.WSHttp
import models._
import play.api.Logger
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future

object BusinessRegistrationConnector extends BusinessRegistrationConnector with ServicesConfig {
  val businessRegUrl = baseUrl("business-registration")
  val http : CoreGet with CorePost = WSHttp
}

sealed trait BusinessRegistrationResponse
case class BusinessRegistrationSuccessResponse(response: BusinessRegistration) extends BusinessRegistrationResponse
case object BusinessRegistrationNotFoundResponse extends BusinessRegistrationResponse
case object BusinessRegistrationForbiddenResponse extends BusinessRegistrationResponse
case class BusinessRegistrationErrorResponse(err: Exception) extends BusinessRegistrationResponse

trait BusinessRegistrationConnector {

  val businessRegUrl: String
  val http: CoreGet with CorePost

  def createMetadataEntry(implicit hc: HeaderCarrier): Future[BusinessRegistration] = {
    val json = Json.toJson[BusinessRegistrationRequest](BusinessRegistrationRequest("en"))
    http.POST[JsValue, BusinessRegistration](s"$businessRegUrl/business-registration/business-tax-registration", json)
  }

  def retrieveAndUpdateCompletionCapacity(registrationID : String, completionCapacity : String)(implicit hc : HeaderCarrier) : Future[BusinessRegistration] = {
    retrieveMetadata(registrationID) flatMap {
      case BusinessRegistrationSuccessResponse(resp) =>
        http.POST[JsValue, BusinessRegistration](s"$businessRegUrl/business-registration/business-tax-registration/update/$registrationID",
          Json.toJson[BusinessRegistration](resp.copy(completionCapacity = Some(completionCapacity))))
      case unknown => {
        Logger.warn(s"[BusinessRegistrationConnector][retrieveAndUpdateCompletionCapacity] Unexpected result, unable to get BR doc : ${unknown}")
        throw new RuntimeException("Missing BR document for signed in user")
      }
    }
  }

  def retrieveMetadata(regId: String)(implicit hc: HeaderCarrier, rds: HttpReads[BusinessRegistration]): Future[BusinessRegistrationResponse] = {
    http.GET[BusinessRegistration](s"$businessRegUrl/business-registration/business-tax-registration/$regId") map {
      metaData =>
        BusinessRegistrationSuccessResponse(metaData)
    } recover {
      case e: NotFoundException =>
        Logger.info(s"[BusinessRegistrationConnector] [retrieveMetadata] - Received a NotFound status code when expecting metadata from Business-Registration")
        BusinessRegistrationNotFoundResponse
      case e: ForbiddenException =>
        Logger.error(s"[BusinessRegistrationConnector] [retrieveMetadata] - Received a Forbidden status code when expecting metadata from Business-Registration")
        BusinessRegistrationForbiddenResponse
      case e: Exception =>
        Logger.error(s"[BusinessRegistrationConnector] [retrieveMetadata] - Received error when expecting metadata from Business-Registration - Error ${e.getMessage}")
        BusinessRegistrationErrorResponse(e)
    }
  }

  def retrieveMetadata(implicit hc: HeaderCarrier, rds: HttpReads[BusinessRegistration]): Future[BusinessRegistrationResponse] = {
    http.GET[BusinessRegistration](s"$businessRegUrl/business-registration/business-tax-registration") map {
      metaData =>
        BusinessRegistrationSuccessResponse(metaData)
    } recover {
      case e: NotFoundException =>
        Logger.info(s"[BusinessRegistrationConnector] [retrieveMetadata] - Received a NotFound status code when expecting metadata from Business-Registration")
        BusinessRegistrationNotFoundResponse
      case e: ForbiddenException =>
        Logger.error(s"[BusinessRegistrationConnector] [retrieveMetadata] - Received a Forbidden status code when expecting metadata from Business-Registration")
        BusinessRegistrationForbiddenResponse
      case e: Exception =>
        Logger.error(s"[BusinessRegistrationConnector] [retrieveMetadata] - Received error when expecting metadata from Business-Registration - Error ${e.getMessage}")
        BusinessRegistrationErrorResponse(e)
    }
  }

  def updatePrePopContactDetails(registrationId: String, contactDetails: CompanyContactDetailsMongo)(implicit hc: HeaderCarrier): Future[Boolean] = {
    val url = s"$businessRegUrl/business-registration/$registrationId/contact-details"
    val json = Json.toJson(contactDetails)(CompanyContactDetailsMongo.prePopWrites)

    http.POST(url, json) map (_ => true) recover handlePrePopError("updatePrePopContactDetails")
  }

  def fetchPrePopContactDetails(registrationId: String)(implicit hc: HeaderCarrier): Future[JsValue] = {
    val url = s"$businessRegUrl/business-registration/$registrationId/contact-details"
    http.GET(url) map (_.json)
  }

  def updatePrePopAddress(registrationId: String, address: Address)(implicit hc: HeaderCarrier): Future[Boolean] = {
    val url = s"$businessRegUrl/business-registration/$registrationId/addresses"
    val json = Json.toJson(address)(Address.prePopWrites)

    http.POST(url, json) map (_ => true) recover handlePrePopError("updatePrePopAddress")
  }

  def fetchPrePopAddress(registrationId: String)(implicit hc: HeaderCarrier): Future[JsValue] = {
    val url = s"$businessRegUrl/business-registration/$registrationId/addresses"
    http.GET(url) map (_.json)
  }

  private def handlePrePopError(funcName: String): PartialFunction[Throwable, Boolean] = {
    case ex: HttpException =>
      Logger.error(s"[BusinessRegistrationConnector] [$funcName] http status code ${ex.responseCode} returned for reason ${ex.message}", ex)
      false
    case ex: Exception =>
      Logger.error(s"[BusinessRegistrationConnector] [$funcName] unknown exception caught : ${ex.getMessage}", ex)
      false
  }
}
