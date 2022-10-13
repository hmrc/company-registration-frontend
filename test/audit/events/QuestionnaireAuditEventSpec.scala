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
import models.QuestionnaireModel
import play.api.libs.json.Json


class QuestionnaireAuditEventSpec extends UnitSpec {

  "QuestionnaireAuditEvent" should {
    "return a valid event when instantiated" in {
      val questionnaireModel = QuestionnaireModel("able", Some("why"), "trying", "satisfaction", 1, "recommend", Some("imp"))
      val event = questionnaireModel

      val expectedJson = Json.parse(
        """
          |{
          | "ableToAchieve":"able","whyNotAchieve":"why","tryingToDo":"trying","satisfaction":"satisfaction","meetNeeds":1,"recommendation":"recommend","improvements":"imp"
          |}
        """.stripMargin
      )

      val result = Json.toJson(event)
      result mustBe expectedJson

    }
  }
}
