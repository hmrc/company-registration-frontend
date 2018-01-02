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

import config.WSHttp
import play.api.libs.json.JsValue
import services.MetricsService
import uk.gov.hmrc.play.config.ServicesConfig

import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import scala.concurrent.Future
import uk.gov.hmrc.http.{CoreGet, HeaderCarrier, HttpException, HttpGet}

object IncorpInfoConnector extends IncorpInfoConnector with ServicesConfig{
  val http : CoreGet = WSHttp
  val incorpInfoUrl = s"${baseUrl("incorp-info")}/incorporation-information"

  override val metricsService = MetricsService
}

trait IncorpInfoConnector {
  val http: CoreGet
  val incorpInfoUrl: String

  val metricsService: MetricsService

  def getCompanyName(transId: String)(implicit hc: HeaderCarrier): Future[String] = {
    getCompanyProfile(transId).map(js => (js \ "company_name").as[String])
  }

  def getCompanyProfile(transId: String)(implicit hc: HeaderCarrier): Future[JsValue] = {
    metricsService.processDataResponseWithMetrics[JsValue](metricsService.retrieveCompanyProfileIITimer.time()) {
      http.GET[JsValue](s"$incorpInfoUrl/$transId/company-profile") recover handleError(transId, "getCompanyProfile")
    }
  }

  private def handleError(transId: String, funcName: String):PartialFunction[Throwable, JsValue] = {
    case ex: HttpException =>
      throw new Exception(s"[IncorpInfoConnector] [$funcName] - An exception was caught. Response code : ${ex.responseCode} reason : ${ex.message}")
    case ex: Throwable =>
      throw new Exception

  }

}
