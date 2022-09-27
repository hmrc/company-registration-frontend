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

package utils

import forms.AccountingDatesFormT
import helpers.UnitSpec
import java.time.{LocalDate, LocalDateTime, LocalTime, ZonedDateTime}

import models.JavaTimeUtils.BankHolidaySet
import services.{BankHolidays, TimeService}

class SCRSValidatorsSpec extends UnitSpec {

  class Setup(newnow : LocalDate = LocalDate.now(), dateTime : LocalDateTime = LocalDateTime.now()) {
    val testAccDatesForm = new AccountingDatesFormT {
      override lazy val bHS = BankHolidays.bankHolidaySet
      override val timeService: TimeService = new TimeService {
        override implicit val bHS: BankHolidaySet = BankHolidays.bankHolidaySet
        override val dayEndHour: Int = 14
        override def currentDateTime: LocalDateTime = dateTime
        override def currentLocalDate: LocalDate = newnow
      }
      override val now: LocalDate = newnow
    }.form
  }

  "Submitting a Accounting Dates form" should {
    "return an error message if any of the date fields are empty" in new Setup {
      val data: Map[String, String] = Map(
        "businessStartDate" -> "futureDate",
        "futureDate.Year" -> "",
        "futureDate.Month" -> "",
        "futureDate.Day" -> "")
      val boundForm = testAccDatesForm.bind(data)
      boundForm.errors.map(_.message) mustBe List("page.reg.accountingDates.date.notFound")

    }
  }

  "Submitting a Accounting Dates form with month and day fields are invalid" should {
    "return an error message if month and day fields are invalid" in new Setup {
      val data: Map[String, String] = Map(
        "businessStartDate" -> "futureDate",
        "futureDate.Year" -> "0001",
        "futureDate.Month" -> "32",
        "futureDate.Day" -> "34")
      val boundForm = testAccDatesForm.bind(data)
      boundForm.errors.map(_.message) mustBe List("page.reg.accountingDates.date.invalid-day", "page.reg.accountingDates.date.invalid-month")
    }
  }

  "Submitting a Accounting Dates form with empty day field" should {
    "return an error message if day field is empty" in new Setup {
      val data: Map[String, String] = Map(
        "businessStartDate" -> "futureDate",
        "futureDate.Year" -> "2017",
        "futureDate.Month" -> "11",
        "futureDate.Day" -> "")
      val boundForm = testAccDatesForm.bind(data)
      boundForm.errors.map(_.message) mustBe List("page.reg.accountingDates.day.notFound")
    }
  }


  "Submitting a Accounting Dates form with empty month field" should {
    "return an error message if month field is empty" in new Setup {
      val data: Map[String, String] = Map(
        "businessStartDate" -> "futureDate",
        "futureDate.Year" -> "2017",
        "futureDate.Month" -> "",
        "futureDate.Day" -> "16")
      val boundForm = testAccDatesForm.bind(data)
      boundForm.errors.map(_.message) mustBe List("page.reg.accountingDates.month.notFound")
    }
  }


  "Submitting a Accounting Dates form with empty year field" should {
    "return an error message if month field is empty" in new Setup {
      val data: Map[String, String] = Map(
        "businessStartDate" -> "futureDate",
        "futureDate.Year" -> "",
        "futureDate.Month" -> "11",
        "futureDate.Day" -> "16")
      val boundForm = testAccDatesForm.bind(data)
      boundForm.errors.map(_.message) mustBe List("page.reg.accountingDates.year.notFound")
    }
  }

  "Submitting a Accounting Dates form with invalid date" should {
    "return an error message if date is invalid" in new Setup {
      val data: Map[String, String] = Map(
        "businessStartDate" -> "futureDate",
        "futureDate.Year" -> "2017",
        "futureDate.Month" -> "2",
        "futureDate.Day" -> "31")
      val boundForm = testAccDatesForm.bind(data)
      boundForm.errors.map(_.message) mustBe List("page.reg.accountingDates.date.invalid-date")
    }
  }


  "Submitting a Accounting Dates form with invalid characters in day field" should {
    "return an error message if day field has characters" in new Setup {
      val data: Map[String, String] = Map(
        "businessStartDate" -> "futureDate",
        "futureDate.Year" -> "2017",
        "futureDate.Month" -> "2",
        "futureDate.Day" -> "jg")
      val boundForm = testAccDatesForm.bind(data)
      boundForm.errors.map(_.message) mustBe List("page.reg.accountingDates.date.invalid-day")
    }
  }


  "Submitting a Accounting Dates form with invalid year field" should {
    "return an error message if year is invalid" in new Setup {
      val data: Map[String, String] = Map(
        "businessStartDate" -> "futureDate",
        "futureDate.Year" -> "jklj",
        "futureDate.Month" -> "2",
        "futureDate.Day" -> "8")
      val boundForm = testAccDatesForm.bind(data)
      boundForm.errors.map(_.message) mustBe List("page.reg.accountingDates.date.invalid-year")
    }
  }

  "Submitting a Accounting Dates form with invalid month field" should {
    "return an error message if month field is invalid" in new Setup {
      val data: Map[String, String] = Map(
        "businessStartDate" -> "futureDate",
        "futureDate.Year" -> "2017",
        "futureDate.Month" -> "21",
        "futureDate.Day" -> "1")
      val boundForm = testAccDatesForm.bind(data)
      boundForm.errors.map(_.message) mustBe List("page.reg.accountingDates.date.invalid-month")
    }

    "return an error message if the date is not at least two days in the future" in new Setup {
      val data: Map[String, String] = Map(
        "businessStartDate" -> "futureDate",
        "futureDate.Year" -> (LocalDate.now().getYear - 1).toString,
        "futureDate.Month" -> "12",
        "futureDate.Day" -> "23")

      val boundForm = testAccDatesForm.bind(data)
      boundForm.errors.map(err => (err.args.head, err.message)) mustBe List(("futureDate.Day", "page.reg.accountingDates.date.future"))
    }

    "return an error message if the date is more than three years in the future" in new Setup {
      val data: Map[String, String] = Map(
        "businessStartDate" -> "futureDate",
        "futureDate.Year" -> (LocalDate.now().getYear + 4).toString,
        "futureDate.Month" -> "12",
        "futureDate.Day" -> "23")

      val boundForm = testAccDatesForm.bind(data)
      boundForm.errors.map(err => (err.args.head, err.message)) mustBe List(("futureDate.Day", "page.reg.accountingDates.date.future"))
    }

    "return an error message if the date is 1st March plus 3 years when todays date is 29th Feb" in new Setup(LocalDate.parse("2020-02-29")) {
      val data: Map[String, String] = Map(
        "businessStartDate" -> "futureDate",
        "futureDate.Year" -> "2023",
        "futureDate.Month" -> "03",
        "futureDate.Day" -> "1")

      val boundForm = testAccDatesForm.bind(data)
      boundForm.errors.map(err => (err.args.head, err.message)) mustBe List(("futureDate.Day","page.reg.accountingDates.date.invalid-date"))
    }

    "return an error message if the day is just before 3 working days" in new Setup(LocalDate.of(18,2,9), LocalDateTime.of(18,2,9,15,0)) {
      val data: Map[String, String] = Map(
        "businessStartDate" -> "futureDate",
        "futureDate.Year" -> "2018",
        "futureDate.Month" -> "02",
        "futureDate.Day" -> "13"
      )

      val boundForm = testAccDatesForm.bind(data)
      boundForm.errors.map(err => (err.args.head, err.message)) mustBe List(("futureDate.Day", "page.reg.accountingDates.date.future"))
    }
  }

  "Submitting a Accounting Dates form with valid data" should {
    "bind successfully if the day is just before 3 years in the future" in new Setup(LocalDate.of(2020,2,29), LocalDateTime.of(2020,2,29,15,0)) {
      val data: Map[String, String] = Map(
        "businessStartDate" -> "futureDate",
        "futureDate.Year" -> "2023",
        "futureDate.Month" -> "02",
        "futureDate.Day" -> "28")
      val boundForm = testAccDatesForm.bind(data)
      boundForm.hasErrors mustBe false
    }

    "bind successfully if the day is just after 3 working days" in new Setup(LocalDate.of(2018, 2 ,9), LocalDateTime.of(2018,2,9,15,0)) {
      val data: Map[String, String] = Map(
        "businessStartDate" -> "futureDate",
        "futureDate.Year" -> "2018",
        "futureDate.Month" -> "02",
        "futureDate.Day" -> "14"
      )

      val boundForm = testAccDatesForm.bind(data)
      boundForm.hasErrors mustBe false
    }

    "bind successfully if the date is 28th Feb plus 3 years when todays date is 29th Feb" in new Setup(LocalDate.of(2020,2,29), LocalDateTime.of(2020,2,29,15,0)) {
      val data: Map[String, String] = Map(
        "businessStartDate" -> "futureDate",
        "futureDate.Year" -> "2023",
        "futureDate.Month" -> "02",
        "futureDate.Day" -> "28"
      )

      val boundForm = testAccDatesForm.bind(data)
      boundForm.hasErrors mustBe false
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
