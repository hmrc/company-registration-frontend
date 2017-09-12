/*
 * Copyright 2017 HM Revenue & Customs
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

import org.joda.time.DateTime
import play.api.libs.json.{JsResultException, JsSuccess, Json}
import uk.gov.hmrc.play.test.UnitSpec


class PAYEStatusSpec extends UnitSpec {

  "Passing a Z date to PAYEStatus" should {

    "return a JsSuccess" in {

      val dateTime = "2017-05-16T16:01:55Z"
      val dt = DateTime.parse(dateTime)
      val json = Json.parse(
        s"""
          |{
          | "status":"held",
          | "lastUpdate":"$dateTime"
          |}
        """.stripMargin)

      json.validate[PAYEStatus] shouldBe JsSuccess(PAYEStatus("held", Some(dt), None, None, None))
    }
    "return a JsSuccess with a CancelRegUrl" in {
      val dateTime = "2017-05-16T16:01:55Z"
      val dt = DateTime.parse(dateTime)
      val json = Json.parse(
        s"""
           |{
           | "status":"foo",
           | "lastUpdate":"$dateTime",
           | "cancelURL": "foo"
           |}
        """.stripMargin)

      json.validate[PAYEStatus] shouldBe JsSuccess(PAYEStatus("foo", Some(dt), None, Some("foo"), None))
    }

    "return a JsSuccess with a restartURL" in {
      val dateTime = "2017-05-16T16:01:55Z"
      val dt = DateTime.parse(dateTime)
      val json = Json.parse(
        s"""
           |{
           | "status":"foo",
           | "lastUpdate":"$dateTime",
           | "restartURL": "bar"
           |}
        """.stripMargin)

      json.validate[PAYEStatus] shouldBe JsSuccess(PAYEStatus("foo", Some(dt), None, None, Some("bar")))
    }

    "return a JsError when an non ISOFormat date is parsed" in {
      val dateTime = "2017-05-16"
      val json = Json.parse(
        s"""
           |{
           | "status":"held",
           | "lastUpdate":"$dateTime"
           |}
        """.stripMargin)

      val ex = intercept[JsResultException](json.validate[PAYEStatus])
      ex.errors.head._2.head.messages shouldBe Seq("error.expected.date.isoformat")
    }
  }
}
