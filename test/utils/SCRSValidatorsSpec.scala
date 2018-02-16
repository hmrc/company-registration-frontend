/*
 * Copyright 2018 HM Revenue & Customs
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

import forms.AccountingDatesForm
import org.joda.time.{DateTime, LocalDate}
import services.{BankHolidays, TimeService}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import uk.gov.hmrc.time.workingdays.BankHolidaySet

class SCRSValidatorsSpec extends UnitSpec with WithFakeApplication {

  class Setup(newnow : LocalDate = LocalDate.now(), dateTime : DateTime = DateTime.now()) {
    val testAccDatesForm = new AccountingDatesForm {
      override val timeService: TimeService = new TimeService {
        override val bHS: BankHolidaySet = BankHolidays.bankHolidaySet
        override val dayEndHour: Int = 14
        override def currentDateTime: DateTime = dateTime
        override def currentLocalDate: LocalDate = newnow
      }
      override val now: LocalDate = newnow
    }.form
  }

  "Submitting a Accounting Dates form" should {
    "return an error message if any of the date fields are empty" in new Setup {
      val data: Map[String, String] = Map(
        "businessStartDate" -> "futureDate",
        "businessStartDate-futureDate.year" -> "",
        "businessStartDate-futureDate.month" -> "",
        "businessStartDate-futureDate.day" -> "")
      val boundForm = testAccDatesForm.bind(data)
      boundForm.errors.map(_.message) shouldBe List("page.reg.accountingDates.date.notFound")

    }
  }

  "Submitting a Accounting Dates form with month and day fields are invalid" should {
    "return an error message if month and day fields are invalid" in new Setup {
      val data: Map[String, String] = Map(
        "businessStartDate" -> "futureDate",
        "businessStartDate-futureDate.year" -> "0001",
        "businessStartDate-futureDate.month" -> "32",
        "businessStartDate-futureDate.day" -> "34")
      val boundForm = testAccDatesForm.bind(data)
      boundForm.errors.map(_.message) shouldBe List("page.reg.accountingDates.date.invalid-day", "page.reg.accountingDates.date.invalid-month")
    }
  }

  "Submitting a Accounting Dates form with empty day field" should {
    "return an error message if day field is empty" in new Setup {
      val data: Map[String, String] = Map(
        "businessStartDate" -> "futureDate",
        "businessStartDate-futureDate.year" -> "2017",
        "businessStartDate-futureDate.month" -> "11",
        "businessStartDate-futureDate.day" -> "")
      val boundForm = testAccDatesForm.bind(data)
      boundForm.errors.map(_.message) shouldBe List("page.reg.accountingDates.day.notFound")
    }
  }


  "Submitting a Accounting Dates form with empty month field" should {
    "return an error message if month field is empty" in new Setup {
      val data: Map[String, String] = Map(
        "businessStartDate" -> "futureDate",
        "businessStartDate-futureDate.year" -> "2017",
        "businessStartDate-futureDate.month" -> "",
        "businessStartDate-futureDate.day" -> "16")
      val boundForm = testAccDatesForm.bind(data)
      boundForm.errors.map(_.message) shouldBe List("page.reg.accountingDates.month.notFound")
    }
  }


  "Submitting a Accounting Dates form with empty year field" should {
    "return an error message if month field is empty" in new Setup {
      val data: Map[String, String] = Map(
        "businessStartDate" -> "futureDate",
        "businessStartDate-futureDate.year" -> "",
        "businessStartDate-futureDate.month" -> "11",
        "businessStartDate-futureDate.day" -> "16")
      val boundForm = testAccDatesForm.bind(data)
      boundForm.errors.map(_.message) shouldBe List("page.reg.accountingDates.year.notFound")
    }
  }

  "Submitting a Accounting Dates form with invalid date" should {
    "return an error message if date is invalid" in new Setup {
      val data: Map[String, String] = Map(
        "businessStartDate" -> "futureDate",
        "businessStartDate-futureDate.year" -> "2017",
        "businessStartDate-futureDate.month" -> "2",
        "businessStartDate-futureDate.day" -> "31")
      val boundForm = testAccDatesForm.bind(data)
      boundForm.errors.map(_.message) shouldBe List("page.reg.accountingDates.date.invalid-date")
    }
  }


  "Submitting a Accounting Dates form with invalid characters in day field" should {
    "return an error message if day field has characters" in new Setup {
      val data: Map[String, String] = Map(
        "businessStartDate" -> "futureDate",
        "businessStartDate-futureDate.year" -> "2017",
        "businessStartDate-futureDate.month" -> "2",
        "businessStartDate-futureDate.day" -> "jg")
      val boundForm = testAccDatesForm.bind(data)
      boundForm.errors.map(_.message) shouldBe List("page.reg.accountingDates.date.invalid-day")
    }
  }


  "Submitting a Accounting Dates form with invalid year field" should {
    "return an error message if year is invalid" in new Setup {
      val data: Map[String, String] = Map(
        "businessStartDate" -> "futureDate",
        "businessStartDate-futureDate.year" -> "jklj",
        "businessStartDate-futureDate.month" -> "2",
        "businessStartDate-futureDate.day" -> "8")
      val boundForm = testAccDatesForm.bind(data)
      boundForm.errors.map(_.message) shouldBe List("page.reg.accountingDates.date.invalid-year")
    }
  }

  "Submitting a Accounting Dates form with invalid month field" should {
    "return an error message if month field is invalid" in new Setup {
      val data: Map[String, String] = Map(
        "businessStartDate" -> "futureDate",
        "businessStartDate-futureDate.year" -> "2017",
        "businessStartDate-futureDate.month" -> "21",
        "businessStartDate-futureDate.day" -> "1")
      val boundForm = testAccDatesForm.bind(data)
      boundForm.errors.map(_.message) shouldBe List("page.reg.accountingDates.date.invalid-month")
    }

    "return an error message if the date is not at least two days in the future" in new Setup {
      val data: Map[String, String] = Map(
        "businessStartDate" -> "futureDate",
        "businessStartDate-futureDate.year" -> (LocalDate.now().getYear - 1).toString,
        "businessStartDate-futureDate.month" -> "12",
        "businessStartDate-futureDate.day" -> "23")

      val boundForm = testAccDatesForm.bind(data)
      boundForm.errors.map(err => (err.args.head, err.message)) shouldBe List(("notFuture", "page.reg.accountingDates.date.future"))
    }

    "return an error message if the date is more than three years in the future" in new Setup {
      val data: Map[String, String] = Map(
        "businessStartDate" -> "futureDate",
        "businessStartDate-futureDate.year" -> (LocalDate.now().getYear + 4).toString,
        "businessStartDate-futureDate.month" -> "12",
        "businessStartDate-futureDate.day" -> "23")

      val boundForm = testAccDatesForm.bind(data)
      boundForm.errors.map(err => (err.args.head, err.message)) shouldBe List(("notFuture", "page.reg.accountingDates.date.future"))
    }

    "return an error message if the date is 1st March plus 3 years when todays date is 29th Feb" in new Setup(LocalDate.parse("2020-02-29")) {
      val data: Map[String, String] = Map(
        "businessStartDate" -> "futureDate",
        "businessStartDate-futureDate.year" -> "2023",
        "businessStartDate-futureDate.month" -> "03",
        "businessStartDate-futureDate.day" -> "1")

      val boundForm = testAccDatesForm.bind(data)
      boundForm.errors.map(err => (err.args.head, err.message)) shouldBe List(("notFuture", "page.reg.accountingDates.date.future"))
    }

    "return an error message if the day is just before 3 working days" in new Setup(LocalDate.parse("2018-02-09"), DateTime.parse("2018-02-09T15:00:00Z")) {
      val data: Map[String, String] = Map(
        "businessStartDate" -> "futureDate",
        "businessStartDate-futureDate.year" -> "2018",
        "businessStartDate-futureDate.month" -> "02",
        "businessStartDate-futureDate.day" -> "13"
      )

      val boundForm = testAccDatesForm.bind(data)
      boundForm.errors.map(err => (err.args.head, err.message)) shouldBe List(("notFuture", "page.reg.accountingDates.date.future"))
    }
  }

  "Submitting a Accounting Dates form with valid data" should {
    "bind successfully if the day is just before 3 years in the future" in new Setup {
      val data: Map[String, String] = Map(
        "businessStartDate" -> "futureDate",
        "businessStartDate-futureDate.year" -> (LocalDate.now().getYear + 3).toString,
        "businessStartDate-futureDate.month" -> LocalDate.now().getMonthOfYear.toString,
        "businessStartDate-futureDate.day" -> LocalDate.now().getDayOfMonth.toString)
      val boundForm = testAccDatesForm.bind(data)
      boundForm.hasErrors shouldBe false
    }

    "bind successfully if the day is just after 3 working days" in new Setup(LocalDate.parse("2018-02-09"), DateTime.parse("2018-02-09T15:00:00Z")) {
      val data: Map[String, String] = Map(
        "businessStartDate" -> "futureDate",
        "businessStartDate-futureDate.year" -> "2018",
        "businessStartDate-futureDate.month" -> "02",
        "businessStartDate-futureDate.day" -> "14"
      )

      val boundForm = testAccDatesForm.bind(data)
      boundForm.hasErrors shouldBe false
    }

    "bind successfully if the date is 28th Feb plus 3 years when todays date is 29th Feb" in new Setup(LocalDate.parse("2020-02-29"), DateTime.parse("2020-02-29T15:00:00Z")) {
      val data: Map[String, String] = Map(
        "businessStartDate" -> "futureDate",
        "businessStartDate-futureDate.year" -> "2023",
        "businessStartDate-futureDate.month" -> "02",
        "businessStartDate-futureDate.day" -> "28"
      )

      val boundForm = testAccDatesForm.bind(data)
      boundForm.hasErrors shouldBe false
    }
  }

}
