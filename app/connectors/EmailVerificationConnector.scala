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
import connectors.httpParsers.AddressLookupHttpParsers._
import models.EmailVerificationRequest
import utils.Logging
import play.api.http.Status._
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NoStackTrace

private[connectors] class EmailErrorResponse(s: String) extends NoStackTrace

class EmailVerificationConnectorImpl @Inject()(val httpClientV2: HttpClientV2, val appConfig: AppConfig) extends EmailVerificationConnector {
  lazy val sendVerificationEmailURL: String = appConfig.servicesConfig.getConfString("email-vs.sendVerificationEmailURL", throw new Exception("email.sendVerificationEmailURL not found"))
  lazy val checkVerifiedEmailURL: String = appConfig.servicesConfig.getConfString("email-vs.checkVerifiedEmailURL", throw new Exception("email.checkVerifiedEmailURL not found"))
}

trait EmailVerificationConnector extends HttpErrorFunctions with Logging {
  val httpClientV2: HttpClientV2
  val sendVerificationEmailURL : String
  val checkVerifiedEmailURL : String

  implicit val reads: HttpReads[HttpResponse] = new HttpReads[HttpResponse] {
    def read(http: String, url: String, res: HttpResponse) = customRead(http, url, res)
  }

  def checkVerifiedEmail(email : String)(implicit hc : HeaderCarrier, ec: ExecutionContext) : Future[Boolean] = {
    def errorMsg(status: String) = {
      logger.debug(s"[checkVerifiedEmail] request to check verified email returned a $status - email not found / not verified")
      false
    }

    httpClientV2.post(url"$checkVerifiedEmailURL")
      .withBody(Json.obj("email" -> email))
      .execute(reads, ec) map {
      _.status match {
        case OK => true
      }
    } recover {
      case ex: NotFoundException => errorMsg("404")
      case ex: InternalServerException => errorMsg("500")
      case ex: BadGatewayException => errorMsg("502")
    }
  }

  def requestVerificationEmailReturnVerifiedEmailStatus(emailRequest: EmailVerificationRequest)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] = {
    def errorMsg(status: String, ex: HttpException): Nothing = {
      logger.error(s"[requestVerificationEmail] request to send verification email returned a $status - email not sent - reason = ${ex.getMessage}")
      throw new EmailErrorResponse(status)
    }

    httpClientV2.post(url"$sendVerificationEmailURL")
      .withBody(Json.toJson(emailRequest))
      .execute(rawReads, ec) map { r =>
      r.status match {
        case CREATED => {
          logger.debug("[requestVerificationEmailReturnVerifiedEmailStatus] request to verification service successful")
          false
        }
        case CONFLICT =>
          logger.warn("[requestVerificationEmailReturnVerifiedEmailStatus] request to send verification email returned a 409 - email already verified")
          true
      }
    } recover {
      case ex: BadRequestException => errorMsg("400", ex)
      case ex: NotFoundException => errorMsg("404", ex)
      case ex: InternalServerException => errorMsg("500", ex)
      case ex: BadGatewayException => errorMsg("502", ex)
    }
  }

  private[connectors] def customRead(http: String, url: String, response: HttpResponse): HttpResponse = {
    response.status match {
      case BAD_REQUEST => throw new BadRequestException("Provided incorrect data to Email Verification")
      case NOT_FOUND => throw new NotFoundException("Email not found")
      case CONFLICT => response
      case INTERNAL_SERVER_ERROR => throw new InternalServerException("Email service returned an error")
      case BAD_GATEWAY => throw new BadGatewayException("Email service returned an upstream error")
      case _ => handleResponseEither(http, url)(response) match {
        case Left(upstreamErrorResponse) => {
          throw new Exception(s"$http to $url failed with status ${upstreamErrorResponse.statusCode}. Response body: '${response.body}'")
        }
        case Right(response) => response
      }
    }
  }
}