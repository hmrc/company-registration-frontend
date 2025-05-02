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

import helpers.UnitSpec
import models.JavaTimeUtils.DateTimeUtils.{currentDate, currentDateTime}
import models.JavaTimeUtils.{BankHoliday, BankHolidaySet}
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatestplus.mockito.MockitoSugar

import java.time.Month.{APRIL, JUNE, MARCH, MAY}
import java.time._

class TimeServiceSpec extends UnitSpec with MockitoSugar with TableDrivenPropertyChecks {

  private val testYear      = 2020
  private val testDateTime  = currentDateTime.withYear(testYear)
  def bhSet: BankHolidaySet = BankHolidays.fetchEnglandAndWalesBankHolidays
  def realBankHolidays: BankHolidaySet =
    bhSet.copy(events = bhSet.events.map(bh => bh.copy(date = bh.date.withYear(testYear))))

  def dateTimeOfJanuary(day: Int, hour: Int): LocalDateTime = LocalDateTime.of(testYear, 1, day, hour, 0)
  def dateOfJanuary(day: Int): LocalDate                    = LocalDate.of(testYear, 1, day)
  def fakeBankHoliday(januaryDay: Int): BankHoliday =
    BankHoliday(title = "testBH", date = dateOfJanuary(januaryDay))

  def timeServiceMock(currentDate: LocalDateTime, endOfDay: Int = 17, bankHolidayDates: List[BankHoliday] = realBankHolidays.events): TimeService =
    new TimeService {
      override val dayEndHour: Int                = endOfDay
      override def currentDateTime: LocalDateTime = currentDate
      override def currentLocalDate: LocalDate    = currentDateTime.toLocalDate
      override val getCurrentHour: Int            = currentDateTime.getHour
      implicit val bHS: BankHolidaySet            = BankHolidaySet("england-and-wales", bankHolidayDates)
    }

  "isDateAtLeastThreeWorkingDaysInFuture" should {

    val successCases = Table(
      ("testDescription", "currentDate", "currentTime", "dateToTest", "bankHolidays"),
      ("future date is more than 3 working days in future", 6, 12, 20, List.empty),
      ("future date is 3 days in future, when including today if working day has not ended", 6, 12, 8, List.empty),
      ("future date is 3 working days in future when accounting for weekends", 3, 12, 7, List.empty),
      ("future date is 3 working days in future when accounting for bank holidays", 6, 12, 9, List(fakeBankHoliday(9)))
    )
    "return true" when {
      successCases.foreach { case (testDescription, currentDate, currentTime, dateToTest, bankHolidays) =>
        testDescription in {
          val ts = timeServiceMock(dateTimeOfJanuary(day = currentDate, hour = currentTime), endOfDay = 17, bankHolidays)

          ts.isDateAtLeastThreeWorkingDaysInFuture(dateOfJanuary(dateToTest))(ts.bHS) mustBe true
        }
      }
    }

    val failureCases = Table(
      ("testDescription", "currentDate", "currentTime", "dateToTest", "bankHolidays"),
      ("future date is less than 3 working days in the future", 6, 12, 7, List.empty),
      ("future date is not 3 days in future, because today is not a working day so today is excluded", 6, 12, 8, List(fakeBankHoliday(6))),
      ("future date is not 3 days in future, because today's working hours have ended so today is excluded", 6, 20, 8, List.empty)
    )
    "return false" when {
      failureCases.foreach { case (testDescription, currentDate, currentTime, dateToTest, bankHolidays) =>
        testDescription in {
          val ts = timeServiceMock(dateTimeOfJanuary(day = currentDate, hour = currentTime), endOfDay = 17, bankHolidays)

          ts.isDateAtLeastThreeWorkingDaysInFuture(dateOfJanuary(dateToTest))(ts.bHS) mustBe false
        }
      }
    }
  }

  "futureWorkingDate" should {
    "return a formatted date the specified number of days in the future, skipping over bank holidays and weekends" in {
      timeServiceMock(testDateTime).futureWorkingDate(LocalDate.parse(s"$testYear-12-24"), 1)(realBankHolidays) mustBe s"29 12 $testYear"
    }
  }

  "validate" should {
    "return true" when {
      "a valid date is validated" in {
        val validDate = "2016-02-12"

        timeServiceMock(testDateTime).validate(validDate) mustBe true
      }
      "Feb 29th on a leap year date is validated" in {
        val validLeapYearDate = "2016-02-29"

        timeServiceMock(testDateTime).validate(validLeapYearDate) mustBe true
      }
    }

    "return false" when {
      "an invalid date is validated" in {
        val invalidDate = "2016-40-40"

        timeServiceMock(testDateTime).validate(invalidDate) mustBe false
      }
      "Feb 29th on a non leap year date is validated" in {
        val validLeapYearDate = "2017-02-29"

        timeServiceMock(testDateTime).validate(validLeapYearDate) mustBe false
      }
    }
  }

  "toDateTime" should {
    val day   = "10"
    val month = "11"
    val year  = testYear.toString

    "return a valid datetime" in {
      val expected = LocalDate.of(testYear, 11, 10)
      TimeHelper.toDateTime(Some(day), Some(month), Some(year)).get mustBe expected
    }
    "return a None when empty dates are supplied" in {
      TimeHelper.toDateTime(None, None, None) mustBe None
    }
    "return None when any of the date fields supplied is empty" in {
      TimeHelper.toDateTime(None, Some(month), Some(year)) mustBe None
      TimeHelper.toDateTime(Some(day), None, Some(year)) mustBe None
      TimeHelper.toDateTime(Some(day), Some(month), None) mustBe None
    }
    "return a valid datetime on a day light savings date" in {
      val expected = Some(LocalDate.of(testYear, 10, 22))
      TimeHelper.toDateTime(Some("22"), Some("10"), Some(testYear.toString)) mustBe expected
    }
  }

  private val nineYearsAroundToday         = (currentDate.getYear - 6 to currentDate.getYear + 2).toList
  private val standardNumberOfBankHolidays = 8
  private val standardTitles = Set(
    "New Year’s Day",
    "Good Friday",
    "Easter Monday",
    "Early May bank holiday",
    "Spring bank holiday",
    "Summer bank holiday",
    "Christmas Day",
    "Boxing Day"
  )

  "BankHolidays.fetchEnglandAndWalesBankHolidays" should {
    "fetch all 8 English and Welsh bank holidays from (year - 6) to (year + 2) inclusive" which {
      val nineYearsOfBankHolidays = nineYearsAroundToday.flatMap(bankHolidaysForYear)
      val expectedHolidays        = BankHolidaySet("england-and-wales", nineYearsOfBankHolidays)
      val actualHolidays          = BankHolidays.fetchEnglandAndWalesBankHolidays

      "are for the right division" in {
        actualHolidays.division mustBe "england-and-wales"
      }

      "has the correct nine years" in {
        actualHolidays.events.map(_.date.getYear).distinct.sorted mustBe nineYearsAroundToday
      }

      "each year has the correct number of standard bank holidays (allowing for one off extras e.g. Jubilee)" in {
        val eventsByYear = actualHolidays.events.groupBy(_.date.getYear)

        eventsByYear.foreach { case (year, holidays) =>
          val numberOfHolidaysInYear = holidays.length
          assert(
            numberOfHolidaysInYear >= standardNumberOfBankHolidays && numberOfHolidaysInYear < standardNumberOfBankHolidays + 4,
            s"\nFor year $year: expected between $standardNumberOfBankHolidays and ${standardNumberOfBankHolidays + 4}" +
              s" holidays, but got $numberOfHolidaysInYear\n"
          )
        }
      }

      "each (standard) bank holiday matches expected title and date (ignoring day due to variance around weekends)" in {
        expectedHolidays.events.foreach { expectedHoliday =>
          val maybeMatchingHoliday: Option[BankHoliday] = actualHolidays.events.find { actualHoliday =>
            val actualDate = actualHoliday.date

            isStandardHoliday(actualHoliday.title) &&
            actualHoliday.title.contains(expectedHoliday.title) &&
            actualDate.getYear == expectedHoliday.date.getYear &&
            (expectedHoliday.title match {
              case "Good Friday" | "Easter Monday" => actualDate.getMonth == MARCH || actualDate.getMonth == APRIL
              case "Spring bank holiday"           => actualDate.getMonth == MAY || actualDate.getMonth == JUNE
              case _                               => actualDate.getMonth == expectedHoliday.date.getMonth
            })
          }

          withClue(s"\nNo match found for expected holiday: $expectedHoliday\n") {
            maybeMatchingHoliday must not be empty
          }
        }
      }
    }
  }

  def isStandardHoliday(title: String): Boolean = standardTitles.exists(title.contains(_))
  def bankHolidaysForYear(year: Int): List[BankHoliday] = List(
    BankHoliday("New Year’s Day", LocalDate.of(year, 1, 1)),
    BankHoliday("Good Friday", LocalDate.of(year, 4, 10)),
    BankHoliday("Easter Monday", LocalDate.of(year, 4, 13)),
    BankHoliday("Early May bank holiday", LocalDate.of(year, 5, 8)),
    BankHoliday("Spring bank holiday", LocalDate.of(year, 5, 25)),
    BankHoliday("Summer bank holiday", LocalDate.of(year, 8, 31)),
    BankHoliday("Christmas Day", LocalDate.of(year, 12, 25)),
    BankHoliday("Boxing Day", LocalDate.of(year, 12, 28))
  )

}
