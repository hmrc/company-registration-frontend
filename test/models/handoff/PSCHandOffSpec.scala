/*
 * Copyright 2022 HM Revenue & Customs
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

import helpers.UnitSpec
import models.NewAddress
import play.api.libs.json.Json

class PSCHandOffSpec extends UnitSpec{


  "formatTaxRef" should {
    "handle Some(empty string)" in {
      PSCHandOff.formatTaxRef(Some("")) shouldBe Some("")
    }
    "handle None" in {
      PSCHandOff.formatTaxRef(None) shouldBe None
    }
    "handle exactly 3 chars and not hash them out" in {
      PSCHandOff.formatTaxRef(Some("123")) shouldBe Some("123")
    }
    "handle  4 chars and not hash them out" in {
      PSCHandOff.formatTaxRef(Some("1234")) shouldBe Some("*234")
    }
    "handle a random amount of chars and not hash them out" in {
      PSCHandOff.formatTaxRef(Some("ABC12345678910")) shouldBe Some("***********910")
    }
    "handle spaces and not hash them out" in {
      PSCHandOff.formatTaxRef(Some(" 123")) shouldBe Some("*123")
    }
  }
  "writes of PSC handOff" should {
    "write with full model" in {
      val validfullModel = PSCHandOff(
        "foo",
        "bar",
        Json.obj("foo" -> "wizz"),
        Some(Json.obj("ch" -> "bar")),
        NavLinks("for","rev"),
        true,
        Some(true),
        Some(ParentCompany("name",NewAddress("1","2",Some("3"),Some("4"),Some("post"),Some("count"),None),Some("1234567890"))
      ),Some(JumpLinksForGroups("loss", Some("parentadd"),Some("parentname"),Some("parenttax"))))

      val res = Json.toJson(validfullModel)(PSCHandOff.writes)
      res shouldBe Json.parse(
        """{"user_id":"foo","journey_id":"bar",
          |"hmrc":{"foo":"wizz"},
          |"another_company_own_shares":true,
          |"ch":{"ch":"bar"},"parent_company":{"name":"name",
          |"address":{"address_line_1":"1","address_line_2":"2","address_line_3":"3","address_line_4":"4","country":"count","postal_code":"post"},
          |"tax_reference":"*******890"},
          |"links":{"forward":"for","reverse":"rev","loss_relief_group":"loss","parent_address":"parentadd","parent_company_name":"parentname","parent_tax_reference":"parenttax"},"loss_relief_group":true}""".stripMargin)
    }
    "write with no groups" in {
      val validfullModel = PSCHandOff(
        "foo",
        "bar",
        Json.obj("foo" -> "wizz"),
        Some(Json.obj("ch" -> "bar")),
        NavLinks("for","rev"),
        false,
        None,
        None,
        None)
      Json.toJson(validfullModel)(PSCHandOff.writes) shouldBe Json.parse(
        """{"user_id":"foo","journey_id":"bar","hmrc":{"foo":"wizz"},
          |"another_company_own_shares":false,"ch":{"ch":"bar"},"links":{"forward":"for","reverse":"rev"}} """.stripMargin)
    }
    "write with loss relief and ONE jump link for loss relief as user was eligible for groups but said no" in {
      val validfullModel = PSCHandOff(
        "foo",
        "bar",
        Json.obj("foo" -> "wizz"),
        Some(Json.obj("ch" -> "bar")),
        NavLinks("for","rev"),
        true,
        Some(false),
        None,Some(JumpLinksForGroups("loss",None,None,None)))

      val res = Json.toJson(validfullModel)(PSCHandOff.writes)
      res shouldBe Json.parse("""{"user_id":"foo","journey_id":"bar","hmrc":{"foo":"wizz"},"another_company_own_shares":true,"ch":{"ch":"bar"},"links":{"forward":"for","reverse":"rev","loss_relief_group":"loss"},"loss_relief_group":false}""")
    }
    "write with no country or postcode in address and no tax reference" in {
      val validfullModel = PSCHandOff(
        "foo",
        "bar",
        Json.obj("foo" -> "wizz"),
        Some(Json.obj("ch" -> "bar")),
        NavLinks("for","rev"),
        true,
        Some(true),
        Some(ParentCompany("name",NewAddress("1","2",Some("3"),Some("4"),None,None,None),None)
        ),Some(JumpLinksForGroups("loss", Some("parentadd"),Some("parentname"),Some("parenttax"))))

      Json.toJson(validfullModel)(PSCHandOff.writes) shouldBe Json.parse(
        """
          |{"user_id":"foo","journey_id":"bar",
          |"hmrc":{"foo":"wizz"},
          |"another_company_own_shares":true,"ch":{"ch":"bar"},
          |"parent_company":{"name":"name","address":{"address_line_1":"1","address_line_2":"2","address_line_3":"3","address_line_4":"4"}},
          |"links":{"forward":"for","reverse":"rev","loss_relief_group":"loss","parent_address":"parentadd","parent_company_name":"parentname","parent_tax_reference":"parenttax"},"loss_relief_group":true}
        """.stripMargin)
    }
  }
}