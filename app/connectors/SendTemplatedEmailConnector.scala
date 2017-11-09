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
import models.{EmailVerificationRequest, SendTemplatedEmailRequest}
import play.api.Logger
import play.api.http.Status._
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http._

import scala.concurrent.Future
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import scala.util.control.NoStackTrace
import uk.gov.hmrc.http._

private[connectors] class TemplateEmailErrorResponse(s: String) extends NoStackTrace

object SendTemplatedEmailConnector extends SendTemplatedEmailConnector with ServicesConfig {
  val http : CorePost = WSHttp
  val sendTemplatedEmailURL = getConfString("email.sendAnEmailURL", throw new Exception("email.sendAnEmailURL not found"))

}

trait SendTemplatedEmailConnector extends HttpErrorFunctions {
  val http : CorePost
  val sendTemplatedEmailURL : String


  def requestTemplatedEmail(templatedEmailRequest : SendTemplatedEmailRequest)(implicit hc : HeaderCarrier) : Future[Boolean] = {
    def errorMsg(status: String, ex: HttpException) = {
      Logger.error(s"[SendTemplatedEmailConnector] [sendTemplatedEmail] request to send templated email returned a $status - email not sent - reason = ${ex.getMessage}")
      throw new TemplateEmailErrorResponse(status)
    }

    http.POST[SendTemplatedEmailRequest, HttpResponse] (s"$sendTemplatedEmailURL", templatedEmailRequest) map { r =>
      r.status match {
        case ACCEPTED => {
          Logger.debug("[SendTemplatedEmailConnector] [sendTemplatedEmail] request to email service was successful")
          true
        }
      }
    } recover {
      case ex: BadRequestException => errorMsg("400", ex)
      case ex: NotFoundException => errorMsg("404", ex)
      case ex: InternalServerException => errorMsg("500", ex)
      case ex: BadGatewayException => errorMsg("502", ex)
    }
  }

  private[connectors] def customRead(http: String, url: String, response: HttpResponse) =
    response.status match {
      case 400 => throw new BadRequestException("Provided incorrect data to Email Service")
      case 404 => throw new NotFoundException("Email not found")
      case 409 => response
      case 500 => throw new InternalServerException("Email service returned an error")
      case 502 => throw new BadGatewayException("Email service returned an upstream error")
      case _ => handleResponse(http, url)(response)
    }

}
