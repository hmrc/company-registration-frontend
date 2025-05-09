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

import javax.inject.Inject
import config.AppConfig
import models.SendTemplatedEmailRequest
import utils.Logging
import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NoStackTrace

private[connectors] class TemplateEmailErrorResponse(s: String) extends NoStackTrace

class SendTemplatedEmailConnectorImpl @Inject()(appConfig: AppConfig, val httpClientV2: HttpClientV2)(implicit val ec: ExecutionContext) extends SendTemplatedEmailConnector {
  lazy val sendTemplatedEmailURL = appConfig.servicesConfig.getConfString("email.sendAnEmailURL", throw new Exception("email.sendAnEmailURL not found"))
}

trait SendTemplatedEmailConnector extends HttpErrorFunctions with Logging {
  val httpClientV2 : HttpClientV2
  val sendTemplatedEmailURL : String

  def requestTemplatedEmail(templatedEmailRequest : SendTemplatedEmailRequest)(implicit hc : HeaderCarrier, ec: ExecutionContext) : Future[Boolean] = {
    def errorMsg(status: String, ex: HttpException) = {
      logger.error(s"[sendTemplatedEmail] request to send templated email returned a $status - email not sent - reason = ${ex.getMessage}")
      throw new TemplateEmailErrorResponse(status)
    }

    httpClientV2.post(url"$sendTemplatedEmailURL")
      .withBody(Json.toJson(templatedEmailRequest))
      .execute[HttpResponse]
      .map { r =>
        r.status match {
          case ACCEPTED => {
            logger.debug("[sendTemplatedEmail] request to email service was successful")
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
      case _ => handleResponseEither(http, url)(response) match {
        case Left(upstreamErrorResponse) => {
          throw new Exception(s"$http to $url failed with status ${upstreamErrorResponse.statusCode}. Response body: '${response.body}'")
        }
        case Right(response) => response
      }
    }
}