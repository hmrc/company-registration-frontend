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

import models.PPOB
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsObject, __}

case class HandoffPPOB(address_line_1: String,
                       address_line_2: String,
                       address_line_3: Option[String],
                       address_line_4: Option[String],
                       postal_code: Option[String],
                       country: Option[String] = None)

object HandoffPPOB {

  def fromCorePPOB(core: PPOB) : HandoffPPOB = {

    core.address match {
      case Some(address) =>
        HandoffPPOB(
          core.address.get.addressLine1,
          core.address.get.addressLine2,
          core.address.get.addressLine3.fold[Option[String]](None)(s => Some(s)),
          core.address.get.addressLine4.fold[Option[String]](None)(s => Some(s)),
          core.address.get.postCode.fold[Option[String]](None)(s => Some(s)),
          core.address.get.country.fold[Option[String]](None)(s => Some(s))
        )
      case None =>
        HandoffPPOB("", "", None, None, None, None)
    }
  }
}

case class BusinessActivitiesModel(authExtId : String,
                                   regId : String,
                                   ppob : Option[HandoffPPOB],
                                   ch : Option[JsObject],
                                   hmrc : JsObject,
                                   language: String,
                                   links : NavLinks)

object BusinessActivitiesModel {

  implicit val formatPPOB = (
      (__ \ "address_line_1").format[String] and
      (__ \ "address_line_2").format[String] and
      (__ \ "address_line_3").formatNullable[String] and
      (__ \ "address_line_4").formatNullable[String] and
      (__ \ "postal_code").formatNullable[String] and
      (__ \ "country").formatNullable[String]
    )(HandoffPPOB.apply, unlift(HandoffPPOB.unapply))


  implicit val format = (
      (__ \ "user_id").format[String] and
      (__ \ "journey_id").format[String] and
      (__ \ "principal_place_of_business_address").formatNullable[HandoffPPOB] and
      (__ \ "ch").formatNullable[JsObject] and
      (__ \ "hmrc").format[JsObject] and
      (__ \ "language").format[String] and
      (__ \ "links").format[NavLinks]
    )(BusinessActivitiesModel.apply, unlift(BusinessActivitiesModel.unapply))

}
