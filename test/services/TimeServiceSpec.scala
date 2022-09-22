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

package services

import helpers.UnitSpec
import org.scalatestplus.mockito.MockitoSugar
import java.time._

import models.JavaTimeUtils._

class TimeServiceSpec extends UnitSpec with MockitoSugar {

  implicit val bHSTest: BankHolidaySet = BankHolidays.bankHolidaySet
  val bhRandomDate = BankHoliday(title="testBH", date= LocalDate.of(2000, 10, 10))

  def timeServiceMock(dateTime: LocalDateTime = LocalDateTime.of(2017, 1, 2, 15, 0), dayEnd: Int = 14, bankHolidayDates : List[BankHoliday] = List(bhRandomDate)) = new TimeService {
    override val dayEndHour: Int = dayEnd
    override def currentDateTime: LocalDateTime = dateTime
    override def currentLocalDate: LocalDate = currentDateTime.toLocalDate
    implicit val bHS: BankHolidaySet = BankHolidaySet("england-and-wales", bankHolidayDates)
  }


  "TimeService" should {

    val bhDud = BankHoliday(title="testBH", date= LocalDate.of(2000, 10, 10))
    val bh3rd = BankHoliday(title="testBH", date= LocalDate.of(2017, 1, 3))
    val bh6th = BankHoliday(title="testBH", date= LocalDate.of(2017, 1, 6))
    val bh9th = BankHoliday(title="testBH", date= LocalDate.of(2017, 1, 9))


    // Before 2pm, no bank holiday
    "return true when a date 2 days away is supplied before 2pm and does not conflict with any bank holidays"in {
      val ts = timeServiceMock(LocalDateTime.of(2017, 1, 2, 12, 0), 14, List(bhDud))
      ts.isDateSomeWorkingDaysInFuture(LocalDate.of(2017, 1, 5))(ts.bHS) shouldBe true

    }

    "return false when a date 1 day away is supplied before 2pm and does not conflict with any bank holidays"in {
      val ts = timeServiceMock(LocalDateTime.of(2017, 1, 2, 12, 0), 14, List(bhDud))
        ts.isDateSomeWorkingDaysInFuture(LocalDate.of(2017, 1, 3))(ts.bHS) shouldBe false
    }


    // After 2pm, no bank holiday
    "return true when a date 3 days away is supplied after 2pm and does not conflict with any bank holidays"in {
      val ts = timeServiceMock(LocalDateTime.of(2017, 1, 2, 15, 0), 14, List(bhDud))
        ts.isDateSomeWorkingDaysInFuture(LocalDate.of(2017, 1, 5))(ts.bHS) shouldBe true
    }

    "return false when a date 1 day away is supplied after 2pm and does not conflict with any bank holidays"in {
      val ts = timeServiceMock(LocalDateTime.of(2017, 1, 2, 15, 0), 14, List(bhDud))
        ts.isDateSomeWorkingDaysInFuture(LocalDate.of(2017, 1, 3))(ts.bHS) shouldBe false
    }


    // Before 2pm, bank holiday
    "return true when a date 3 days away is supplied before 2pm and conflicts with one bank holiday" in {
      val ts = timeServiceMock(LocalDateTime.of(2017, 1, 2, 12, 0), 14, List(bh9th))
        ts.isDateSomeWorkingDaysInFuture(LocalDate.of(2017, 1, 5))(ts.bHS) shouldBe true
    }

    "return false when a date 2 days away is supplied before 2pm and conflicts with one bank holiday" in {
      val ts = timeServiceMock(LocalDateTime.of(2017, 1, 2, 12, 0), 14, List(bh3rd))
        ts.isDateSomeWorkingDaysInFuture(LocalDate.of(2017, 1, 4))(ts.bHS) shouldBe false
    }


    // Weekend, no bank holiday
    "return true when a date is a saturday and the date entered is a wednesday and no bank holiday" in {
      val ts = timeServiceMock(LocalDateTime.of(2017, 1, 7, 12, 0), 14, List(bhDud))
        ts.isDateSomeWorkingDaysInFuture(LocalDate.of(2017, 1, 11))(ts.bHS) shouldBe true
    }

    "return true when a date is a saturday and it is submitted before 2pm and the date entered is a tuesday and no bank holiday" in {
      val ts = timeServiceMock(LocalDateTime.of(2017, 1, 7, 12, 0), 14, List(bhDud))
        ts.isDateSomeWorkingDaysInFuture(LocalDate.of(2017, 1, 10))(ts.bHS) shouldBe true
    }


    // Weekend, bank holiday monday
    "return true when a date is a saturday and the date entered is a thursday with a bank holiday monday" in {
      val ts = timeServiceMock(LocalDateTime.of(2017, 1, 7, 12, 0), 14, List(bh9th))
        ts.isDateSomeWorkingDaysInFuture(LocalDate.of(2017, 1, 12))(ts.bHS) shouldBe true
    }

    "return true when a date is a saturday and it is submitted after 2pm and the date entered is a wednesday with a bank holiday monday" in {
      val ts = timeServiceMock(LocalDateTime.of(2017, 1, 7, 15, 0), 14, List(bh9th))
        ts.isDateSomeWorkingDaysInFuture(LocalDate.of(2017, 1, 11))(ts.bHS) shouldBe true
    }


    // Weekend, bank holiday monday, after 2pm
    "return true when a date is a saturday and it is submitted after 2pm and the date entered is a thursday with a bank holiday monday" in {
      val ts = timeServiceMock(LocalDateTime.of(2017, 1, 7, 15, 0), 14, List(bh9th))
        ts.isDateSomeWorkingDaysInFuture(LocalDate.of(2017, 1, 12))(ts.bHS) shouldBe true
    }

    "return false when a date is a saturday and it is submitted after 2pm and the date entered is a wednesday with a bank holiday monday" in {
      val ts = timeServiceMock(LocalDateTime.of(2017, 1, 14, 15, 0), 14, List(bh9th))
        ts.isDateSomeWorkingDaysInFuture(LocalDate.of(2017, 1, 11))(ts.bHS) shouldBe false
    }


    // Thursday, bank holiday friday, bank holiday monday, after 2pm
    "return true when a date is a thursday and it is submitted after 2pm and the date entered is the next thursday with a bank holiday friday and monday" in {
      val ts = timeServiceMock(LocalDateTime.of(2017, 1, 5, 15, 0), 14, List(bh6th, bh9th))
      ts.isDateSomeWorkingDaysInFuture(LocalDate.of(2017, 1, 12))(ts.bHS) shouldBe true
    }

    "return false when a date is a thursday and it is submitted after 2pm and the date entered is the next wednesday with a bank holiday friday and monday" in {
      val ts = timeServiceMock(LocalDateTime.of(2017, 1, 12, 15, 0), 14, List(bh6th, bh9th))
      ts.isDateSomeWorkingDaysInFuture(LocalDate.of(2017, 1, 11))(ts.bHS) shouldBe false
    }


    //Test the future dates
    "return a future date " in {
      timeServiceMock().futureWorkingDate(LocalDate.parse("2016-12-13"), 60)(bHSTest) shouldBe "11 02 2017"
    }
    "return a future date ignoring bank holidays" in {
      timeServiceMock().futureWorkingDate(LocalDate.parse("2019-08-26"), 1)(bHSTest) shouldBe "27 08 2019"
    }
    "return a future date ignoring bank holidays 2 working days in the future" in {
      timeServiceMock().futureWorkingDate(LocalDate.parse("2019-12-24"), 2)(bHSTest) shouldBe "28 12 2019"
    }
  }

  "validate" should {

    "return true when a leap year date is validated" in {
      val leapYearDate = "2016-02-29"

      timeServiceMock().validate(leapYearDate) shouldBe true
    }

    "return false when a non-leap year date is validated" in {
      val leapYearDate = "2017-02-29"

      timeServiceMock().validate(leapYearDate) shouldBe false
    }
  }

  "toDateTime" should {

    val day = "10"
    val month = "11"
    val year = "2017"

    "return a valid datetime" in {
      val expected = LocalDate.of(2017,11,10)
      TimeHelper.toDateTime(Some(day), Some(month), Some(year)).get shouldBe expected
    }

    "return a None when empty dates are supplied" in {
      TimeHelper.toDateTime(None, None, None) shouldBe None
    }

    "return None when any of the date fields supplied is empty" in {
      TimeHelper.toDateTime(None, Some(month), Some(year)) shouldBe None
      TimeHelper.toDateTime(Some(day), None, Some(year)) shouldBe None
      TimeHelper.toDateTime(Some(day), Some(month), None) shouldBe None
    }
    "return a valid datetime on a day light savings date" in {
      val expected = Some(LocalDate.of(2020,10,22))
      TimeHelper.toDateTime(Some("22"), Some("10"), Some("2020")) shouldBe expected
    }
  }
}