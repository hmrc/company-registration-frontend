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

import models.NewAddress
import play.api.libs.json._

case class PSCHandOff(user_id: String,
                      journey_id: String,
                      hmrc: JsObject,
                      ch: Option[JsObject],
                      language: String,
                      links: NavLinks,
                      another_company_own_shares: Boolean = false,
                      loss_relief_group: Option[Boolean] = None,
                      parent_company: Option[ParentCompany] = None,
                      jumpLinks: Option[JumpLinksForGroups] = None
                     )


case class JumpLinksForGroups(loss_relief_group: String,
                              parent_address: Option[String],
                              parent_company_name: Option[String],
                              parent_tax_reference: Option[String])

case class ParentCompany(name: String,
                         address: NewAddress,
                         tax_reference: Option[String])
object PSCHandOff {
  implicit val jLinksFormats: OFormat[JumpLinksForGroups] = Json.format[JumpLinksForGroups]
  val formatTaxRef: Option[String] => Option[String] = (taxRef: Option[String]) => {
    taxRef.map(str => {
      val utr = str.takeRight(3)
      str.take(str.size - utr.size)
        .map(_ => "*")
        .fold("")((a, b) => a + b) + utr

    })
  }
  val writesForAddrForCohoHandOff: Writes[NewAddress] = new Writes[NewAddress] {
    override def writes(o: NewAddress): JsValue = {
     val line1 =  Json.obj("address_line_1" -> o.addressLine1)
     val line2 =  Json.obj("address_line_2" -> o.addressLine2)
     val line3 =  o.addressLine3.fold(Json.obj())(l3 => Json.obj("address_line_3" -> l3))
     val line4 =  o.addressLine4.fold(Json.obj())(l4 => Json.obj("address_line_4" -> l4))
      val country = o.country.fold(Json.obj())(count => Json.obj("country" -> count))
      val postcode = o.postcode.fold(Json.obj())(count => Json.obj("postal_code" -> count))

      line1.deepMerge(line2).deepMerge(line3).deepMerge(line4).deepMerge(country).deepMerge(postcode)
    }
  }

  val formatOfParentCompany = new Writes[ParentCompany] {
    override def writes(o: ParentCompany): JsValue = {
      val taxReference = formatTaxRef(o.tax_reference)
      Json.obj(
        "name" -> o.name,
        "address" -> Json.toJson(o.address)(writesForAddrForCohoHandOff))
        .deepMerge(taxReference.fold(Json.obj())(tr => Json.obj("tax_reference" -> tr)))
    }
  }

    implicit val writes: Writes[PSCHandOff] = new Writes[PSCHandOff] {
      override def writes(o: PSCHandOff): JsValue = {
        val chJs = o.ch.fold(Json.obj())(ch => Json.obj("ch" -> ch))

        val parentCompany = o.parent_company.fold(Json.obj())(pc => Json.obj("parent_company" -> Json.toJson(pc)(formatOfParentCompany)))

        val links = Json.obj("links" -> Json.toJson(o.links))
          .deepMerge(o.jumpLinks.map(jLinks => Json.toJson(jLinks)(jLinksFormats))
            .fold(Json.obj())(jLinks => Json.obj("links" -> jLinks))
        )
        val lossReliefGroup = o.loss_relief_group.fold(Json.obj())(lrg => Json.obj("loss_relief_group" -> lrg))

        Json.obj(
          "user_id" -> o.user_id,
          "journey_id" -> o.journey_id,
          "hmrc" -> o.hmrc,
          "language" -> o.language,
          "another_company_own_shares" -> o.another_company_own_shares
        ).deepMerge(chJs)
          .deepMerge(parentCompany)
          .deepMerge(links)
          .deepMerge(lossReliefGroup)
      }
    }

}
