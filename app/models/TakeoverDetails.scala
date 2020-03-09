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

case class TakeoverDetails(replacingAnotherBusiness: Boolean,
                           businessName: Option[String] = None,
                           businessTakeoverAddress: Option[NewAddress] = None,
                           previousOwnersName: Option[String] = None,
                           previousOwnersAddress: Option[NewAddress] = None)

object TakeoverDetails {

  implicit val newAddressFormat: Format[NewAddress] = (
    (JsPath \ "line1").format[String] and
      (JsPath \ "line2").format[String] and
      (JsPath \ "line3").formatNullable[String] and
      (JsPath \ "line4").formatNullable[String] and
      (JsPath \ "country").formatNullable[String] and
      (JsPath \ "postcode").formatNullable[String]
    ) ((l1, l2, l3, l4, coun, post) => NewAddress(l1, l2, l3, l4, post, coun, None),
    nAddress => (nAddress.addressLine1, nAddress.addressLine2, nAddress.addressLine3, nAddress.addressLine4, nAddress.country, nAddress.postcode))

  implicit val format: Format[TakeoverDetails] = (
    (JsPath \ "replacingAnotherBusiness").format[Boolean] and
      (JsPath \ "businessName").formatNullable[String] and
      (JsPath \ "businessTakeoverAddress").formatNullable[NewAddress] and
      (JsPath \ "prevOwnersName").formatNullable[String] and
      (JsPath \ "prevOwnersAddress").formatNullable[NewAddress]
    ) (TakeoverDetails.apply, unlift(TakeoverDetails.unapply))
} 
