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

import java.net.MalformedURLException
import javax.inject.{Inject, Singleton}


import config.WSHttp
import models.PAYEStatus
import play.api.Logger
import play.api.mvc.Result
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http._
import play.api.http.Status._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NoStackTrace

object PAYEConnector extends PAYEConnector with ServicesConfig  {
  val http = WSHttp
  val payeBaseUrl =  baseUrl("paye-registration")
}
sealed trait CancellationResponse
case object Cancelled extends CancellationResponse
case object NotCancelled extends CancellationResponse

sealed trait cantCancelT extends Throwable with NoStackTrace
case object cantCancel extends cantCancelT

sealed trait PAYEResponse
case object PAYENotStarted extends PAYEResponse
case object PAYEErrorResponse extends PAYEResponse
case class PAYESuccessfulResponse(status: PAYEStatus) extends PAYEResponse

trait PAYEConnector {

  val http: HttpGet with HttpDelete

  val payeBaseUrl: String

  def getStatus(regId: String)(implicit hc: HeaderCarrier): Future[PAYEResponse] = {
    val url = s"$payeBaseUrl/paye-registration/$regId/status"
    http.GET[PAYEStatus](url) map { PAYESuccessfulResponse } recover {
      case ex: NotFoundException => PAYENotStarted
      case ex: HttpException =>
        Logger.error(s"[PAYEConnector] [getStatus] - ${ex.responseCode} response code was returned - reason : ${ex.message}", ex)
        PAYEErrorResponse
      case ex: Throwable =>
        Logger.error(s"[PAYEConnector] [getStatus] - Non-Http Exception caught", ex)
        PAYEErrorResponse
    }
  }

  def canStatusBeCancelled(regId:String)(f: String => Future[PAYEResponse])(implicit hc: HeaderCarrier):Future[String] = {
    f(regId).map{
      case a:PAYESuccessfulResponse => a.status.cancelURL.getOrElse(throw cantCancel)
      case _ => throw cantCancel
    }
  }
  def cancelReg(regID:String)(f: String => Future[PAYEResponse])(implicit hc:HeaderCarrier):Future[CancellationResponse] = {
      f(regID).flatMap{
        case a:PAYESuccessfulResponse =>
          http.DELETE[HttpResponse](a.status.cancelURL.getOrElse(throw cantCancel)).map {
            case  HttpResponse(OK,_,_,_)  => Cancelled
            case _ => NotCancelled
        }
      } recover {
        case ex:HttpException => Logger.error(s"[PAYEConnector] [cancelReg] - ${ex.responseCode} response code was returned - reason : ${ex.message}  ", ex)
          NotCancelled
        case ex:Throwable =>Logger.error(s"[PAYEConnector] [cancelReg] - Non-Http Exception caught", ex)
          NotCancelled
      }


  }
}
