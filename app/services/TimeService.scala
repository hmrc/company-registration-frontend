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

package services

import config.AppConfig
import models.JavaTimeUtils.BankHolidaySet
import models.JavaTimeUtils.BankHolidays.LocalDateWithHolidays
import models.JavaTimeUtils.DateTimeUtils.isEqualOrAfter
import play.api.libs.json.Json
import utils.SystemDate

import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime}
import javax.inject.Inject
import scala.io.Source
import scala.util.{Failure, Success, Try}

object TimeHelper {
  def splitDate(date: String): Array[String] =
    for (i <- date.split("-")) yield i
  def toDateTime(d: Option[String], m: Option[String], y: Option[String]): Option[LocalDate] =
    if (d.isDefined && m.isDefined && y.isDefined) {
      val (iY, iM, iD) = (y.get.toInt, m.get.toInt, d.get.toInt)
      Some(LocalDate.of(iY, iM, iD))
    } else {
      None
    }

}

class TimeServiceImpl @Inject() (val appConfig: AppConfig) extends TimeService {
  override lazy val dayEndHour: Int =
    appConfig.servicesConfig.getConfInt("time-service.day-end-hour", throw new Exception("could not find config key time-service.day-end-hour"))
  override def currentDateTime: LocalDateTime = LocalDateTime.now()
  override def currentLocalDate: LocalDate    = SystemDate.getSystemDate
  override lazy val bHS: BankHolidaySet       = BankHolidays.fetchEnglandAndWalesBankHolidays
}

object BankHolidays {

  val fetchEnglandAndWalesBankHolidays: BankHolidaySet = {
    val jsonStr = {
      val bhSource = Source.fromURL("https://www.gov.uk/bank-holidays.json")
      try
        bhSource.mkString
      finally
        bhSource.close()
    }
    val json = Json.parse(jsonStr)

    (json \ "england-and-wales").as[BankHolidaySet]
  }

}

trait TimeService {

  val DATE_FORMAT = "yyyy-MM-dd"

  val bHS: BankHolidaySet

  val dayEndHour: Int

  def currentDateTime: LocalDateTime

  def currentLocalDate: LocalDate

  val getCurrentHour: Int = LocalDateTime.now().getHour

  // If today is still within working hours, it is included in the 3 days
  def isDateAtLeastThreeWorkingDaysInFuture(futureDate: LocalDate)(implicit bHS: BankHolidaySet): Boolean =
    isEqualOrAfter(getWorkingDays, futureDate)

  private def getWorkingDays(implicit bHS: BankHolidaySet): LocalDate =
    currentLocalDate plusWorkingDays getDaysInAdvance(getCurrentHour)

  private def getDaysInAdvance(currentHour: Int)(implicit bHS: BankHolidaySet): Int =
    if (LocalDateWithHolidays(currentLocalDate).isWorkingDay && currentHour < dayEndHour) 2 else 3

  def futureWorkingDate(date: LocalDate, days: Int)(implicit bHS: BankHolidaySet): String = {
    val futureDate = date.plusWorkingDays(days)
    DateTimeFormatter.ofPattern("dd MM yyyy").format(futureDate)
  }

  def splitDate(date: String): Array[String] =
    for (i <- date.split("-")) yield i

  def validate(date: String): Boolean = {
    val format = new SimpleDateFormat(DATE_FORMAT)
    format.setLenient(false)

    Try(format.parse(date)) match {
      case Success(_) => true
      case Failure(_) => false
    }
  }
}
