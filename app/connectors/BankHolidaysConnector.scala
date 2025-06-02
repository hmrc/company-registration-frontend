/*
 * Copyright 2025 HM Revenue & Customs
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


import com.google.inject.{Inject, Singleton}
import config.AppConfig
import play.api.http.HeaderNames
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http.client.HttpClientV2

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BankHolidaysConnector @Inject() (val httpClientV2: HttpClientV2, val appConfig: AppConfig)(implicit val ec: ExecutionContext) {


  implicit val hc: HeaderCarrier = HeaderCarrier().withExtraHeaders("Accept" -> "application/vnd.hmrc.1.0+json")

  def getBankHolidays: Future[HttpResponse] =
    httpClientV2
      .get(url"${appConfig.bankHolidaysApiUrl}")
      .setHeader(HeaderNames.FROM -> appConfig.bankHolidaysApiFromEmailAddress)
      .withProxy
      .execute

}
