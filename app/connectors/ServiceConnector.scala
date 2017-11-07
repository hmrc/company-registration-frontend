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
import models.external.OtherRegStatus
import play.api.Logger
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http._
import play.api.http.Status._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object PAYEConnector extends ServiceConnector with ServicesConfig  {
  val http = WSHttp
  val serviceBaseUrl =  baseUrl("paye-registration")
  val serviceUri = getConfString("paye-registration.uri", "/paye-registration")
}

object VATConnector extends ServiceConnector with ServicesConfig  {
  val http = WSHttp
  val serviceBaseUrl =  baseUrl("vat-registration")
  val serviceUri = getConfString("vat-registration.uri", "/vatreg")
}

trait ServiceConnector {
  val http: HttpGet with HttpDelete
  val serviceBaseUrl: String
  val serviceUri: String

  def getStatus(regId: String)(implicit hc: HeaderCarrier): Future[StatusResponse] = {
    val url = s"$serviceBaseUrl$serviceUri/$regId/status"
    http.GET[OtherRegStatus](url) map { SuccessfulResponse } recover {
      case ex: NotFoundException => NotStarted
      case ex: HttpException =>
        Logger.error(s"[ServiceConnector] [getStatus] - ${ex.responseCode} response code was returned - reason : ${ex.message}", ex)
        ErrorResponse
      case ex: Throwable =>
        Logger.error(s"[ServiceConnector] [getStatus] - Non-Http Exception caught", ex)
        ErrorResponse
    }
  }

  def canStatusBeCancelled(regId:String)(f: String => Future[StatusResponse])(implicit hc: HeaderCarrier):Future[String] = {
    f(regId).map{
      case a:SuccessfulResponse => a.status.cancelURL.getOrElse(throw cantCancel)
      case _ => throw cantCancel
    }
  }
  def cancelReg(regID:String)(f: String => Future[StatusResponse])(implicit hc:HeaderCarrier):Future[CancellationResponse] = {
      f(regID).flatMap{
        case a:SuccessfulResponse =>
          http.DELETE[HttpResponse](a.status.cancelURL.getOrElse(throw cantCancel)).map {
            case  HttpResponse(OK,_,_,_)  => Cancelled
            case _ => NotCancelled
        }
      } recover {
        case ex:HttpException => Logger.error(s"[ServiceConnector] [cancelReg] - ${ex.responseCode} response code was returned - reason : ${ex.message}  ", ex)
          NotCancelled
        case ex:Throwable =>Logger.error(s"[ServiceConnector] [cancelReg] - Non-Http Exception caught", ex)
          NotCancelled
      }


  }
}
