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
import models.IncorporationResponse
import models.test.ETMPNotification
import play.api.libs.json.{Json, Reads}
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2

import scala.concurrent.{ExecutionContext, Future}

class DynamicStubConnectorImpl @Inject()(val httpClientV2: HttpClientV2, appConfig: AppConfig)(implicit val ec: ExecutionContext) extends DynamicStubConnector {

 lazy val busRegDyUrl = s"${appConfig.servicesConfig.baseUrl("business-registration-dynamic-stub")}/business-registration"
}

trait DynamicStubConnector {
  val httpClientV2: HttpClientV2
  val busRegDyUrl : String
  implicit val ec: ExecutionContext

  implicit val hc: HeaderCarrier = HeaderCarrier().withExtraHeaders("Accept" -> "application/vnd.hmrc.1.0+json")

  implicit val rds: Reads[IncorporationResponse] = Json.reads[IncorporationResponse]
  hc.withExtraHeaders("Accept" -> "application/vnd.hmrc.1.0+json")

  def postETMPNotificationData(etmp : ETMPNotification)(implicit hc : HeaderCarrier) : Future[HttpResponse] = {
    httpClientV2//.POST[JsValue, HttpResponse](s"$busRegDyUrl/cache-etmp-notification", Json.toJson(etmp))(implicitly, readRaw, hc, ec)
      .post(url"$busRegDyUrl/cache-etmp-notification")
      .withBody(Json.toJson(etmp))
      .execute[HttpResponse](readRaw, ec)
  }

  def simulateDesPost(ackRef: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    httpClientV2
      .get(url"$busRegDyUrl/simulate-des-post/$ackRef")
      .execute[HttpResponse](readRaw, ec)
  }
}