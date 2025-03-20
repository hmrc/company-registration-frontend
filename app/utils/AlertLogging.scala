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

package utils

import java.time.format.{DateTimeFormatter, TextStyle}
import java.time.{LocalDate, LocalTime, ZoneOffset}
import java.util.Locale

object PagerDutyKeys extends Enumeration {
  val CT_UTR_MISMATCH = Value
}

trait AlertLogging extends Logging {
  protected val loggingDays: String
  protected val loggingTimes: String

  def pagerduty(key: PagerDutyKeys.Value, message: Option[String] = None): Unit = {
    val log = s"${key.toString}${message.fold("")(msg => s" - $msg")}"
    if(inWorkingHours) logger.error(log) else logger.info(log)
  }

  def inWorkingHours: Boolean = isLoggingDay && isBetweenLoggingTimes

  private[utils] def today: String = getTheDay(LocalDate.now(ZoneOffset.UTC))

  def getTheDay(nowDateTime: LocalDate): String =
    nowDateTime.getDayOfWeek.getDisplayName(TextStyle.SHORT, Locale.UK).toUpperCase

  private[utils] def now: LocalTime = getCurrentTime

  def getCurrentTime: LocalTime = LocalTime.now


  private[utils] def isLoggingDay = loggingDays.split(",").contains(today)

  private[utils] def isBetweenLoggingTimes: Boolean = {
    val stringToDate = LocalTime.parse(_: String, DateTimeFormatter.ofPattern("HH:mm:ss"))
    val Array(start, end) = loggingTimes.split("_") map stringToDate
    ((start isBefore now) || (now equals start)) && (now isBefore end)
  }
}