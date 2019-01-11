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
import play.api.Logger
import play.api.libs.json.{Json, Writes}
import uk.gov.hmrc.http.{CorePost, HeaderCarrier, HttpPost, HttpResponse}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future
import scala.util.Random

case class DimensionValue(index: String, value: String)

case class GAEvent(category: String, action: String, label: String, dimensions: Seq[DimensionValue], userId: Option[String])

object GAEvent {
  implicit val dimensionWrites: Writes[DimensionValue] = Json.writes[DimensionValue]
  implicit val eventWrites: Writes[GAEvent] = Json.writes[GAEvent]
}

case class AnalyticsRequest(gaClientId: String, events: Seq[GAEvent])

object AnalyticsRequest {
  implicit val writes: Writes[AnalyticsRequest] = Json.writes[AnalyticsRequest]
}

object GAEvents {
  val invalidDESEmailFromUserDetails = GAEvent.apply("invalidEmailCat", "invalidEmailAction", "invalidEmailLabel", Seq.empty, None)
}

object PlatformAnalyticsConnector extends PlatformAnalyticsConnector with ServicesConfig {
  lazy val serviceUrl = baseUrl("platform-analytics")
  val http = WSHttp
  val gaClientId = s"GA1.1.${Math.abs(Random.nextInt())}.${Math.abs(Random.nextInt())}"
}

trait PlatformAnalyticsConnector {
  val serviceUrl: String
  val http: HttpPost with CorePost
  val gaClientId: String

  def sendEvents(events: GAEvent*)(implicit hc: HeaderCarrier): Future[Unit] = sendEvents(AnalyticsRequest(gaClientId, events))

  private def sendEvents(data: AnalyticsRequest)(implicit hc: HeaderCarrier) = {
    val url = s"$serviceUrl/platform-analytics/event"
    http.POST[AnalyticsRequest, HttpResponse](url, data, Seq.empty).map{ _ => ()}.recoverWith {
      case e: Exception =>
        Logger.error(s"Couldn't send analytics event $data", e)
        Future.successful(())
    }
  }
}
