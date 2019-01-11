/*
 * Copyright 2019 HM Revenue & Customs
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

package models

import play.api.libs.json.Json
import services.TimeService

import scala.language.implicitConversions

case class AccountingDatesModel(crnDate : String,
                                year: Option[String],
                                month: Option[String],
                                day: Option[String]){

  val isDateDefined = day.isDefined && month.isDefined && year.isDefined
  val toSummaryDate: Option[String] = if(isDateDefined) Some(s"${day.get}/${month.get}/${year.get}") else None
}

object AccountingDatesModel {
  val WHEN_REGISTERED = "WHEN_REGISTERED"
  val FUTURE_DATE = "FUTURE_DATE"
  val NOT_PLANNING_TO_YET = "NOT_PLANNING_TO_YET"

  implicit val format = Json.format[AccountingDatesModel]

  def empty = AccountingDatesModel("", None, None, None)

  implicit def toModel(api:AccountingDetails) : AccountingDatesModel = {
    if(api.startDateOfBusiness.isDefined) {
      val date = TimeService.splitDate(api.startDateOfBusiness.get)
      AccountingDatesModel(
        api.accountingDateStatus,
        Some(date(0)),
        Some(date(1)),
        Some(date(2))
      )
    } else {
      AccountingDatesModel(api.accountingDateStatus, None, None, None)
    }
  }
}

case class AccountingDetails(accountingDateStatus : String,
                             startDateOfBusiness : Option[String],
                             links : Links){
}

object AccountingDetails {
  implicit val formatLinks = Json.format[Links]
  implicit val formats = Json.format[AccountingDetails]
}

case class AccountingDetailsRequest(accountingDateStatus : String,
                                    startDateOfBusiness : Option[String])

object AccountingDetailsRequest {
  implicit val formats = Json.format[AccountingDetailsRequest]

  def toRequest(accDates: AccountingDatesModel) : AccountingDetailsRequest = {
      val formattedDate = TimeService.toDateTime(accDates.day, accDates.month, accDates.year).map(_.toString(TimeService.DATE_FORMAT))
      AccountingDetailsRequest(accDates.crnDate, formattedDate)
  }
}

sealed trait AccountingDetailsResponse
final case class AccountingDetailsSuccessResponse(response: AccountingDetails) extends AccountingDetailsResponse
case object AccountingDetailsNotFoundResponse extends AccountingDetailsResponse
case object AccountingDetailsBadRequestResponse extends AccountingDetailsResponse
final case class AccountingDetailsErrorResponse(err: Exception) extends AccountingDetailsResponse
