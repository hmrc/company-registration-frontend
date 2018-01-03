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

import config.{WSHttp, WSHttpProxy}
import play.api.Logger
import play.api.libs.json.JsValue
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.ws.WSProxy
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import utils.SCRSFeatureSwitches

import scala.concurrent.Future
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.logging.Authorization

sealed trait CohoApiResponse
case class CohoApiSuccessResponse(json: JsValue) extends CohoApiResponse
case object CohoApiBadRequestResponse extends CohoApiResponse
case object CohoApiNoData extends CohoApiResponse
case class CohoApiErrorResponse(ex: Exception) extends CohoApiResponse

object CohoAPIConnector extends CohoAPIConnector with ServicesConfig {
  val cohoAPIStubUrl = getConfString("coho-api.stub-url", throw new Exception("coho-api.stub-url not found"))
  val cohoAPIUrl = getConfString("coho-api.url", throw new Exception("coho-api.url not found"))
  val cohoApiAuthToken = getConfString("coho-api.token", throw new Exception("coho-api.token not found"))
  val httpNoProxy = WSHttp
  val httpProxy = WSHttpProxy
  val featureSwitch = SCRSFeatureSwitches
}

trait CohoAPIConnector extends HttpErrorFunctions {

  val cohoAPIStubUrl: String
  val cohoAPIUrl: String
  val cohoApiAuthToken: String
  val httpNoProxy: CoreGet
  val httpProxy: CoreGet with WSProxy
  val featureSwitch: SCRSFeatureSwitches

  private[connectors] def httpResponseRead(http: String, url: String, response: HttpResponse) = {
    response.status match {
      case 416 => Logger.warn(s"[CohoAPIConnector] [fetchIncorporationStatus] - Received a 416 Http response. There are no incorporations ready to process.")
                  response
      case _   => handleResponse(http, url)(response)
    }
  }

  private val httpRds = new HttpReads[HttpResponse] {
    def read(http: String, url: String, res: HttpResponse) = httpResponseRead(http, url, res)
  }

  private[connectors] def buildQueryString(timePoint: Option[String], itemsPerPage: Int) = {
    timePoint match {
      case Some(tp) => s"?timepoint=$tp&items_per_page=$itemsPerPage"
      case _ => s"?items_per_page=$itemsPerPage"
    }
  }

  def fetchIncorporationStatus(timePoint: Option[String], itemsPerPage: Int)(implicit hc: HeaderCarrier): Future[CohoApiResponse] = {
    val queryString = buildQueryString(timePoint, itemsPerPage)
    val (http, realHc, url) = useProxy match {
      case true => (httpProxy, appendAPIAuthHeader(hc, cohoApiAuthToken), s"$cohoAPIUrl$queryString")
      case false => (httpNoProxy, hc, s"$cohoAPIStubUrl$queryString")
    }
    http.GET[HttpResponse](url)(httpRds, realHc, implicitly) map {
      res =>
        res.status match {
        case 416 => CohoApiNoData
        case _ => CohoApiSuccessResponse(res.json)
      }
    } recover {
      case ex: BadRequestException =>
        Logger.error(s"[CohoAPIConnector] [fetchIncorporationStatus] - ${badRequestMessage("GET", url, ex.getMessage)}")
        CohoApiBadRequestResponse
      case ex: HttpException =>
        Logger.error(s"[CohoAPIConnector] [fetchIncorporationStatus] - ${upstreamResponseMessage("GET", url, ex.responseCode, ex.message)}")
        CohoApiErrorResponse(ex)
      case ex: Exception =>
        Logger.error(s"[CohoAPIConnector] [fetchIncorporationStatus] - An unknown exception was caught - ${ex.getMessage}")
        CohoApiErrorResponse(ex)
    }
  }

  private[connectors] def useProxy: Boolean = {
    featureSwitch.cohoFirstHandOff.enabled
  }

  private[connectors] def appendAPIAuthHeader(hc: HeaderCarrier, token: String): HeaderCarrier = {
    hc.copy(authorization = Some(Authorization(s"Bearer $token")))
  }
}
