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
import models.IncorporationResponse
import models.test.{ETMPCTRecordUpdates, ETMPNotification}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http._
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.Future
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.http._

object DynamicStubConnector extends DynamicStubConnector with ServicesConfig {
  val http = WSHttp
  val busRegDyUrl = s"${baseUrl("business-registration-dynamic-stub")}/business-registration"
}

trait DynamicStubConnector {
  val http: CoreGet with CorePost with CorePut
  val busRegDyUrl : String

  implicit val hc: HeaderCarrier = HeaderCarrier().withExtraHeaders("Accept" -> "application/vnd.hmrc.1.0+json")

  implicit val rds = Json.reads[IncorporationResponse]
  hc.withExtraHeaders("Accept" -> "application/vnd.hmrc.1.0+json")

  def postETMPNotificationData(etmp : ETMPNotification)(implicit hc : HeaderCarrier) : Future[HttpResponse] = {
    val json = Json.toJson(etmp)
    http.POST[JsValue, HttpResponse](s"$busRegDyUrl/cache-etmp-notification", json)
  }

  def simulateDesPost(ackRef: String)(implicit hc: HeaderCarrier) = {
    http.GET[HttpResponse](s"$busRegDyUrl/simulate-des-post/$ackRef")
  }
}
