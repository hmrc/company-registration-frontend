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

package models.connectors

import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.play.test.UnitSpec

class ConfirmationReferencesSpec extends UnitSpec {

  "ConfirmationReferences" should {
    "Be able to parsed into the correct JSON format without AckRef" in {

      val json1: String =
        s"""
           |{
           |  "transaction-id" : "a",
           |  "payment-reference" : "b",
           |  "payment-amount" : "c",
           |  "acknowledgement-reference" : ""
           |}
       """.stripMargin

      val testModel1 = ConfirmationReferences("a", Some("b"), Some("c"), "")

      val result = Json.toJson[ConfirmationReferences](testModel1)
      result.getClass shouldBe classOf[JsObject]
      result shouldBe Json.parse(json1)
    }

    "Be able to parsed into the correct JSON format with AckRef" in {
      val json1: String =
        s"""
           |{
           |  "transaction-id" : "a",
           |  "payment-reference" : "b",
           |  "payment-amount" : "c",
           |  "acknowledgement-reference" : "d"
           |}
       """.stripMargin

      val testModel1 = ConfirmationReferences("a", Some("b"), Some("c"), "d")

      val result = Json.toJson[ConfirmationReferences](testModel1)
      result.getClass shouldBe classOf[JsObject]
      result shouldBe Json.parse(json1)
    }

    "Be able to parsed into the correct JSON format without a payment reference or amount" in {
      val json1: String =
        s"""
           |{
           |  "transaction-id" : "a",
           |  "acknowledgement-reference" : "d"
           |}
       """.stripMargin

      val testModel1 = ConfirmationReferences("a", None, None, "d")

      val result = Json.toJson[ConfirmationReferences](testModel1)
      result.getClass shouldBe classOf[JsObject]
      result shouldBe Json.parse(json1)
    }
  }
}
