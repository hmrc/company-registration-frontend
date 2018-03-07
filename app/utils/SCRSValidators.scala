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

import models._
import org.joda.time.LocalDate
import play.api.data.validation._
import services.TimeService

import scala.util.{Failure, Success, Try}

object SCRSValidators extends SCRSValidators {
  val timeService: TimeService = TimeService
  val now : LocalDate          = LocalDate.now()
}

trait SCRSValidators {

  val timeService: TimeService
  val now : LocalDate

  private val nameRegex               = """^[a-zA-Z-]+(?:\W+[a-zA-Z-]+)+$""".r
  private val contactNameRegex        = """^[A-Za-z '\\-]{1,100}$""".r
  private val emailRegex              = """^(?!.{71,})([-0-9a-zA-Z.+_]+@[-0-9a-zA-Z.+_]+\.[a-zA-Z]{1,11})$"""
  private val emailRegexDes           = """^[A-Za-z0-9\-_.@]{1,70}$"""
  private val phoneNumberRegex        = """^[0-9 ]{1,20}$""".r
  val postCodeRegex                   = """^[A-Z]{1,2}[0-9][0-9A-Z]? [0-9][A-Z]{2}$""".r
  private val addresslinelongRegex    = """^$|[a-zA-Z0-9,.\(\)/&'"\-]{1}[a-zA-Z0-9, .\(\)/&'"\-]{0,26}$""".r
  private val addressline4Regex       = """^$|[a-zA-Z0-9,.\(\)/&'"\-]{1}[a-zA-Z0-9, .\(\)/&'"\-]{0,17}$""".r
  private val nonEmptyRegex           = """^(?=\s*\S).*$""".r
  private val completionCapacityRegex = """^[A-Za-z0-9 '\-]{1,100}$""".r

  val datePatternRegex                = """([12]\d{3}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01]))"""

  implicit val bHS = TimeService.bHS

  val MAX_NAME_LENGTH = 300



  def companyContactDetailsValidation = {
    Constraint("constraints.companyContactDetails")({
      (model: CompanyContactViewModel) =>
        (model.contactEmail, model.contactDaytimeTelephoneNumber, model.contactMobileNumber) match {
          case (Some(_), _, _) => Valid
          case (_, Some(_), _) => Valid
          case (_, _, Some(_)) => Valid
          case _ => Invalid(Seq(ValidationError(Messages("page.reg.company-contact-details.validation.chooseOne"), "chooseOne")))
        }
    })
  }

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
          case "futureDate"  if model.day.isEmpty && model.month.isEmpty && model.year.isEmpty =>
            Invalid(Seq(ValidationError("page.reg.accountingDates.date.notFound", "dateNotFoundDay")))
          case "futureDate" if Seq(model.day, model.month, model.year).flatten.length < 3 =>
            Invalid(dmyNotCompletedErrors(model))
          case _ => Valid
        }
    })
  }

  private def dmyNotCompletedErrors(model: AccountingDatesModel) : Seq[ValidationError] = {
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
      model => model.crnDate match {
        case "futureDate" =>
          val fieldErrors = validateDateFields(model.day.get, model.month.get, model.year.get)
          if(fieldErrors.nonEmpty) Invalid(fieldErrors) else {
            val date = s"${model.year.get}-${model.month.get}-${model.day.get}"
            if(TimeService.validate(date)) Valid else Invalid(Seq(ValidationError("page.reg.accountingDates.date.invalid-date", "invalidDate")))
          }
        case _ => Valid
      }
    })
  }

  private def validateDateFields(day: String, month: String, year: String): Seq[ValidationError] = {
    def validDateField(fieldName: String, maxVal: Int, field: String) = Try(field.toInt) match {
      case Success(int) => if(0 < int && int <= maxVal) None else Some(ValidationError(s"page.reg.accountingDates.date.invalid-$fieldName", s"invalid${fieldName.capitalize}"))
      case Failure(_)   => Some(ValidationError(s"page.reg.accountingDates.date.invalid-$fieldName", s"invalid${fieldName.capitalize}"))
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

  def validNameFormat(name: Name) : Boolean = {
    val nameFormat: String = contactNameRegex.regex
    name.firstName.matches(nameFormat) && name.middleName.fold(true)(_.matches(nameFormat)) && name.surname.fold(true)(_.matches(nameFormat))
  }

  val contactNameValidation: Constraint[String] = Constraint("constraints.nameCheck")({
    fullName =>
      val trimmedName = fullName.trim
      val split: Name = SplitName.splitName(trimmedName)
      val errors = split match {
        case _ if trimmedName.contains(".") => Seq(ValidationError(Messages("validation.name.full-stop")))
        case name if name.firstName.length > 100 => Seq(ValidationError(Messages("validation.firstMaxLength")))
        case name if name.middleName.fold(false)(_.length > 100) => Seq(ValidationError(Messages("validation.middleMaxLength")))
        case name if name.surname.isEmpty => Seq(ValidationError(Messages("validation.name.noSurname")))
        case name if name.surname.get.length > 100 => Seq(ValidationError(Messages("validation.surnameMaxLength")))
        case name if validNameFormat(name) => Nil
        case _ => Seq(ValidationError(Messages("validation.name")))
      }
      if (errors.isEmpty) Valid else Invalid(errors)
  })

  val nameRequiredValidation: Constraint[String] = Constraint("constraints.nameRequired")({
    name => if(name.trim == "") Invalid(ValidationError(Messages("validation.name.required"))) else Valid
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

  def isValidPhoneNo(phone: String): Either[String, String] = {
    def isValidNumber(s: String) = s.replaceAll(" ", "").matches("[0-9]+")
    val digitCount = phone.trim.replaceAll(" ", "").length

    (isValidNumber(phone), phone.trim.matches(phoneNumberRegex.toString())) match {
      case (true, _) if digitCount > 20      => Left("validation.contactNum.tooLong")
      case (true, _) if digitCount < 10      => Left("validation.contactNum.tooShort")
      case (true, true)                      => Right(phone.trim)
      case (true, false)                     => Right(phone.replaceAll(" ", ""))
      case _                                 => Left("validation.contactNum")
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
}
