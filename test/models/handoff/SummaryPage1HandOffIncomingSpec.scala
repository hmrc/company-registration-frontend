/*
 * Copyright 2019 HM Revenue & Customs
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

import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.play.test.UnitSpec

class SummaryPage1HandOffIncomingSpec extends UnitSpec {

  class Setup {
  }

  "SummaryPage1HandOffIncoming model" should {
    "Be able to parsed from simple JSON" in new Setup {

      val json1: String =
        s"""
           |{
           |  "user_id" : "FAKE_OPEN_CONNECT",
           |  "journey_id" : "regID",
           |  "hmrc" : { "foo":"bar" },
           |  "ch" : { "foo":"bar" },
           |  "links" : { "forward":"test", "reverse":"test2" }
           |}
       """.stripMargin

      val testModel1 =
      SummaryPage1HandOffIncoming(
        "FAKE_OPEN_CONNECT",
        "regID",
        JsObject(Seq("foo" -> Json.toJson("bar"))),
        JsObject(Seq("foo" -> Json.toJson("bar"))),
        NavLinks("test", "test2")
      )

      val result = Json.parse(json1).as[SummaryPage1HandOffIncoming]
      result.getClass shouldBe classOf[SummaryPage1HandOffIncoming]
      result shouldBe testModel1
    }
  }
}
