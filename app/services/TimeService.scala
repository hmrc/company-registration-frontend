/*
 * Copyright 2022 HM Revenue & Customs
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

import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime}

import config.AppConfig
import javax.inject.Inject
import models.JavaTimeUtils.BankHolidays.LocalDateWithHolidays
import models.JavaTimeUtils.DateTimeUtils._
import models.JavaTimeUtils.{BankHoliday, BankHolidaySet}
import utils.SystemDate

import scala.util.{Failure, Success, Try}

object TimeHelper {
  val DATE_FORMAT = "yyyy-MM-dd"
  def splitDate(date: String): Array[String] = {
    for (i <- date.split("-")) yield i
  }
  def toDateTime(d: Option[String], m: Option[String], y: Option[String]): Option[LocalDateTime] = {
    if(d.isDefined && m.isDefined && y.isDefined) {
      val (iY, iM, iD) = (y.get.toInt, m.get.toInt, d.get.toInt)
      Some(LocalDateTime.of(iY, iM, iD, 0, 0))
    } else {
      None
    }
  }

}

class TimeServiceImpl @Inject()(val appConfig: AppConfig) extends TimeService {
  override lazy val dayEndHour      = appConfig.servicesConfig.getConfInt("time-service.day-end-hour", throw new Exception("could not find config key time-service.day-end-hour"))
  override def currentDateTime      = LocalDateTime.now()
  override def currentLocalDate     = SystemDate.getSystemDate
  override lazy val bHS: BankHolidaySet       = BankHolidays.bankHolidaySet
}

object BankHolidays {
  val bankHolidaySet: BankHolidaySet = BankHolidaySet("england-and-wales", List(
    BankHoliday(title = "Christmas Day",          date = LocalDate.of(2018, 12, 25)),
    BankHoliday(title = "Boxing Day",             date = LocalDate.of(2018, 12, 26)),
    BankHoliday(title = "New Year's Day",         date = LocalDate.of(2019, 1, 1)),
    BankHoliday(title = "Good Friday",            date = LocalDate.of(2019, 4, 19)),
    BankHoliday(title = "Easter Monday",          date = LocalDate.of(2019, 4, 22)),
    BankHoliday(title = "Early May bank holiday", date = LocalDate.of(2019, 5, 6)),
    BankHoliday(title = "Spring bank holiday",    date = LocalDate.of(2019, 5, 27)),
    BankHoliday(title = "Summer bank holiday",    date = LocalDate.of(2019, 8, 26)),
    BankHoliday(title = "Christmas Day",          date = LocalDate.of(2019, 12, 25)),
    BankHoliday(title = "Boxing Day",             date = LocalDate.of(2019, 12, 26))
  ))
}

trait TimeService {

  val DATE_FORMAT = "yyyy-MM-dd"

  val bHS: BankHolidaySet

  val dayEndHour: Int

  def currentDateTime: LocalDateTime

  def currentLocalDate: LocalDate

  val getCurrentHour = LocalDateTime.now()

  def isDateSomeWorkingDaysInFuture(futureDate: LocalDate)(implicit bHS: BankHolidaySet): Boolean = {
    isEqualOrAfter(getWorkingDays, futureDate)
  }

  private def getWorkingDays(implicit bHS: BankHolidaySet): LocalDate = {
    currentLocalDate plusWorkingDays getDaysInAdvance(getCurrentHour.getHour)
  }

  private def getDaysInAdvance(currentHour: Int)(implicit bHS: BankHolidaySet): Int = {
    if (LocalDateWithHolidays(currentLocalDate).isWorkingDay) {
      if (currentHour >= dayEndHour) 3 else 2
    } else {
      3
    }
  }

  def futureWorkingDate(date: LocalDate, days: Int)(implicit bHS: BankHolidaySet) : String = {
    val futureDate = date.plusWorkingDays(days)
    DateTimeFormatter.ofPattern("dd MM yyyy").format(futureDate)
  }

  def splitDate(date: String): Array[String] = {
    for (i <- date.split("-")) yield i
  }

  def validate(date: String): Boolean = {
    val format = new SimpleDateFormat("yyyy-MM-dd")
    format.setLenient(false)

    Try(format.parse(date)) match {
      case Success(_) => true
      case Failure(_) => false
    }
  }
}
