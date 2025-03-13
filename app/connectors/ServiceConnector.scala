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

import config.AppConfig

import javax.inject.Inject
import models.external.OtherRegStatus
import utils.Logging
import play.api.http.Status._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2

import scala.concurrent.{ExecutionContext, Future}

class PAYEConnectorImpl @Inject()(val appConfig: AppConfig, val httpClientV2: HttpClientV2) extends PAYEConnector

trait PAYEConnector extends ServiceConnector {
  val appConfig: AppConfig
  lazy val serviceBaseUrl = appConfig.servicesConfig.baseUrl("paye-registration")
  lazy val serviceUri = appConfig.servicesConfig.getConfString("paye-registration.uri", "/paye-registration")
}

class VATConnectorImpl @Inject()(val appConfig: AppConfig, val httpClientV2: HttpClientV2)(implicit val ec: ExecutionContext) extends VATConnector

trait VATConnector extends ServiceConnector {
  val appConfig: AppConfig
  lazy val serviceBaseUrl = appConfig.servicesConfig.baseUrl("vat-registration")
  lazy val serviceUri = appConfig.servicesConfig.getConfString("vat-registration.uri", "/vatreg")
}

trait ServiceConnector extends Logging {
  val httpClientV2: HttpClientV2
  val serviceBaseUrl: String
  val serviceUri: String

  def getStatus(regId: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[StatusResponse] = {
    val url = url"$serviceBaseUrl$serviceUri/$regId/status"
    httpClientV2.get(url)
      .execute[OtherRegStatus]
      .map {
        SuccessfulResponse
      } recover {
        case ex: NotFoundException => NotStarted
        case ex: HttpException =>
          logger.error(s"[getStatus] ${ex.responseCode} response code was returned - reason : ${ex.message}", ex)
          ErrorResponse
        case ex: Throwable =>
          logger.error(s"[getStatus] Non-Http Exception caught", ex)
          ErrorResponse
      }
  }

  def canStatusBeCancelled(regId: String)(f: String => Future[StatusResponse])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[String] = {
    f(regId).map {
      case a: SuccessfulResponse => a.status.cancelURL.getOrElse(throw cantCancel)
      case _ => throw cantCancel
    }
  }

  def cancelReg(regID: String)(f: String => Future[StatusResponse])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[CancellationResponse] = {
    f(regID).flatMap {
      case a: SuccessfulResponse =>
        val url = a.status.cancelURL.getOrElse(throw cantCancel)
        httpClientV2
          .delete(url"$url")
          .execute[HttpResponse]
          .map { resp =>
          if (resp.status == OK) {
            Cancelled
          } else {
            NotCancelled
          }
        }
      case _ => throw cantCancel
    } recover {
      case ex: HttpException => logger.error(s"[cancelReg] ${ex.responseCode} response code was returned - reason : ${ex.message}  ", ex)
        NotCancelled
      case ex: cantCancelT => logger.error(s"[cancelReg] $ex functionPassed in to return regId succeeded but didn't return a SuccessfulResponse (getStatus)")
        NotCancelled
      case ex: Throwable => logger.error(s"[cancelReg] Non-Http Exception caught", ex)
        NotCancelled
    }
  }
}