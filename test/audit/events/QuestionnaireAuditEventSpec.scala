/*
 * Copyright 2021 HM Revenue & Customs
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
import play.api.test.FakeRequest
import uk.gov.hmrc.http.{Authorization, HeaderCarrier, RequestId, SessionId}


class QuestionnaireAuditEventSpec extends UnitSpec {

  "QuestionnaireAuditEvent" should {
    "return a valid event when instantiated" in {
      val f = FakeRequest()
      val h = HeaderCarrier()
      val event = new QuestionnaireAuditEvent(QuestionnaireModel("able", Some("why"), "trying", "satisfaction", 1, "recommend", Some("imp")))(h, f)

      event.detail shouldBe Json.parse("""{"ableToAchieve":"able","whyNotAchieve":"why","tryingToDo":"trying","satisfaction":"satisfaction","meetNeeds":1,"recommendation":"recommend","improvements":"imp"}""")

      event.auditType shouldBe "Questionnaire"
      event.auditSource shouldBe "company-registration-frontend"
      event.tags shouldBe Map("clientIP" -> "-", "path" -> "/", "X-Session-ID" -> "-", "X-Request-ID" -> "-", "deviceID" -> "-", "clientPort" -> "-", "Authorization" -> "-", "transactionName" -> "Questionnaire")

    }
    "return a valid fully populated audit event with a populated HeaderCarrier" in {
      val head = HeaderCarrier(sessionId = Some(SessionId("foo")),
        requestId = Some(RequestId("foo1")),
        trueClientIp = Some("foo2"),
        deviceID = Some("foo3"),
        trueClientPort = Some("foo4"),
        authorization = Some(Authorization("foo5")))

      val fake = FakeRequest("", "pathhere")

      val anotherEvent = new QuestionnaireAuditEvent(QuestionnaireModel("able", Some("why"), "trying", "satisfaction", 1, "recommend", Some("imp")))(head, fake)

      anotherEvent.tags shouldBe Map("clientIP" -> "foo2", "path" -> "pathhere", "X-Session-ID" -> "foo", "X-Request-ID" -> "foo1", "deviceID" -> "foo3", "clientPort" -> "foo4", "Authorization" -> "foo5", "transactionName" -> "Questionnaire")

    }

  }


}
