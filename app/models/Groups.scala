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

package models

import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, Json, _}

case class Groups(
                   groupRelief: Boolean = true,
                   nameOfCompany: Option[GroupCompanyName],
                   addressAndType: Option[GroupsAddressAndType],
                   groupUTR: Option[GroupUTR]
                 )

object Groups {

  implicit val formatsNewAddressGroups: Format[NewAddress] = (
    (__ \ "line1").format[String] and
    (__ \ "line2").format[String] and
    (__ \ "line3").formatNullable[String] and
    (__ \ "line4").formatNullable[String] and
    (__ \ "country").formatNullable[String] and
    (__ \ "postcode").formatNullable[String]
    )((l1,l2,l3,l4,coun,post) => NewAddress.apply(l1,l2,l3,l4,post,coun,None),
    nAddress => (nAddress.addressLine1, nAddress.addressLine2, nAddress.addressLine3, nAddress.addressLine4, nAddress.country, nAddress.postcode))
  implicit val formatsGCN: OFormat[GroupCompanyName] = Json.format[GroupCompanyName]
  implicit val formatsGAAT: OFormat[GroupsAddressAndType] = Json.format[GroupsAddressAndType]
  implicit val formatsGU: OFormat[GroupUTR] = Json.format[GroupUTR]
  implicit val formats: OFormat[Groups] =  Json.format[Groups]
}

case class GroupAddressChoice(choice: String)
case class GroupCompanyName(name: String, nameType: String)

case class GroupUTR(UTR : Option[String])

object GroupUTR{
  implicit val format: OFormat[GroupUTR] = Json.format[GroupUTR]
}
case class GroupsAddressAndType(addressType: String, address: NewAddress)

case class GroupRelief(
                      groupRelief: String = ""
                      )

object GroupRelief {
  implicit val format: OFormat[GroupRelief] = Json.format[GroupRelief]
}


case class Shareholder(corporate_name: String,
                       percentage_voting_rights: Option[Double],
                       percentage_dividend_rights: Option[Double],
                       percentage_capital_rights: Option[Double],
                       address: CHROAddress)

object Shareholder {
  val formatOfSingleShareholder: OFormat[Shareholder] = Json.format[Shareholder]

  implicit val formats: Format[List[Shareholder]] = new Format[List[Shareholder]] {
    override def writes(o: List[Shareholder]): JsValue = Json.toJson(o.map(_.corporate_name))

    override def reads(json: JsValue): JsResult[List[Shareholder]] = {
      val resOfCollect = json.validate[JsArray].map { jsArr =>
        jsArr.value.collect {
          case js: JsValue if js.validateOpt[Shareholder](formatOfSingleShareholder).isSuccess => js.as[Shareholder](formatOfSingleShareholder)
        }
      }
      resOfCollect.map(_.toList)
    }
  }
}