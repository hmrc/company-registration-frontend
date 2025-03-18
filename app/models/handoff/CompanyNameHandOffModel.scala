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

package models.handoff

import models._
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

case class CompanyNameHandOffModel(email_address : String,
                                   is_verified_email_address : Option[Boolean],
                                   journey_id : Option[String],
                                   user_id : String,
                                   name : String,
                                   hmrc: JsObject,
                                   session: JsObject,
                                   ch: Option[JsObject],
                                   language: String,
                                   links: NavLinks)

object CompanyNameHandOffModel {
  implicit val format: OFormat[CompanyNameHandOffModel] = Json.format[CompanyNameHandOffModel]
}

case class CompanyNameHandOffFormModel(registration_id : Option[String],
                                       openidconnectid : String,
                                       company_name : String,
                                       registered_office_address : CHROAddress,
                                       jurisdiction : String,
                                       ch: String,
                                       hmrc: String,
                                       language: String)

object CompanyNameHandOffFormModel {
  implicit val format: OFormat[CompanyNameHandOffFormModel] = Json.format[CompanyNameHandOffFormModel]
}

case class CompanyNameHandOffIncoming(journey_id : Option[String],
                                      user_id : String,
                                      company_name : String,
                                      registered_office_address : CHROAddress,
                                      jurisdiction : String,
                                      transactionId : String,
                                      ch: JsObject,
                                      hmrc: JsObject,
                                      language: String,
                                      links: JsObject) {


}

object CompanyNameHandOffIncoming {
  implicit val format: OFormat[CompanyNameHandOffIncoming] = (
    (__ \ "journey_id").formatNullable[String] and
    (__ \ "user_id").format[String] and
    (__ \ "company_name").format[String] and
    (__ \ "registered_office_address").format[CHROAddress] and
    (__ \ "jurisdiction").format[String] and
    (__ \ "transaction_id").format[String] and
    (__ \ "ch").format[JsObject] and
    (__ \ "hmrc").format[JsObject] and
    (__ \ "language").format[String] and
    (__ \ "links").format[JsObject]
  )(CompanyNameHandOffIncoming.apply, unlift(CompanyNameHandOffIncoming.unapply))
}
