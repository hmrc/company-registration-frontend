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
import play.api.Logger
import play.api.libs.json.JsValue
import services.MetricsService
import uk.gov.hmrc.http.{CoreGet, HeaderCarrier, HttpException, HttpResponse}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future

object IncorpInfoConnector extends IncorpInfoConnector with ServicesConfig {
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


  def injectTestIncorporationUpdate(transId: String, isSuccess: Boolean)(implicit hc: HeaderCarrier): Future[Boolean] = {
    val queryString = s"txId=$transId&date=2018-1-1${if(isSuccess) "&crn=12345678" else ""}&success=$isSuccess"

    http.GET[HttpResponse](s"$incorpInfoUrl/test-only/add-incorp-update/?$queryString") map (_ => true) recover { case _ =>
        Logger.error(s"[IncorpInfoConnector] [injectTestIncorporationUpdate] Failed to inject a test incorporation update into II for $transId")
        false
    }
  }

  def manuallyTriggerIncorporationUpdate(implicit hc:HeaderCarrier): Future[Boolean] = {
    http.GET[HttpResponse](s"$incorpInfoUrl/test-only/manual-trigger/fireSubs") map (_ => true) recover { case _ =>
        Logger.error(s"[IncorpInfoConnector] [manuallyTriggerIncorporationUpdate] Failed to trigger subscription processing on II")
        false
    }
  }

}
