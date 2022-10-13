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

import config.{AppConfig, WSHttp}
import javax.inject.Inject
import models._
import utils.Logging
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class BusinessRegistrationConnectorImpl @Inject()(val wsHttp: WSHttp, val appConfig: AppConfig) extends BusinessRegistrationConnector {
  lazy val businessRegUrl = appConfig.servicesConfig.baseUrl("business-registration")
}

sealed trait BusinessRegistrationResponse

case class BusinessRegistrationSuccessResponse(response: BusinessRegistration) extends BusinessRegistrationResponse

case object BusinessRegistrationNotFoundResponse extends BusinessRegistrationResponse

case object BusinessRegistrationForbiddenResponse extends BusinessRegistrationResponse

case class BusinessRegistrationErrorResponse(err: Exception) extends BusinessRegistrationResponse

trait BusinessRegistrationConnector extends Logging {

  val businessRegUrl: String
  val wsHttp: CoreGet with CorePost

  def createMetadataEntry(implicit hc: HeaderCarrier): Future[BusinessRegistration] = {
    val json = Json.toJson[BusinessRegistrationRequest](BusinessRegistrationRequest("en"))
    wsHttp.POST[JsValue, BusinessRegistration](s"$businessRegUrl/business-registration/business-tax-registration", json)
  }

  def retrieveAndUpdateCompletionCapacity(registrationID: String, completionCapacity: String)(implicit hc: HeaderCarrier): Future[BusinessRegistration] = {
    retrieveMetadata(registrationID) flatMap {
      case BusinessRegistrationSuccessResponse(resp) =>
        wsHttp.POST[JsValue, BusinessRegistration](s"$businessRegUrl/business-registration/business-tax-registration/update/$registrationID",
          Json.toJson[BusinessRegistration](resp.copy(completionCapacity = Some(completionCapacity))))
      case unknown => {
        logger.warn(s"[retrieveAndUpdateCompletionCapacity] Unexpected result, unable to get BR doc : ${unknown}")
        throw new RuntimeException("Missing BR document for signed in user")
      }
    }
  }

  def retrieveMetadata(regId: String)(implicit hc: HeaderCarrier, rds: HttpReads[BusinessRegistration]): Future[BusinessRegistrationResponse] = {
    wsHttp.GET[BusinessRegistration](s"$businessRegUrl/business-registration/business-tax-registration/$regId") map {
      metaData =>
        BusinessRegistrationSuccessResponse(metaData)
    } recover {
      case e: NotFoundException =>
        logger.warn(s"[retrieveMetadata] Received a NotFound status code when expecting metadata from Business-Registration")
        BusinessRegistrationNotFoundResponse
      case e: ForbiddenException =>
        logger.error(s"[retrieveMetadata] Received a Forbidden status code when expecting metadata from Business-Registration")
        BusinessRegistrationForbiddenResponse
      case e: Exception =>
        logger.error(s"[retrieveMetadata] Received error when expecting metadata from Business-Registration - Error ${e.getMessage}")
        BusinessRegistrationErrorResponse(e)
    }
  }

  def retrieveMetadata(implicit hc: HeaderCarrier, rds: HttpReads[BusinessRegistration]): Future[BusinessRegistrationResponse] = {
    wsHttp.GET[BusinessRegistration](s"$businessRegUrl/business-registration/business-tax-registration") map {
      metaData =>
        BusinessRegistrationSuccessResponse(metaData)
    } recover {
      case e: NotFoundException =>
        logger.warn(s"[retrieveMetadata] Received a NotFound status code when expecting metadata from Business-Registration")
        BusinessRegistrationNotFoundResponse
      case e: ForbiddenException =>
        logger.error(s"[retrieveMetadata] Received a Forbidden status code when expecting metadata from Business-Registration")
        BusinessRegistrationForbiddenResponse
      case e: Exception =>
        logger.error(s"[retrieveMetadata] Received error when expecting metadata from Business-Registration - Error ${e.getMessage}")
        BusinessRegistrationErrorResponse(e)
    }
  }

  def updatePrePopContactDetails(registrationId: String, contactDetails: CompanyContactDetailsApi)(implicit hc: HeaderCarrier): Future[Boolean] = {
    val url = s"$businessRegUrl/business-registration/$registrationId/contact-details"
    val json = Json.toJson(contactDetails)(CompanyContactDetailsApi.prePopWrites)

    wsHttp.POST(url, json) map (_ => true) recover handlePrePopError("updatePrePopContactDetails")
  }

  def fetchPrePopContactDetails(registrationId: String)(implicit hc: HeaderCarrier): Future[JsValue] = {
    val url = s"$businessRegUrl/business-registration/$registrationId/contact-details"
    wsHttp.GET(url) map (_.json)
  }

  def updatePrePopAddress(registrationId: String, address: Address)(implicit hc: HeaderCarrier): Future[Boolean] = {
    val url = s"$businessRegUrl/business-registration/$registrationId/addresses"
    val json = Json.toJson(address)(Address.prePopWrites)

    wsHttp.POST(url, json) map (_ => true) recover handlePrePopError("updatePrePopAddress")
  }

  def updatePrePopAddress(registrationId: String, address: NewAddress)(implicit hc: HeaderCarrier): Future[Boolean] = {
    val url = s"$businessRegUrl/business-registration/$registrationId/addresses"
    val json = Json.toJson(address)(NewAddress.prePopWrites)

    wsHttp.POST(url, json) map (_ => true) recover handlePrePopError("updatePrePopAddress")
  }

  def fetchPrePopAddress(registrationId: String)(implicit hc: HeaderCarrier): Future[JsValue] = {
    val url = s"$businessRegUrl/business-registration/$registrationId/addresses"
    wsHttp.GET(url) map (_.json)
  }

  private def handlePrePopError(funcName: String): PartialFunction[Throwable, Boolean] = {
    case ex: HttpException =>
      logger.error(s"[$funcName] http status code ${ex.responseCode} returned for reason ${ex.message}", ex)
      false
    case ex: Exception =>
      logger.error(s"[$funcName] unknown exception caught : ${ex.getMessage}", ex)
      false
  }
}