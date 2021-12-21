/*
 * Copyright 2021 HM Revenue & Customs
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

package forms

import helpers.UnitSpec
import org.joda.time.{DateTime, LocalDate}
import services.{BankHolidays, TimeService}
import uk.gov.hmrc.time.workingdays.BankHolidaySet

class AccountingDatesFormSpec extends UnitSpec {

  val curDateTime = DateTime.parse("2022-02-28T08:00")
  val curLocalDate = LocalDate.parse("2022-02-28")

  def testForm(newnow : LocalDate = curLocalDate) = new AccountingDatesFormT {
    override val timeService: TimeService = new TimeService {
      override val bHS: BankHolidaySet = BankHolidays.bankHolidaySet
      override val dayEndHour: Int = 14
      override def currentDateTime: DateTime = curDateTime
      override def currentLocalDate: LocalDate = curLocalDate
    }
    override val now: LocalDate = newnow
  }.form

  val currentYear = LocalDate.now().getYear
  val futureYear = (2022 + 1).toString
  val pastYear = (2022 - 1).toString

  val invalidBusinessStartDateData = Map(
    "businessStartDate" -> "",
    "businessStartDate-futureDate.year" -> "",
    "businessStartDate-futureDate.month" -> "",
    "businessStartDate-futureDate.day" -> "")

  val invalidDateData = Map(
    "businessStartDate" -> "futureDate",
    "businessStartDate-futureDate.year" -> "2010",
    "businessStartDate-futureDate.month" -> "99",
    "businessStartDate-futureDate.day" -> "99")

  val whenRegisteredData = Map(
    "businessStartDate" -> "whenRegistered",
    "businessStartDate-futureDate.year" -> "",
    "businessStartDate-futureDate.month" -> "",
    "businessStartDate-futureDate.day" -> "")

  val futureDateData = Map(
    "businessStartDate" -> "futureDate",
    "businessStartDate-futureDate.year" -> futureYear,
    "businessStartDate-futureDate.month" -> "03",
    "businessStartDate-futureDate.day" -> "05")

  val pastDateData = Map(
    "businessStartDate" -> "futureDate",
    "businessStartDate-futureDate.year" -> pastYear,
    "businessStartDate-futureDate.month" -> "03",
    "businessStartDate-futureDate.day" -> "04")

  val notPlanningToYetdata = Map(
    "businessStartDate" -> "whenRegistered",
    "businessStartDate-futureDate.year" -> "",
    "businessStartDate-futureDate.month" -> "",
    "businessStartDate-futureDate.day" -> "")

  "Creating a form using an empty model" should {
    "return an empty string for amount" in {
      testForm().data.isEmpty shouldBe true
    }
  }

  "Creating a form with a valid post" when {
    "selecting when the CRN is received" should {
      val boundForm = testForm().bind(whenRegisteredData)
      "Have no errors" in {
        boundForm.hasErrors shouldBe false
      }
    }

    "selecting 'Start my business on a future date' and inputting said date" should {

      val boundForm = testForm().bind(futureDateData)
      "have no errors when a date further than 3 working days in the future is provided" in {
        boundForm.hasErrors shouldBe false
      }
      val boundForm2 = testForm().bind(pastDateData)
      "have errors when a date less than than 3 working days in the future is provided" in {
        boundForm2.hasErrors shouldBe true
      }
    }
  }

  "Creating a form with an invalid post" when {
    "having no business start date flag" should {
      val boundForm = testForm().bind(invalidBusinessStartDateData)
      "have errors" in {
        boundForm.errors.map(_.key) shouldBe List("businessStartDate")
      }
    }

    "entering an invalid date" should {
      val boundForm = testForm().bind(invalidDateData)
      "have errors" in {
        boundForm.hasErrors shouldBe true
      }
    }
  }
}
