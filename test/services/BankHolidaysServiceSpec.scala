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

package services

import connectors.BankHolidaysConnector
import helpers.UnitSpec
import models.JavaTimeUtils.{BankHoliday, BankHolidaySet}
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status.{NO_CONTENT, OK}
import uk.gov.hmrc.http.HttpResponse

import java.time.LocalDate
import scala.concurrent.ExecutionContext.global
import scala.concurrent.{ExecutionContext, Future}

class BankHolidaysServiceSpec extends UnitSpec with MockitoSugar {

  implicit def ec: ExecutionContext = global

  lazy val mockBankHolidayConnect: BankHolidaysConnector = mock[BankHolidaysConnector]

  trait Setup {
    val testBankHolidayService: BankHolidaysService = new BankHolidaysService(mockBankHolidayConnect)
  }


  "BankHolidaysService" should {

    "return none as result with fresh call before load of bank holidays" in new Setup {

      testBankHolidayService.getEnglandAndWalesBankHolidays mustBe None
    }

    "return correct result after load data for bank holidays" in new Setup {

      val bankHolidayJson: String =
        """
          |{
          |  "england-and-wales": {
          |    "division": "england-and-wales",
          |    "events": [
          |      {
          |        "title": "New Year’s Day",
          |        "date": "2019-01-01",
          |        "notes": "",
          |        "bunting": true
          |      },
          |      {
          |        "title": "Good Friday",
          |        "date": "2019-04-19",
          |        "notes": "",
          |        "bunting": false
          |      },
          |      {
          |        "title": "Easter Monday",
          |        "date": "2019-04-22",
          |        "notes": "",
          |        "bunting": true
          |      }
          |    ]
          |  },
          |  "scotland": {
          |    "division": "scotland",
          |    "events": [
          |      {
          |        "title": "New Year’s Day",
          |        "date": "2019-01-01",
          |        "notes": "",
          |        "bunting": true
          |      }
          |    ]
          |  },
          |  "northern-ireland": {
          |    "division": "northern-ireland",
          |    "events": [
          |      {
          |        "title": "New Year’s Day",
          |        "date": "2019-01-01",
          |        "notes": "",
          |        "bunting": true
          |      }
          |    ]
          |  }
          |}
          |""".stripMargin

      when(mockBankHolidayConnect.getBankHolidays).thenReturn(Future(HttpResponse(OK, bankHolidayJson)))
      val bH: BankHolidaySet = await(testBankHolidayService.loadEnglandAndWalesBankHolidays())

      bH.events.size mustBe 3
      val expectedBankHolidays: Seq[BankHoliday] = List(BankHoliday("New Year’s Day", LocalDate.parse("2019-01-01")),
        BankHoliday("Good Friday", LocalDate.parse("2019-04-19")), BankHoliday("Easter Monday", LocalDate.parse("2019-04-22")))
      bH.events mustBe expectedBankHolidays

      testBankHolidayService.getEnglandAndWalesBankHolidays mustBe Some(BankHolidaySet("england-and-wales", expectedBankHolidays.toList))
    }

    "return exception result  if the bank holiday connector fails" in new Setup {
      when(mockBankHolidayConnect.getBankHolidays).thenReturn(Future(HttpResponse(NO_CONTENT, "")))
      intercept[Exception](await(testBankHolidayService.loadEnglandAndWalesBankHolidays()))
    }

    "return exception result  if the bank holiday connector fails to return proper json" in new Setup {
      when(mockBankHolidayConnect.getBankHolidays).thenReturn(Future(HttpResponse(OK, "improper json")))
      intercept[Exception](await(testBankHolidayService.loadEnglandAndWalesBankHolidays()))
    }

    "fallback to read json data from file and no result on load bank holiday" in new Setup {
      when(mockBankHolidayConnect.getBankHolidays).thenReturn(Future(HttpResponse(NO_CONTENT, "")))
      intercept[Exception](await(testBankHolidayService.loadEnglandAndWalesBankHolidays()))
      testBankHolidayService.getEnglandAndWalesBankHolidays mustBe None
      testBankHolidayService.fetchBankHolidays().events.size mustBe 75
    }
  }

}
