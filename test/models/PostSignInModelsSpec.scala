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

import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.play.test.UnitSpec


class PostSignInModelsSpec extends UnitSpec {

  "UserAccessModel" should {
    "With no email, be able to be parsed into JSON" in {

      val json : String =
        s"""
           |{
           |  "registration-id" : "regID",
           |  "created" : true,
           |  "confirmation-reference": false
           |}
       """.stripMargin

      val expected =
        ThrottleResponse(
          "regID",
          created= true,
          confRefs = false
        )

      Json.parse(json).as[ThrottleResponse] shouldBe expected
    }

    "With email, be able to be parsed into JSON" in {

      val json : String =
        s"""
           |{
           |  "registration-id" : "regID",
           |  "created" : true,
           |  "confirmation-reference": false,
           |  "email": { "address": "a@a.a", "type": "GG", "link-sent": true, "verified": false , "return-link-email-sent" : false}
           |}
       """.stripMargin

      val expected =
        ThrottleResponse(
          "regID",
          created= true,
          confRefs = false,
          emailData = Some(Email("a@a.a", "GG", true, false, false))
        )

      Json.parse(json).as[ThrottleResponse] shouldBe expected
    }
  }

  "EmailVerificationRequest" should {
    "Create a valid email verification request with no parameters" in {

      val json : String =
        s"""
           |{
           |  "email": "a@a.com",
           |  "templateId": "wibble",
           |  "templateParameters": {},
           |  "linkExpiryDuration" : "P2D",
           |  "continueUrl" : "http://a/b/c"
           |}
       """.stripMargin

      val expected =
        EmailVerificationRequest(
          "a@a.com",
          "wibble",
          Map(),
          "P2D",
          "http://a/b/c"
        )

      val result = Json.toJson[EmailVerificationRequest](expected)
      result.getClass shouldBe classOf[JsObject]
      result shouldBe Json.parse(json)
    }

    "Create a valid email verification request with some parameters" in {

      val json : String =
        s"""
           |{
           |  "email": "a@a.com",
           |  "templateId": "wibble",
           |  "templateParameters": {"name":"xxx","foo":"bar"},
           |  "linkExpiryDuration" : "P2D",
           |  "continueUrl" : "http://a/b/c"
           |}
       """.stripMargin

      val expected =
        EmailVerificationRequest(
          "a@a.com",
          "wibble",
          Map(("name"->"xxx"),("foo","bar")),
          "P2D",
          "http://a/b/c"
        )

      val result = Json.toJson[EmailVerificationRequest](expected)
      result.getClass shouldBe classOf[JsObject]
      result shouldBe Json.parse(json)
    }
  }
}
