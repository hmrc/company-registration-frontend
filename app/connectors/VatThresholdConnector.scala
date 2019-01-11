/*
 * Copyright 2019 HM Revenue & Customs
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
import org.joda.time.LocalDate
import play.api.Logger
import play.api.http.Status._
import play.api.libs.json.{JsNull, JsString}
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future
import scala.util.control.NoStackTrace

private[connectors] class VatErrorResponse(s: String) extends NoStackTrace

object VatThresholdConnector extends VatThresholdConnector with ServicesConfig {
  val http: CoreGet = WSHttp
  val serviceBaseUrl =  baseUrl("vat-registration")
  val serviceUri = getConfString("vat-registration.uri", "/vatreg")
}

trait VatThresholdConnector extends HttpErrorFunctions {
  val http : CoreGet
  val serviceBaseUrl: String
  val serviceUri: String


  def getVATThreshold(date: LocalDate)(implicit hc: HeaderCarrier): Future[String] = {
    val url=s"${serviceBaseUrl}${serviceUri}/threshold/${date.toString}"
    http.GET[HttpResponse](url).map{
      _.json match {
        case JsNull   => logAndThrow(s"[getVATThreshold] taxable-threshold for $date not found")
        case json @ _ => (json \ "taxable-threshold").toOption match {
          case Some(JsString(value)) => value
          case Some(_)               => logAndThrow("[getVATThreshold] taxable-threshold is not a string")
          case None                  => logAndThrow("[getVATThreshold] taxable-threshold key not found")
        }
      }
    } recover {
      case ex: NotFoundException => Logger.error(s"[VATThresholdConnector] [getVATThreshold] $ex"); throw ex
      case ex: InternalServerException => Logger.error(s"[VATThresholdConnector] [getVATThreshold] $ex"); throw ex
      case ex: BadGatewayException => Logger.error(s"[VATThresholdConnector] [getVATThreshold] $ex"); throw ex
    }
  }

    private def logAndThrow(msg: String) = {
    Logger.error(msg)
    throw new RuntimeException(msg)
  }
}
