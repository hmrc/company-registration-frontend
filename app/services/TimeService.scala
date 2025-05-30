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

import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime}
import config.AppConfig

import javax.inject.Inject
import models.JavaTimeUtils.BankHolidays.LocalDateWithHolidays
import models.JavaTimeUtils.DateTimeUtils._
import models.JavaTimeUtils.{BankHoliday, BankHolidaySet}
import utils.SystemDate

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object TimeHelper {
  val DATE_FORMAT = "yyyy-MM-dd"
  def splitDate(date: String): Array[String] = {
    for (i <- date.split("-")) yield i
  }
  def toDateTime(d: Option[String], m: Option[String], y: Option[String]): Option[LocalDate] = {
    if(d.isDefined && m.isDefined && y.isDefined) {
      val (iY, iM, iD) = (y.get.toInt, m.get.toInt, d.get.toInt)
      Some(LocalDate.of(iY, iM, iD))
    } else {
      None
    }
  }

}

class TimeServiceImpl @Inject()(bankHolidaysService: BankHolidaysService, val appConfig: AppConfig) extends TimeService {
  override lazy val dayEndHour      = appConfig.servicesConfig.getConfInt("time-service.day-end-hour", throw new Exception("could not find config key time-service.day-end-hour"))
  override def currentDateTime      = LocalDateTime.now()
  override def currentLocalDate     = SystemDate.getSystemDate
  override lazy val bHS: BankHolidaySet       = bankHolidaysService.fetchBankHolidays()
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
