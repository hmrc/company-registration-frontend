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

import uk.gov.hmrc.http.HttpReads.Implicits._
import config.AppConfig

import javax.inject.Inject
import models._
import utils.Logging
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2

import scala.concurrent.{ExecutionContext, Future}

class BusinessRegistrationConnectorImpl @Inject()(val httpClientV2: HttpClientV2, val appConfig: AppConfig)(implicit val ec: ExecutionContext) extends BusinessRegistrationConnector {
  lazy val businessRegUrl = appConfig.servicesConfig.baseUrl("business-registration")
}

sealed trait BusinessRegistrationResponse

case class BusinessRegistrationSuccessResponse(response: BusinessRegistration) extends BusinessRegistrationResponse

case object BusinessRegistrationNotFoundResponse extends BusinessRegistrationResponse

case object BusinessRegistrationForbiddenResponse extends BusinessRegistrationResponse

case class BusinessRegistrationErrorResponse(err: Exception) extends BusinessRegistrationResponse

trait BusinessRegistrationConnector extends Logging {
  implicit val ec: ExecutionContext

  val businessRegUrl: String
  val httpClientV2: HttpClientV2

  def createMetadataEntry(implicit hc: HeaderCarrier): Future[BusinessRegistration] = {
    val json = Json.toJson[BusinessRegistrationRequest](BusinessRegistrationRequest("en"))
    httpClientV2.post(url"$businessRegUrl/business-registration/business-tax-registration")
      .withBody(json)
      .execute[BusinessRegistration]
  }

  def retrieveAndUpdateCompletionCapacity(registrationID: String, completionCapacity: String)(implicit hc: HeaderCarrier): Future[BusinessRegistration] = {
    retrieveMetadata(registrationID) flatMap {
      case BusinessRegistrationSuccessResponse(resp) =>
        httpClientV2.post(url"$businessRegUrl/business-registration/business-tax-registration/update/$registrationID")
          .withBody(Json.toJson[BusinessRegistration](resp.copy(completionCapacity = Some(completionCapacity))))
          .execute[BusinessRegistration]
      case unknown => {
        logger.warn(s"[retrieveAndUpdateCompletionCapacity] Unexpected result, unable to get BR doc : ${unknown}")
        throw new RuntimeException("Missing BR document for signed in user")
      }
    }
  }

  def retrieveMetadata(regId: String)(implicit hc: HeaderCarrier, rds: HttpReads[BusinessRegistration]): Future[BusinessRegistrationResponse] = {
    httpClientV2.get(url"$businessRegUrl/business-registration/business-tax-registration/$regId")
      .execute[BusinessRegistration]
      .map {
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

  def retrieveMetadata(implicit hc: HeaderCarrier): Future[BusinessRegistrationResponse] = {
    httpClientV2.get(url"$businessRegUrl/business-registration/business-tax-registration")
      .execute[BusinessRegistration]
      .map {
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
    httpClientV2.post(url"$businessRegUrl/business-registration/$registrationId/contact-details")
      .withBody(Json.toJson(contactDetails)(CompanyContactDetailsApi.prePopWrites))
      .execute[HttpResponse]
      .map(_ => true) recover handlePrePopError("updatePrePopContactDetails")
  }

  def fetchPrePopContactDetails(registrationId: String)(implicit hc: HeaderCarrier): Future[JsValue] = {
    val url = url"$businessRegUrl/business-registration/$registrationId/contact-details"
    httpClientV2.get(url).execute[HttpResponse].map(_.json)
  }

  def updatePrePopAddress(registrationId: String, address: Address)(implicit hc: HeaderCarrier): Future[Boolean] = {
    httpClientV2.post(url"$businessRegUrl/business-registration/$registrationId/addresses")
      .withBody(Json.toJson(address)(Address.prePopWrites))
      .execute[HttpResponse].map (_ => true) recover handlePrePopError("updatePrePopAddress")
  }

  def updatePrePopAddress(registrationId: String, address: NewAddress)(implicit hc: HeaderCarrier): Future[Boolean] = {
    httpClientV2.post(url"$businessRegUrl/business-registration/$registrationId/addresses")
      .withBody(Json.toJson(address))
      .execute[HttpResponse]
      .map (_ => true) recover handlePrePopError("updatePrePopAddress")
  }

  def fetchPrePopAddress(registrationId: String)(implicit hc: HeaderCarrier): Future[JsValue] = {
    httpClientV2.get(url"$businessRegUrl/business-registration/$registrationId/addresses")
      .execute[HttpResponse]
      .map (_.json)
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