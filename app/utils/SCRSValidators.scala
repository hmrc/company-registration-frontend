/*
 * Copyright 2020 HM Revenue & Customs
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

import models._
import org.joda.time.LocalDate
import play.api.data.validation._
import services.TimeService

import scala.util.{Failure, Success, Try}

object SCRSValidators {
  val desSessionRegex = "^([A-Za-z0-9-]{0,60})$"
  val deskproRegex = """^[A-Za-z\-.,()'"\s]+$"""
  val postCodeRegex = """^[A-Z]{1,2}[0-9][0-9A-Z]? [0-9][A-Z]{2}$""".r
  private val phoneNumberRegex = """^[0-9 ]{1,20}$""".r
  private val completionCapacityRegex = """^[A-Za-z0-9 '\-]{1,100}$""".r
  private val utrRegex = """^[0-9]{0,10}$"""
  private val emailRegex = """^(?!.{71,})([-0-9a-zA-Z.+_]+@[-0-9a-zA-Z.+_]+\.[a-zA-Z]{1,11})$"""
  private val emailRegexDes = """^[A-Za-z0-9\-_.@]{1,70}$"""
  val datePatternRegex = """([12]\d{3}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01]))"""
  val shareholderNameRegex = """[A-Z a-z 0-9\\'-]{1,20}$"""
  val otherBusinessNameRegex = """[A-Z a-z 0-9\\'-]{1,100}$"""

  def isValidPhoneNo(phone: String): Either[String, String] = {
    def isValidNumber(s: String) = s.replaceAll(" ", "").matches("[0-9]+")

    val digitCount = phone.trim.replaceAll(" ", "").length

    (isValidNumber(phone), phone.trim.matches(phoneNumberRegex.toString())) match {
      case (true, _) if digitCount > 20 => Left("validation.contactNum.tooLong")
      case (true, _) if digitCount < 10 => Left("validation.contactNum.tooShort")
      case (true, true) => Right(phone.trim)
      case (true, false) => Right(phone.replaceAll(" ", ""))
      case _ => Left("validation.contactNum")
    }
  }

  val completionCapacityValidation: Constraint[String] = Constraint("constraints.completionCapacity")({
    text =>
      val errors = text.trim match {
        case completionCapacityRegex() => Nil
        case _ => Seq(ValidationError(Messages("validation.invalid")))
      }
      if (errors.isEmpty) Valid else Invalid(errors)
  })

  val shareholderNameValidation: Constraint[String] = Constraint("constraints.shareholderName")({
    text =>
      val textTrimmed = text.trim
      val errors = textTrimmed match {
        case t if t.length == 0 => Seq(ValidationError("page.groups.groupName.noCompanyNameEntered"))
        case t if t.length > 20 => Seq(ValidationError("page.groups.groupName.20CharsOrLess"))
        case t if !t.matches(shareholderNameRegex) => Seq(ValidationError("page.groups.groupName.invalidFormat"))
        case t if t.matches(shareholderNameRegex) => Nil
      }
      if (errors.isEmpty) Valid else Invalid(errors)
  })

  val otherBusinessNameValidation: Constraint[String] = Constraint("constraints.otherBusinessName")({
    text =>
      val errors = text.trim match {
        case name if name.length == 0 => Seq(ValidationError("error.otherBusinessName.required"))
        case name if name.length > 100 => Seq(ValidationError("error.otherBusinessName.over100Characters"))
        case name if !name.matches(otherBusinessNameRegex) => Seq(ValidationError("error.otherBusinessName.invalidCharacters"))
        case _ => Nil
      }
      if (errors.isEmpty) Valid else Invalid(errors)
  })

  val whoAgreedTakeoverValidation: Constraint[String] = Constraint("constraints.whoAgreedTakeover")({
    text =>
      val errors = text.trim match {
        case name if name.length == 0 => Seq(ValidationError("error.whoAgreedTakeover.required"))
        case name if name.length > 100 => Seq(ValidationError("error.whoAgreedTakeover.over100Characters"))
        case name if !name.matches(otherBusinessNameRegex) => Seq(ValidationError("error.whoAgreedTakeover.invalidCharacters"))
        case _ => Nil
      }
      if (errors.isEmpty) Valid else Invalid(errors)
  })

  val UtrValidation: Constraint[String] = Constraint("constraints.utr")({
    text =>
      val errors = text match {
        case t if t.length == 0 => Seq(ValidationError("error.groupUtr.yesButNoUtr"))
        case t if t.matches(utrRegex) => Nil
        case t if t.length > 10 => Seq(ValidationError("error.groupUtr.utrMoreThan10Chars"))
        case t if !t.matches("[0-9]+") => Seq(ValidationError("error.groupUtr.utrHasSymbols"))
        case _ => Seq(ValidationError("error.groupUtr.yesButNoUtr"))
      }
      if (errors.isEmpty) Valid else Invalid(errors)
  })

  val emailValidation: Constraint[String] = Constraint("constraints.emailCheck")({
    text =>
      val errors = text match {
        case t if t.matches(emailRegex) && t.matches(emailRegexDes) => Nil
        case t if t.length > 70 => Seq(ValidationError(Messages("validation.emailtoolong")))
        case _ => Seq(ValidationError(Messages("validation.email")))
      }
      if (errors.isEmpty) Valid else Invalid(errors)
  })

  def companyContactDetailsValidation = {
    Constraint("constraints.companyContactDetails")({
      (model: CompanyContactDetailsApi) =>
        (model.contactEmail, model.contactDaytimeTelephoneNumber, model.contactMobileNumber) match {
          case (Some(_), _, _) => Valid
          case (_, Some(_), _) => Valid
          case (_, _, Some(_)) => Valid
          case _ => Invalid(Seq(ValidationError(Messages("page.reg.company-contact-details.validation.chooseOne"), "chooseOne")))
        }
    })
  }
}

class SCRSValidators(val timeService: TimeService) extends SCRSValidatorsT {
  val now: LocalDate = LocalDate.now()
}

trait SCRSValidatorsT {

  val timeService: TimeService
  val now: LocalDate

  private val nameRegex = """^[a-zA-Z-]+(?:\W+[a-zA-Z-]+)+$""".r

  private val addresslinelongRegex = """^$|[a-zA-Z0-9,.\(\)/&'"\-]{1}[a-zA-Z0-9, .\(\)/&'"\-]{0,26}$""".r
  private val addressline4Regex = """^$|[a-zA-Z0-9,.\(\)/&'"\-]{1}[a-zA-Z0-9, .\(\)/&'"\-]{0,17}$""".r
  private val nonEmptyRegex = """^(?=\s*\S).*$""".r


  val desSessionRegex = "^([A-Za-z0-9-]{0,60})$"

  implicit lazy val bHS = timeService.bHS

  val MAX_NAME_LENGTH = 300

  def accountingDateValidation = {
    Constraint("constraints.twoWorkingDays")({
      (model: AccountingDatesModel) =>
        model.crnDate match {
          case "futureDate" =>
            Try[LocalDate] {
              LocalDate.parse(s"${model.year.get}-${model.month.get}-${model.day.get}")
            } match {
              case Success(date) =>
                if (timeService.isDateSomeWorkingDaysInFuture(date) && date.isBefore(now.plusYears(3).plusDays(1))) {
                  Valid
                } else {
                  Invalid(Seq(ValidationError("page.reg.accountingDates.date.future", "notFuture")))
                }
              case Failure(_) => Invalid(Seq(ValidationError("page.reg.accountingDates.date.invalid-date", "invalidDate")))
            }
          case _ => Valid
        }
    })
  }

  def emptyDateConstraint: Constraint[AccountingDatesModel] = {
    Constraint("constraints.emptyDate")({
      model =>
        model.crnDate match {
          case "futureDate" if model.day.isEmpty && model.month.isEmpty && model.year.isEmpty =>
            Invalid(Seq(ValidationError("page.reg.accountingDates.date.notFound", "dateNotFoundDay")))
          case "futureDate" if Seq(model.day, model.month, model.year).flatten.length < 3 =>
            Invalid(dmyNotCompletedErrors(model))
          case _ => Valid
        }
    })
  }

  private def dmyNotCompletedErrors(model: AccountingDatesModel): Seq[ValidationError] = {
    val definedFields = Seq(
      model.day.map(_ => "day"),
      model.month.map(_ => "month"),
      model.year.map(_ => "year")
    ).flatten

    Seq("day", "month", "year").filterNot(definedFields.contains(_)).map {
      fieldName => ValidationError(s"page.reg.accountingDates.$fieldName.notFound", s"dateNotFound${fieldName.capitalize}")
    }
  }

  def validateDate: Constraint[AccountingDatesModel] = {
    Constraint("constraints.validateDate")({
      model =>
        model.crnDate match {
          case "futureDate" =>
            val fieldErrors = validateDateFields(model.day.get, model.month.get, model.year.get)
            if (fieldErrors.nonEmpty) Invalid(fieldErrors) else {
              val date = s"${model.year.get}-${model.month.get}-${model.day.get}"
              if (timeService.validate(date)) Valid else Invalid(Seq(ValidationError("page.reg.accountingDates.date.invalid-date", "invalidDate")))
            }
          case _ => Valid
        }
    })
  }

  private def validateDateFields(day: String, month: String, year: String): Seq[ValidationError] = {
    def validDateField(fieldName: String, maxVal: Int, field: String) = Try(field.toInt) match {
      case Success(int) => if (0 < int && int <= maxVal) None else Some(ValidationError(s"page.reg.accountingDates.date.invalid-$fieldName", s"invalid${fieldName.capitalize}"))
      case Failure(_) => Some(ValidationError(s"page.reg.accountingDates.date.invalid-$fieldName", s"invalid${fieldName.capitalize}"))
    }

    val validatedYear = Try(year.toInt) match {
      case Success(_) => None
      case Failure(_) => Some(ValidationError("page.reg.accountingDates.date.invalid-year", "invalidYear"))
    }
    Seq(
      validDateField("day", 31, day),
      validDateField("month", 12, month),
      validatedYear
    ).flatten
  }
}