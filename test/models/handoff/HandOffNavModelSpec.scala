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

import helpers.UnitSpec
import play.api.libs.json.{JsObject, Json}

import java.time.Instant
import java.time.temporal.ChronoUnit

class HandOffNavModelSpec extends UnitSpec {

  val handOffNavModel = HandOffNavModel(
    Sender(
      Map(
        "1" -> NavLinks(
          "testForwardLinkFromSender1",
          "testReverseLinkFromSender1"
        ),
        "3" -> NavLinks(
          "testForwardLinkFromSender3",
          "testReverseLinkFromSender3"
        )
      )
    ),
    Receiver(
      Map(
        "0" -> NavLinks(
          "testForwardLinkFromReceiver0",
          "testReverseLinkFromReceiver0"
        ),
        "2" -> NavLinks(
          "testForwardLinkFromReceiver2",
          "testReverseLinkFromReceiver2"
        )
      ),
      Map("testJumpKey" -> "testJumpLink"),
      Some(Json.parse("""{"testCHBagKey": "testValue"}""").as[JsObject])
    )
  )

  val handOffNavModelWithoutOptions = HandOffNavModel(
    Sender(
      Map(
        "1" -> NavLinks(
          "testForwardLinkFromSender1",
          "testReverseLinkFromSender1"
        )
      )
    ),
    Receiver(
      Map(
        "0" -> NavLinks(
          "testForwardLinkFromReceiver0",
          "testReverseLinkFromReceiver0"
        )
      ),
      Map.empty[String, String]
    )
  )

  val modelAsJson = Json.parse(
    """{
      |"sender":{
      | "nav":{
      |  "1":{
      |   "forward":"testForwardLinkFromSender1",
      |   "reverse":"testReverseLinkFromSender1"
      |  },
      |  "3":{
      |   "forward":"testForwardLinkFromSender3",
      |   "reverse":"testReverseLinkFromSender3"
      |  }
      | }
      |},
      |"receiver":{
      | "nav":{
      |  "0":{
      |   "forward":"testForwardLinkFromReceiver0",
      |   "reverse":"testReverseLinkFromReceiver0"
      |  },
      |  "2":{
      |   "forward":"testForwardLinkFromReceiver2",
      |   "reverse":"testReverseLinkFromReceiver2"
      |  }
      | },
      | "jump":{
      |  "testJumpKey":"testJumpLink"
      | },
      | "chData":{
      |  "testCHBagKey":"testValue"
      | }
      |}
      |}
      """.stripMargin)

  val modelAsJsonWithMissingField = Json.parse(
    """{
      |"receiver":{
      | "nav":{
      |  "0":{
      |   "forward":"testForwardLinkFromReceiver0",
      |   "reverse":"testReverseLinkFromReceiver0"
      |  }
      | },
      | "jump":{
      |  "":""
      | },
      | "chData":{
      |  "testCHBagKey":"testValue"
      | }
      |}
      |}
    """.stripMargin)

  val modelAsJsonWithoutOptions = Json.parse(
    """{
      |"sender":{
      | "nav":{
      |  "1":{
      |   "forward":"testForwardLinkFromSender1",
      |   "reverse":"testReverseLinkFromSender1"
      |  }
      | }
      |},
      |"receiver":{
      | "nav":{
      |  "0":{
      |   "forward":"testForwardLinkFromReceiver0",
      |   "reverse":"testReverseLinkFromReceiver0"
      |  }
      | },
      | "jump":{}
      |}
      |}
    """.stripMargin)

  "HandOffNavModel" should {

    "be able to write to json" in {
      val modelToJson = Json.toJson(handOffNavModel)
      modelToJson mustBe modelAsJson
    }

    "be able to read from json" in {
      val modelFromJson = Json.fromJson[HandOffNavModel](modelAsJson)
      modelFromJson.get mustBe handOffNavModel
    }

    "be able to read from json when jump links and ch data are non existent" in {
      val modelFromJson = Json.fromJson[HandOffNavModel](modelAsJsonWithoutOptions)
      modelFromJson.get mustBe handOffNavModelWithoutOptions
    }

    "save CH data as json" in {
      val modelFromJson = Json.fromJson[HandOffNavModel](modelAsJson)
      modelFromJson.get.receiver.chData.get.getClass mustBe classOf[JsObject]
    }

    "make sure json in CH data is the same when read from json" in {
      val modelFromJson = Json.fromJson[HandOffNavModel](modelAsJson)
      modelFromJson.get.receiver.chData.get mustBe Json.parse("""{"testCHBagKey": "testValue"}""")
    }
  }

  "MongoHandOffNavModel" should {

    val regId = "ABCD1234"
    val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)

    val mongoHandOffNavModel = MongoHandOffNavModel(regId, handOffNavModel, now)

    val mongoModelAsJson = Json.obj(
      "_id" -> regId,
      "HandOffNavigation" -> modelAsJson,
      "lastUpdated" -> Json.obj(
        "$date" -> Json.obj(
          "$numberLong" -> now.toEpochMilli.toString
        )
      )
    )

    "be able to serialize to json" in {
      Json.toJson(mongoHandOffNavModel) mustBe mongoModelAsJson
    }

    "be able to deserialize from json" in {
      mongoModelAsJson.as[MongoHandOffNavModel] mustBe mongoHandOffNavModel
    }
  }
}
