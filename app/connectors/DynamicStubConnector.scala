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

import config.{FrontendAppConfig, WSHttp}
import javax.inject.Inject
import models.IncorporationResponse
import models.test.ETMPNotification
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http._
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

class DynamicStubConnectorImpl @Inject()(val wSHttp: WSHttp, appConfig: FrontendAppConfig) extends DynamicStubConnector {

 lazy val busRegDyUrl = s"${appConfig.baseUrl("business-registration-dynamic-stub")}/business-registration"
}

trait DynamicStubConnector {
  val wSHttp: CoreGet with CorePost with CorePut
  val busRegDyUrl : String

  implicit val hc: HeaderCarrier = HeaderCarrier().withExtraHeaders("Accept" -> "application/vnd.hmrc.1.0+json")

  implicit val rds = Json.reads[IncorporationResponse]
  hc.withExtraHeaders("Accept" -> "application/vnd.hmrc.1.0+json")

  def postETMPNotificationData(etmp : ETMPNotification)(implicit hc : HeaderCarrier) : Future[HttpResponse] = {
    val json = Json.toJson(etmp)
    wSHttp.POST[JsValue, HttpResponse](s"$busRegDyUrl/cache-etmp-notification", json)
  }

  def simulateDesPost(ackRef: String)(implicit hc: HeaderCarrier) = {
    wSHttp.GET[HttpResponse](s"$busRegDyUrl/simulate-des-post/$ackRef")
  }
}