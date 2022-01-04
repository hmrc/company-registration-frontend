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

import config.{FrontendAppConfig, WSHttp}
import javax.inject.Inject
import models.external.OtherRegStatus
import play.api.Logger
import play.api.http.Status._
import uk.gov.hmrc.http._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PAYEConnectorImpl @Inject()(val appConfig: FrontendAppConfig, val wSHttp: WSHttp) extends PAYEConnector

trait PAYEConnector extends ServiceConnector {
  val appConfig: FrontendAppConfig
  lazy val serviceBaseUrl = appConfig.servicesConfig.baseUrl("paye-registration")
  lazy val serviceUri = appConfig.servicesConfig.getConfString("paye-registration.uri", "/paye-registration")
}

class VATConnectorImpl @Inject()(val appConfig: FrontendAppConfig, val wSHttp: WSHttp) extends VATConnector

trait VATConnector extends ServiceConnector {
  val appConfig: FrontendAppConfig
  lazy val serviceBaseUrl = appConfig.servicesConfig.baseUrl("vat-registration")
  lazy val serviceUri = appConfig.servicesConfig.getConfString("vat-registration.uri", "/vatreg")
}

trait ServiceConnector {
  val wSHttp: HttpGet with HttpDelete with CoreGet with CoreDelete
  val serviceBaseUrl: String
  val serviceUri: String

  def getStatus(regId: String)(implicit hc: HeaderCarrier): Future[StatusResponse] = {
    val url = s"$serviceBaseUrl$serviceUri/$regId/status"
    wSHttp.GET[OtherRegStatus](url) map {
      SuccessfulResponse
    } recover {
      case ex: NotFoundException => NotStarted
      case ex: HttpException =>
        Logger.error(s"[ServiceConnector] [getStatus] - ${ex.responseCode} response code was returned - reason : ${ex.message}", ex)
        ErrorResponse
      case ex: Throwable =>
        Logger.error(s"[ServiceConnector] [getStatus] - Non-Http Exception caught", ex)
        ErrorResponse
    }
  }

  def canStatusBeCancelled(regId: String)(f: String => Future[StatusResponse])(implicit hc: HeaderCarrier): Future[String] = {
    f(regId).map {
      case a: SuccessfulResponse => a.status.cancelURL.getOrElse(throw cantCancel)
      case _ => throw cantCancel
    }
  }

  def cancelReg(regID: String)(f: String => Future[StatusResponse])(implicit hc: HeaderCarrier): Future[CancellationResponse] = {
    f(regID).flatMap {
      case a: SuccessfulResponse =>
        wSHttp.DELETE[HttpResponse](a.status.cancelURL.getOrElse(throw cantCancel)).map { resp =>
          if (resp.status == OK) {
            Cancelled
          } else {
            NotCancelled
          }
        }
      case _ => throw cantCancel
    } recover {
      case ex: HttpException => Logger.error(s"[ServiceConnector] [cancelReg] - ${ex.responseCode} response code was returned - reason : ${ex.message}  ", ex)
        NotCancelled
      case ex: cantCancelT => Logger.error(s"[ServiceConnector] [cancelReg] - $ex functionPassed in to return regId succeeded but didn't return a SuccessfulResponse (getStatus)")
        NotCancelled
      case ex: Throwable => Logger.error(s"[ServiceConnector] [cancelReg] - Non-Http Exception caught", ex)
        NotCancelled
    }
  }
}