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

import forms.AccountingDatesFormT
import helpers.UnitSpec
import models.AccountingDatesModel
import models.JavaTimeUtils.BankHolidaySet
import models.JavaTimeUtils.DateTimeUtils.currentDateTime
import org.scalatest.prop.TableDrivenPropertyChecks
import play.api.data.Form
import services.{BankHolidays, TimeService}

import java.time.{LocalDate, LocalDateTime}

class SCRSValidatorsSpec extends UnitSpec with TableDrivenPropertyChecks {

  private val testDateTime = LocalDateTime.of(2020, 11, 20, 12, 0)
  private val testDate     = testDateTime.toLocalDate
  class Setup(date: LocalDate = testDate, dateTime: LocalDateTime = testDateTime) {
    val testAccDatesForm: Form[AccountingDatesModel] = new AccountingDatesFormT {
      override val timeService: TimeService = new TimeService {
        override implicit val bHS: BankHolidaySet   = BankHolidays.fetchEnglandAndWalesBankHolidays
        override val dayEndHour: Int                = 14
        override def currentDateTime: LocalDateTime = dateTime
        override def currentLocalDate: LocalDate    = date
        override val getCurrentHour: Int            = currentDateTime.getHour
      }
      override val now: LocalDate = date
    }.form
  }

  private val errorCases = Table(
    ("testErrorDescription", "year", "month", "day", "expectedErrors"),
    ("all date fields are empty", "", "", "", List("page.reg.accountingDates.date.notFound")),
    ("day field is empty", "2017", "11", "", List("page.reg.accountingDates.day.notFound")),
    ("month field is empty", "2017", "", "11", List("page.reg.accountingDates.month.notFound")),
    ("year field is empty", "", "11", "11", List("page.reg.accountingDates.year.notFound")),
    ("date is invalid", "2017", "2", "31", List("page.reg.accountingDates.date.invalid-date")),
    (
      "any date fields are invalid",
      "-0001",
      "32",
      "34",
      List("page.reg.accountingDates.date.invalid-day", "page.reg.accountingDates.date.invalid-month")),
    ("invalid characters in day field", "2017", "2", "asd", List("page.reg.accountingDates.date.invalid-day")),
    ("invalid characters in month field", "2017", "asd", "2", List("page.reg.accountingDates.date.invalid-month")),
    ("invalid characters in year field", "asd", "2", "2", List("page.reg.accountingDates.date.invalid-year")),
    (
      "the date is not at least two days in the future",
      testDate.getYear.toString,
      testDate.getMonthValue.toString,
      (testDate.getDayOfMonth + 1).toString,
      List("page.reg.accountingDates.date.future"))
  )

  "Submitting an Accounting Dates form" should {
    "return an error message" when {
      errorCases.foreach { case (testErrorDescription, year, month, day, expectedErrors) =>
        testErrorDescription in new Setup {
          val data: Map[String, String] = Map(
            "businessStartDate" -> "futureDate",
            "futureDate.Year"   -> year,
            "futureDate.Month"  -> month,
            "futureDate.Day"    -> day
          )

          val boundForm: Form[AccountingDatesModel] = testAccDatesForm.bind(data)
          boundForm.errors.map(_.message) mustBe expectedErrors
        }
      }
      val dateTimeNow = currentDateTime
      val dateNow     = dateTimeNow.toLocalDate
      "the date is over three years in the future" in new Setup(dateNow, dateTimeNow) {
        val data: Map[String, String] = Map(
          "businessStartDate" -> "futureDate",
          "futureDate.Year"   -> (dateNow.getYear + 3).toString,
          "futureDate.Month"  -> dateNow.getMonthValue.toString,
          "futureDate.Day"    -> (dateNow.getDayOfMonth + 1).toString
        )

        val boundForm: Form[AccountingDatesModel] = testAccDatesForm.bind(data)
        boundForm.errors.map(_.message) mustBe List("page.reg.accountingDates.date.future")
      }
    }

    "bind successfully" when {
      "date is less than 3 years in the future" in new Setup() {
        val data: Map[String, String] = Map(
          "businessStartDate" -> "futureDate",
          "futureDate.Year"   -> (testDate.getYear + 3).toString,
          "futureDate.Month"  -> testDate.getMonthValue.toString,
          "futureDate.Day"    -> testDate.getDayOfMonth.toString
        )

        val boundForm: Form[AccountingDatesModel] = testAccDatesForm.bind(data)
        boundForm.hasErrors mustBe false
      }

      "date is 3 working days in future" in new Setup() {
        val data: Map[String, String] = Map(
          "businessStartDate" -> "futureDate",
          "futureDate.Year"   -> testDate.getYear.toString,
          "futureDate.Month"  -> testDate.getMonthValue.toString,
          "futureDate.Day"    -> (testDate.getDayOfMonth + 4).toString
        )

        val boundForm: Form[AccountingDatesModel] = testAccDatesForm.bind(data)
        boundForm.hasErrors mustBe false
      }

      "date is 28th Feb plus 3 years when today's date is 29th Feb" in new Setup(LocalDate.of(2020, 2, 29), LocalDateTime.of(2020, 2, 29, 15, 0)) {
        val data: Map[String, String] = Map(
          "businessStartDate" -> "futureDate",
          "futureDate.Year"   -> "2023",
          "futureDate.Month"  -> "02",
          "futureDate.Day"    -> "28"
        )

        val boundForm: Form[AccountingDatesModel] = testAccDatesForm.bind(data)
        boundForm.hasErrors mustBe false
      }
    }
  }

  "dateRegex" should {
    "validate 2018-01-01" in {
      assert("2018-01-01".matches(SCRSValidators.datePatternRegex))
    }

    "not validate 2018-1-1" in {
      assert(!"2018-1-1".matches(SCRSValidators.datePatternRegex))
    }
  }

  "desSchemaRegex" should {

    "not validate FAKE_SOD::TR9873!^^7FDFNN" in {
      assert(!"FAKE_SOD::TR9873!^^7FDFNN".matches(SCRSValidators.desSessionRegex))
    }

    "validate stubbed-1sds-sdijhi-2383-seei" in {
      assert("stubbed-1sds-sdijhi-2383-seei".matches(SCRSValidators.desSessionRegex))
    }
  }
}
