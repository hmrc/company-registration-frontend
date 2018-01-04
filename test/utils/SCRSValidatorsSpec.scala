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

import forms.{AccountingDatesForm, PPOBForm}
import org.joda.time.LocalDate
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

class SCRSValidatorsSpec extends UnitSpec with WithFakeApplication {

  class Setup {

    val testAccDatesForm = AccountingDatesForm.form
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
  }

}
