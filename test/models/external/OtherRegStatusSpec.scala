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

package models.external

import java.time._

import helpers.UnitSpec
import play.api.libs.json.{JsObject, JsResultException, Json}


class OtherRegStatusSpec extends UnitSpec {

  "Passing a Z date to Status" should {

    "return a JsSuccess" in {

      val json = """ {"status":"","lastUpdate":"1970-01-01T00:00:00","ackRef":"","cancelURL":"","restartURL":""}"""
      val testModel = OtherRegStatus("", Some(LocalDateTime.of(1970, 1, 1, 0, 0, 0, 0)), Some(""), Some(""), Some(""))

      Json.toJson[OtherRegStatus](testModel) mustBe Json.parse(json)
    }
    "return a JsSuccess with a CancelRegUrl" in {
      val json = """ {"status":"","lastUpdate":"1970-01-01T00:00:00","ackRef":"","cancelURL":"foo","restartURL":""}"""
      val testModel = OtherRegStatus("", Some(LocalDateTime.of(1970, 1, 1, 0, 0, 0, 0)), Some(""), Some("foo"), Some(""))

      Json.toJson[OtherRegStatus](testModel) mustBe Json.parse(json)

    }

    "return a JsSuccess with a restartURL" in {
      val json = """ {"status":"","lastUpdate":"1970-01-01T00:00:00","ackRef":"","cancelURL":"","restartURL":"bar"}"""
      val testModel = OtherRegStatus("", Some(LocalDateTime.of(1970, 1, 1, 0, 0, 0, 0)), Some(""), Some(""), Some("bar"))

      Json.toJson[OtherRegStatus](testModel) mustBe Json.parse(json)
    }

    "return a JsError when an non ISOFormat date is parsed" in {
      val dateTime = "2017-05-16"
      val json = Json.parse(
        s"""
           |{
           | "status":"held",
           | "lastUpdate":"$dateTime"
           |}
        """.stripMargin).as[JsObject]

      a[JsResultException] mustBe thrownBy(json.as[OtherRegStatus])

    }
  }
}
