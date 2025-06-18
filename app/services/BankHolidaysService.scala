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

import com.google.inject.{Inject, Singleton}
import connectors.BankHolidaysConnector
import models.JavaTimeUtils.{BankHoliday, BankHolidaySet}
import models.{BankHoliday, BankHolidays, JavaTimeUtils}
import play.api.Logger
import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK}
import play.api.libs.json.{JsError, JsSuccess, Json, Reads}
import services.BankHolidaysService.{Event, GDSBankHolidays, loadFromBankHolidayJson}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import utils.Logging

import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source

@Singleton
class BankHolidaysService @Inject()(val bankHolidaysConnector: BankHolidaysConnector)(implicit val ec: ExecutionContext) extends Logging {

  private var cachedBankHolidays : Option[BankHolidaySet] = None

  def getEnglandAndWalesBankHolidays: Option[BankHolidaySet] = cachedBankHolidays

  def loadEnglandAndWalesBankHolidays(): Future[BankHolidaySet] = {
    bankHolidaysConnector.getBankHolidays.map { httpResponse =>
      if (httpResponse.status == OK) {
        httpResponse.json.validate[GDSBankHolidays] match {
          case JsSuccess(gdsBankHolidays, _) =>
            logger.info(s"Got success http status ${httpResponse.status.toString} when calling the bank holiday API")
            val bh = toEnglandAndWalesBankHolidays(gdsBankHolidays)
            cachedBankHolidays = Some(bh)
            bh
          case JsError(e) =>
            throw new Exception(s"Could not parse response from GDS get bank holidays API: ${e.toString()}")
        }
      } else {
        logger.warn(s"Got http status ${httpResponse.status.toString} when calling the bank holiday API")
        throw UpstreamErrorResponse(
          message = "Could not retrieve bank holidays",
          statusCode = httpResponse.status,
          reportAs = INTERNAL_SERVER_ERROR
        )
      }
    }
  }

  private def toEnglandAndWalesBankHolidays(gdsBankHolidays: GDSBankHolidays): BankHolidaySet = {
    BankHolidaySet("england-and-wales",
      gdsBankHolidays.`england-and-wales`.events.map(event => JavaTimeUtils.BankHoliday(event.title,event.date)).toList)
  }

  def fetchBankHolidays(): BankHolidaySet = {
    getEnglandAndWalesBankHolidays.getOrElse(loadFromBankHolidayJson())
  }
}


object BankHolidaysService extends Logging {

  final case class Event(title: String, date: LocalDate)

  final case class RegionalResult(events: Seq[Event])

  final case class GDSBankHolidays(
                                    `england-and-wales`: RegionalResult,
                                    scotland: RegionalResult,
                                    `northern-ireland`: RegionalResult
                                  )

  implicit val eventReads: Reads[Event] = {
    implicit val eventDateReads: Reads[LocalDate] = Reads.localDateReads(DateTimeFormatter.ISO_DATE)
    Json.reads[Event]
  }

  implicit val regionalResultReads: Reads[RegionalResult] = Json.reads[RegionalResult]

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  implicit val gdsBankHolidaysReads: Reads[GDSBankHolidays] = Json.reads[GDSBankHolidays]

  def loadFromBankHolidayJson(): BankHolidaySet = {
    val jsonStr = {
      logger.info("Trying to read BankHolidays from conf file")
      val url = getClass.getClassLoader.getResource("bank-holidays.json")
      val bhSource = Source.fromURL(url, StandardCharsets.UTF_8.name())
      try
        bhSource.mkString
      finally
        bhSource.close()
    }
    val json = Json.parse(jsonStr)
    logger.info("Completed reading BankHolidays from conf file")
    (json \ "england-and-wales").as[BankHolidaySet]
  }

}