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

package fixtures

import models.AccountingDatesModel
import org.joda.time.LocalDate

trait AccountingDatesFixture {

  lazy val validAccountingDatesModelCRN = AccountingDatesModel("Yes", None, None, None)
  lazy val validAccountingDatesModelFutureDate = AccountingDatesModel("No", Some("2019"), Some("1"), Some("1"))

  val currentYear = LocalDate.now().getYear
  val futureYear = (currentYear + 1).toString
  val pastYear = (currentYear - 1).toString

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
    "businessStartDate-futureDate.month" -> "12",
    "businessStartDate-futureDate.day" -> "23")

  val pastDateData = Map(
    "businessStartDate" -> "futureDate",
    "businessStartDate-futureDate.year" -> pastYear,
    "businessStartDate-futureDate.month" -> "12",
    "businessStartDate-futureDate.day" -> "23")

  val notPlanningToYetdata = Map(
    "businessStartDate" -> "notPlanningToYet",
    "businessStartDate-futureDate.year" -> "",
    "businessStartDate-futureDate.month" -> "",
    "businessStartDate-futureDate.day" -> "")

  lazy val validAccountingDatesFormDataCRN = Seq(
    "crnDate" -> "Yes",
    "year" -> "",
    "month" -> "",
    "day" -> "")

  lazy val validAccountingDatesFormDataFutureDate = Seq(
    "crnDate" -> "No",
    "year" -> "11",
    "month" -> "12",
    "day" -> "2016")

  lazy val invalidAccountingDatesFormDataEmptyCRN = Seq(
    "crnDate" -> "",
    "year" -> "12",
    "month" -> "11",
    "day" -> "2019")

  lazy val invalidAccountingDatesFormDataDay = Seq(
    "crnDate" -> "No",
    "year" -> "999",
    "month" -> "11",
    "day" -> "2019")

  lazy val invalidAccountingDatesFormDataMonth = Seq(
    "crnDate" -> "No",
    "year" -> "10",
    "month" -> "999",
    "day" -> "2019")

  lazy val invalidAccountingDatesFormDataYear = Seq(
    "crnDate" -> "No",
    "year" -> "10",
    "month" -> "12",
    "day" -> "1")

  lazy val invalidAccountingDatesFormDataDate = Seq(
    "crnDate" -> "No",
    "year" -> "aaa",
    "month" -> "bbb",
    "day" -> "ccc")
}
