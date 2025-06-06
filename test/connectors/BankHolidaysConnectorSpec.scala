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

import config.AppConfig
import helpers.UnitSpec
import mocks.SCRSMocks
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.{any, anyString}
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status.{NOT_FOUND, OK}
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.global

class BankHolidaysConnectorSpec extends UnitSpec with SCRSMocks {

  implicit def ec: ExecutionContext = global

  trait Setup {
    val testBankHolidayConnector: BankHolidaysConnector = new BankHolidaysConnector(mockHttpClientV2, mockAppConfig)
  }

  "BankHolidaysConnector" should  {

    "return positive 200 status" in new Setup {

      val jsonResponse: JsValue = Json.parse(s"""{"holiday" : "5/5/2025"}""")
      mockHttpGET(HttpResponse(OK, "testJson"))
      when(mockAppConfig.bankHolidaysApiUrl).thenReturn("https://www.gov.uk/bank-holidays.json")
      when(mockRequestBuilder.withProxy).thenReturn(mockRequestBuilder)

      val result: HttpResponse = await(testBankHolidayConnector.getBankHolidays)
      result.status mustBe 200
      result.body mustBe "testJson"
    }

    "return 404" in new Setup {

      mockHttpGET(HttpResponse(NOT_FOUND, ""))
      when(mockAppConfig.bankHolidaysApiUrl).thenReturn("https://www.gov.uk/bank-holidays.json")
      when(mockRequestBuilder.withProxy).thenReturn(mockRequestBuilder)

      val result: HttpResponse = await(testBankHolidayConnector.getBankHolidays)
      result.status mustBe 404
      result.body mustBe ""
    }

  }


}
