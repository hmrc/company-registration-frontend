/*
 * Copyright 2017 HM Revenue & Customs
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

import org.joda.time.{DateTime, LocalDate}
import org.joda.time.format.DateTimeFormat
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.time.DateTimeUtils._
import uk.gov.hmrc.time.workingdays.{BankHoliday, BankHolidaySet, LocalDateWithHolidays}

import scala.util.{Failure, Success, Try}

object TimeService extends TimeService with ServicesConfig {
  override lazy val dayEndHour = getConfInt("time-service.day-end-hour", throw new Exception("could not find config key time-service.day-end-hour"))
  override def currentDateTime = DateTime.now
  override def currentLocalDate = LocalDate.now
  override val bHS: BankHolidaySet = BankHolidaySet("england-and-wales", List(
    BankHoliday(title = "Good Friday", date = new LocalDate(2017, 4, 14)),
    BankHoliday(title = "Easter Monday", date = new LocalDate(2017, 4, 17)),
    BankHoliday(title = "Early May bank holiday", date = new LocalDate(2017, 5, 1)),
    BankHoliday(title = "Spring bank holiday", date = new LocalDate(2017, 5, 29)),
    BankHoliday(title = "Summer bank holiday", date = new LocalDate(2017, 8, 28)),
    BankHoliday(title = "Christmas Day", date = new LocalDate(2017, 12, 25)),
    BankHoliday(title = "Boxing Day", date = new LocalDate(2017, 12, 26)),
    BankHoliday(title = "New Year's Day", date = new LocalDate(2018, 1, 1))
  ))
}

trait TimeService {

  val DATE_FORMAT = "yyyy-MM-dd"

  val bHS: BankHolidaySet

  val dayEndHour: Int

  def currentDateTime: DateTime

  def currentLocalDate: LocalDate

  def isDateSomeWorkingDaysInFuture(futureDate: LocalDate)(implicit bHS: BankHolidaySet): Boolean = {
    isEqualOrAfter(getWorkingDays, futureDate)
  }

  private def getWorkingDays(implicit bHS: BankHolidaySet): LocalDate = {
    currentLocalDate plusWorkingDays getDaysInAdvance(currentDateTime.getHourOfDay())
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
    DateTimeFormat.forPattern("dd MM yyyy").print(futureDate)
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

  def toDateTime(d: Option[String], m: Option[String], y: Option[String]): Option[DateTime] = {
    d.isDefined && m.isDefined && y.isDefined match {
      case false => None
      case true =>
        val (iY, iM, iD) = (y.get.toInt, m.get.toInt, d.get.toInt)
        Some(new DateTime(iY, iM, iD, 0, 0))
    }
  }
}
