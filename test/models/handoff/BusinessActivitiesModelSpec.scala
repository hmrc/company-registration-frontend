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

package models.handoff

import models.{Address, PPOB}
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.play.test.UnitSpec

class BusinessActivitiesModelSpec extends UnitSpec {

  "BusinessActivitiesModel" should {
    "Be able to parsed into JSON without PPOB" in {

      val json1 : String =
        s"""
           |{
           |  "user_id" : "FAKE_OPEN_CONNECT",
           |  "journey_id" : "regID",
           |  "ch" : { "foo":"bar" },
           |  "hmrc" : { "foo":"bar" },
           |  "links" : { "forward":"test", "reverse":"test2" }
           |}
       """.stripMargin

      val testModel1 =
        BusinessActivitiesModel(
          "FAKE_OPEN_CONNECT",
          "regID",
          None,
          Some(JsObject(Seq("foo" -> Json.toJson("bar")))),
          JsObject(Seq("foo" -> Json.toJson("bar"))),
          NavLinks("test", "test2")
      )

      val result = Json.toJson[BusinessActivitiesModel](testModel1)
      result.getClass shouldBe classOf[JsObject]
      result shouldBe Json.parse(json1)
    }

    "Be able to parsed into JSON with PPOB" in {

      val json2 : String =
        s"""
           |{
           |  "user_id" : "FAKE_OPEN_CONNECT",
           |  "journey_id" : "regID",
           |  "principal_place_of_business_address" : {
           |    "address_line_1": "number L1",
           |    "address_line_2": "L2",
           |    "address_line_3": "L3",
           |    "address_line_4": "L4",
           |    "postal_code": "POCode",
           |    "country": "Country"
           |  },
           |  "hmrc" : { "foo":"bar" },
           |  "ch" : { "foo":"bar" },
           |  "links" : { "forward": "testForward", "reverse": "testReverse" }
           |}
       """.stripMargin

      val testModel2 =
        BusinessActivitiesModel(
          "FAKE_OPEN_CONNECT", "regID",
          Some(HandoffPPOB( "number L1", "L2", Some("L3"), Some("L4"),
            Some("POCode"), Some("Country")) ),
            Some(JsObject(Seq("foo" -> Json.toJson("bar")))),
            JsObject(Seq("foo" -> Json.toJson("bar"))),
          NavLinks("testForward","testReverse")
        )

      val result = Json.toJson[BusinessActivitiesModel](testModel2)
      result.getClass shouldBe classOf[JsObject]
      result shouldBe Json.parse(json2)
    }

    "Be able to parsed into JSON with PPOB having no L4 or country" in {
      val testModel3 =
        BusinessActivitiesModel(
          "FAKE_OPEN_CONNECT", "regID",
          Some(HandoffPPOB( "number L1", "L2", Some("L3"), None,
            Some("POCode"), None) ),
          Some(JsObject(Seq("foo" -> Json.toJson("bar")))),
          JsObject(Seq("foo" -> Json.toJson("bar"))),
          NavLinks("testForward","testReverse")
        )

      val json3 : String =
        s"""
           |{
           |  "user_id" : "FAKE_OPEN_CONNECT",
           |  "journey_id" : "regID",
           |  "principal_place_of_business_address" : {
           |    "address_line_1": "number L1",
           |    "address_line_2": "L2",
           |    "address_line_3": "L3",
           |    "postal_code": "POCode"
           |  },
           |  "hmrc" : { "foo":"bar" },
           |  "ch" : { "foo":"bar" },
           |  "links": {"forward" : "testForward", "reverse" : "testReverse"}
           |}
       """.stripMargin

      val result = Json.toJson[BusinessActivitiesModel](testModel3)
      result.getClass shouldBe classOf[JsObject]
      result shouldBe Json.parse(json3)
    }
  }

  "Conversion from Core PPOB to Handoff PPOB" should {
    "quick all field check" in {
      val core = PPOB("RO", Some(Address(Some("num"), "L1", "L2", Some("L3"), Some("L4"), Some("pc"), Some("uk"))))
      val handoff = HandoffPPOB("L1", "L2", Some("L3"), Some("L4"), Some("pc"), Some("uk"))
      HandoffPPOB.fromCorePPOB(core) shouldBe handoff
    }

    "No premises" in {
      val core = PPOB("RO", Some(Address(Some(""), "L1", "L2", Some("L3"), Some("L4"), Some("pc"), Some("uk"))))
      val handoff = HandoffPPOB("L1", "L2", Some("L3"), Some("L4"), Some("pc"), Some("uk"))
      HandoffPPOB.fromCorePPOB(core) shouldBe handoff
    }

    "Missing L4 & Postcode" in {
      val core = PPOB("RO", Some(Address(Some("num"), "L1", "L2", Some("L3"), None, None, Some("country"))))
      val handoff = HandoffPPOB("L1", "L2", Some("L3"), None, None, Some("country"))
      HandoffPPOB.fromCorePPOB(core) shouldBe handoff
    }

    "Missing L3 & Country" in {
      val core = PPOB("RO", Some(Address(Some("num"), "L1", "L2", Some("L3"), Some("L4"), Some("pc"), None)))
      val handoff = HandoffPPOB("L1", "L2", Some("L3"), Some("L4"), Some("pc"), None)
      HandoffPPOB.fromCorePPOB(core) shouldBe handoff
    }

    "Missing all optional fields" in {
      val core = PPOB("RO", Some(Address(Some("num"), "L1", "L2", None, None, None, None)))
      val handoff = HandoffPPOB("L1", "L2", None, None, None, None)
      HandoffPPOB.fromCorePPOB(core) shouldBe handoff
    }
  }
}
