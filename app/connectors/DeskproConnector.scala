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
import models.external.Ticket
import utils.Logging
import play.api.libs.json._
import services.MetricsService
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2

import scala.concurrent.{ExecutionContext, Future}

class DeskproConnectorImpl @Inject()(val appConfig: AppConfig,
                                     val httpClientV2: HttpClientV2, val metricsService: MetricsService)(implicit val ec: ExecutionContext) extends DeskproConnector {
  override lazy val deskProUrl: String = appConfig.servicesConfig.baseUrl("hmrc-deskpro")
}

trait DeskproConnector extends Logging {

  val httpClientV2: HttpClientV2
  val deskProUrl : String
  val metricsService: MetricsService

  def submitTicket(t: Ticket)(implicit hc: HeaderCarrier, ec: ExecutionContext) : Future[Long] = {
    val deskproTimer = metricsService.deskproResponseTimer.time()
    httpClientV2.post(url"$deskProUrl/deskpro/ticket")
      .withBody(Json.toJson(t))
      .execute[JsObject]
      .map {
        res =>
          deskproTimer.stop()
          (res \ "ticket_id").as[Long]
      } recover {
        case e =>
          deskproTimer.stop()
          logger.warn(s"[submitTicket] returned ${e.getMessage}")
          throw e
      }
  }
}