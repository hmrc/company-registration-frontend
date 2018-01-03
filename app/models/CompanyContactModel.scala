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

package models

import play.api.libs.functional.syntax._
import play.api.libs.json._
import utils.SplitName
import scala.language.implicitConversions

case class CompanyContactViewModel(contactName: String,
                                   contactEmail: Option[String],
                                   contactDaytimeTelephoneNumber: Option[String],
                                   contactMobileNumber: Option[String])

object CompanyContactViewModel {
  implicit val formats = Json.format[CompanyContactViewModel]

  implicit def toMongoModel(viewModel: CompanyContactViewModel): CompanyContactDetailsMongo = {

    val name = SplitName.splitName(viewModel.contactName)

    CompanyContactDetailsMongo(
      Some(name.firstName),
      name.middleName,
      name.surname,
      viewModel.contactDaytimeTelephoneNumber,
      viewModel.contactMobileNumber,
      viewModel.contactEmail
    )
  }

  def empty = {
    CompanyContactViewModel("", None, None, None)
  }
}

case class CompanyContactDetailsMongo(contactFirstName: Option[String],
                                      contactMiddleName: Option[String],
                                      contactSurname: Option[String],
                                      contactDaytimeTelephoneNumber: Option[String],
                                      contactMobileNumber: Option[String],
                                      contactEmail: Option[String])

object CompanyContactDetailsMongo {
  implicit val formats = Json.format[CompanyContactDetailsMongo]

  val prePopWrites = (
    (__ \ "firstName").writeNullable[String] and
      (__ \ "middleName").writeNullable[String] and
      (__ \ "surname").writeNullable[String] and
      (__ \ "telephoneNumber").writeNullable[String] and
      (__ \ "mobileNumber").writeNullable[String] and
      (__ \ "email").writeNullable[String]
  )(unlift(CompanyContactDetailsMongo.unapply))

  val prePopReads = (
    (__ \ "firstName").readNullable[String] and
      (__ \ "middleName").readNullable[String] and
      (__ \ "surname").readNullable[String] and
      (__ \ "telephoneNumber").readNullable[String] and
      (__ \ "mobileNumber").readNullable[String] and
      (__ \ "email").readNullable[String]
    )(CompanyContactDetailsMongo.apply _)
}

case class CompanyContactDetails(contactFirstName: Option[String],
                                 contactMiddleName: Option[String],
                                 contactSurname: Option[String],
                                 contactDaytimeTelephoneNumber: Option[String],
                                 contactMobileNumber: Option[String],
                                 contactEmail: Option[String],
                                 links: Links)

object CompanyContactDetails {
  implicit val formatsLinks = Json.format[Links]
  implicit val formats = Json.format[CompanyContactDetails]

  def isEqual(a: CompanyContactDetails, b: CompanyContactDetails): Boolean = {
    Seq(
      a.contactFirstName.equals(b.contactFirstName),
      a.contactMiddleName.equals(b.contactMiddleName),
      a.contactSurname.equals(b.contactSurname),
      a.contactEmail.equals(b.contactEmail)
    ).filter(eq => !eq).isEmpty
  }

  implicit def toMongoModel(companyContactDetails: CompanyContactDetails): CompanyContactDetailsMongo = {
    CompanyContactDetailsMongo(
      companyContactDetails.contactFirstName,
      companyContactDetails.contactMiddleName,
      companyContactDetails.contactSurname,
      companyContactDetails.contactDaytimeTelephoneNumber,
      companyContactDetails.contactMobileNumber,
      companyContactDetails.contactEmail
    )
  }

  implicit def toViewModel(companyContactDetails: CompanyContactDetails): CompanyContactViewModel = {

    val contactName = (companyContactDetails.contactFirstName, companyContactDetails.contactMiddleName, companyContactDetails.contactSurname) match {
      case (Some(f), Some(m), Some(l)) => s"$f $m $l"
      case (Some(f), _, Some(l)) => s"$f $l"
      case (Some(f), _, _) => f
      case _ => ""
    }

    CompanyContactViewModel(
      contactName,
      companyContactDetails.contactEmail,
      companyContactDetails.contactDaytimeTelephoneNumber,
      companyContactDetails.contactMobileNumber
    )
  }

  def empty = {
    CompanyContactViewModel("", None, None, None)
  }
}

sealed trait CompanyContactDetailsResponse
final case class CompanyContactDetailsSuccessResponse(response: CompanyContactDetails) extends CompanyContactDetailsResponse
case object CompanyContactDetailsNotFoundResponse extends CompanyContactDetailsResponse
case object CompanyContactDetailsBadRequestResponse extends CompanyContactDetailsResponse
case object CompanyContactDetailsForbiddenResponse extends CompanyContactDetailsResponse
final case class CompanyContactDetailsErrorResponse(err: Exception) extends CompanyContactDetailsResponse
