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

import javax.inject.{Inject, Singleton}

import config.WSHttp
import models.external.Ticket
import play.api.Logger
import services.MetricsService
import uk.gov.hmrc.play.http.ws.WSHttp
import play.api.libs.json._
import uk.gov.hmrc.play.config.ServicesConfig

import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import scala.concurrent.Future
import uk.gov.hmrc.http.{ HeaderCarrier, HttpPost }

object DeskproConnectorImpl extends DeskproConnector with ServicesConfig {
  val metricsService: MetricsService = MetricsService
  override val http = WSHttp
  override val deskProUrl: String = baseUrl("hmrc-deskpro")
}

trait DeskproConnector {

  val http: HttpPost
  val deskProUrl : String
  val metricsService: MetricsService

  def submitTicket(t: Ticket)(implicit hc: HeaderCarrier) : Future[Long] = {
    val deskproTimer = metricsService.deskproResponseTimer.time()
    http.POST[Ticket, JsObject](s"$deskProUrl/deskpro/ticket", t) map {
      res =>
        deskproTimer.stop()
        (res \ "ticket_id").as[Long]
    } recover {
      case e =>
        deskproTimer.stop()
        Logger.warn(s"[DeskproConnector] [submitTicket] returned ${e.getMessage}")
        throw e
    }
  }

}
