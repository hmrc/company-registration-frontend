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

package models

import play.api.libs.functional.syntax._
import play.api.libs.json._

import scala.language.implicitConversions

case class CompanyContactDetailsApi(contactEmail: Option[String],
                                    contactDaytimeTelephoneNumber: Option[String],
                                    contactMobileNumber: Option[String])

object CompanyContactDetailsApi {
  implicit val formats = Json.format[CompanyContactDetailsApi]

  val prePopWrites = new OWrites[CompanyContactDetailsApi] {
    override def writes(o: CompanyContactDetailsApi): JsObject = {
      Seq(
        o.contactEmail.map(a => Json.obj("email" -> a)),
        o.contactDaytimeTelephoneNumber.map(a => Json.obj("telephoneNumber" -> a)),
        o.contactMobileNumber.map(a => Json.obj("mobileNumber" -> a)))
          .map(_.getOrElse(Json.obj())).fold[JsObject](Json.obj())((a,b) => a.deepMerge(b))
    }
  }

  val prePopReads = (
      (__ \ "email").readNullable[String] and
      (__ \ "telephoneNumber").readNullable[String] and
      (__ \ "mobileNumber").readNullable[String]
    )(CompanyContactDetailsApi.apply _)
}

case class CompanyContactDetails(contactEmail: Option[String],
                                  contactDaytimeTelephoneNumber: Option[String],
                                  contactMobileNumber: Option[String],
                                  links: Links)

object CompanyContactDetails {
  implicit val formatsLinks = Json.format[Links]
  implicit val formats      = Json.format[CompanyContactDetails]

  def toApiModel(companyContactDetails: CompanyContactDetails): CompanyContactDetailsApi = {
    CompanyContactDetailsApi(
      companyContactDetails.contactEmail,
      companyContactDetails.contactDaytimeTelephoneNumber,
      companyContactDetails.contactMobileNumber
    )
  }
}

sealed trait CompanyContactDetailsResponse
final case class CompanyContactDetailsSuccessResponse(response: CompanyContactDetails) extends CompanyContactDetailsResponse
case object CompanyContactDetailsNotFoundResponse extends CompanyContactDetailsResponse
case object CompanyContactDetailsBadRequestResponse extends CompanyContactDetailsResponse
case object CompanyContactDetailsForbiddenResponse extends CompanyContactDetailsResponse
final case class CompanyContactDetailsErrorResponse(err: Exception) extends CompanyContactDetailsResponse
