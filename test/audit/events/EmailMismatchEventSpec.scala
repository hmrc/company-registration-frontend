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

import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeRequest
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent
import uk.gov.hmrc.play.test.UnitSpec

class EmailMismatchEventSpec extends UnitSpec {

  implicit val req = FakeRequest("GET", "/test-path")

  "emailMismatchEventDetail" should {
    "output json in the correct format" when {
      "the appropriate case class has been populated" in {
        val sample =
          """
            |{
            |   "externalUserId" : "testEXID",
            |   "authProviderId" : "testAPId",
            |   "journeyId" : "testJourneyId"
            |}
          """.stripMargin

        val testModel =
          EmailMismatchEventDetail(
            "testEXID",
            "testAPId",
            "testJourneyId"
          )

        val result = Json.toJson[EmailMismatchEventDetail](testModel)
        result.getClass shouldBe classOf[JsObject]
        result shouldBe Json.parse(sample)
      }
    }
  }

  "EmailMismatchEvent" should {
    "contain the correct field values" when {
      "populated with event detail" in {

        implicit val hc = HeaderCarrier()
        implicit val format = Json.format[ExtendedDataEvent]

        val testModel =
          EmailMismatchEventDetail(
            "testEXID",
            "testAPId",
            "testJourneyId"
          )

        val auditEvent = new EmailMismatchEvent(testModel)(hc, req)

        val result = Json.toJson[ExtendedDataEvent](auditEvent)
        result.getClass shouldBe classOf[JsObject]
        (result \ "auditSource").as[String] shouldBe "company-registration-frontend"
        (result \ "auditType").as[String] shouldBe "emailMismatch"
      }
    }
  }
}
