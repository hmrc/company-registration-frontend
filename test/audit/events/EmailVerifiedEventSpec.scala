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

package audit.events

import helpers.UnitSpec
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeRequest

class EmailVerifiedEventSpec extends UnitSpec {

  implicit val req = FakeRequest("GET", "/test-path")

  "emailVerifiedEventDetail" should {
    "output json in the correct format" when {
      "the appropriate case class has been populated" in {
        val sample =
          """
            |{
            |   "externalUserId" : "testEXID",
            |   "authProviderId" : "testAPId",
            |   "journeyId" : "testJourneyId",
            |   "emailAddress" : "foo@bar.wibble",
            |   "isVerifiedEmailAddress" : true,
            |   "previouslyVerified" : true
            |}
          """.stripMargin

        val testModel =
          EmailVerifiedEventDetail(
            "testEXID",
            "testAPId",
            "testJourneyId",
            "foo@bar.wibble",
            true,
            true
          )

        val result = Json.toJson[EmailVerifiedEventDetail](testModel)
        result.getClass mustBe classOf[JsObject]
        result mustBe Json.parse(sample)
      }
    }
  }

  "EmailVerifiedEvent" should {
    "contain the correct field values" when {
      "populated with event detail" in {

        val testModel =
          EmailVerifiedEventDetail(
            "testEXID",
            "testAPId",
            "testJourneyId",
            "foo@bar.wibble",
            true,
            true
          )

        val expectedJson = Json.parse(
          """
            |{
            | "externalUserId": "testEXID",
            | "authProviderId": "testAPId",
            | "journeyId": "testJourneyId",
            | "emailAddress": "foo@bar.wibble",
            | "isVerifiedEmailAddress": true,
            | "previouslyVerified": true
            |}
        """.stripMargin
        )

        val result = Json.toJson[EmailVerifiedEventDetail](testModel)
        result mustBe expectedJson
      }
    }
  }
}
