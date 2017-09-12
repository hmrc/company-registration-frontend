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

package audit.events

import models.CHROAddress
import play.api.libs.json.{JsDefined, JsString, JsValue, Json}
import play.api.test.FakeRequest
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

class ROUsedAsPPOBAuditEventSpec extends UnitSpec {

  implicit val req = FakeRequest("GET", "/test-path")

  "ROUsedAsPPOBAuditEventDetail" should {

    "construct full Json as per definition" in {
      val expected = Json.parse(
        """
          |{
          | "authProviderId": "credId12345",
          | "journeyId": "regId12345",
          | "companyName": "testCompanyName",
          | "registeredOfficeAddress": {
          |    "premises" : "testPremises",
          |    "addressLine1" : "testAddressLine1",
          |    "addressLine2" : "testAddressLine2",
          |    "locality" : "testLocality",
          |    "postCode" : "testPostcode",
          |    "region" : "testRegion"
          | }
          |}
        """.stripMargin)

      val model = ROUsedAsPPOBAuditEventDetail(
        "regId12345", "credId12345", "testCompanyName",
        CHROAddress(
          "testPremises",
          "testAddressLine1",
          Some("testAddressLine2"),
          "testLocality",
          "UK",
          None,
          Some("testPostcode"),
          Some("testRegion")
        )
      )

      val result = Json.toJson(model)
      result shouldBe expected
    }

    "leave out optional fields" in {
      val expected = Json.parse(
        """
          |{
          | "authProviderId": "credId12345",
          | "journeyId": "regId12345",
          | "companyName": "testCompanyName",
          | "registeredOfficeAddress": {
          |    "premises" : "testPremises",
          |    "addressLine1" : "testAddressLine1",
          |    "locality" : "testLocality"
          | }
          |}
        """.stripMargin)

      val model = ROUsedAsPPOBAuditEventDetail(
        "regId12345", "credId12345", "testCompanyName",
        CHROAddress(
          "testPremises",
          "testAddressLine1",
          None,
          "testLocality",
          "UK",
          None, None, None
        )
      )

      val result = Json.toJson(model)
      result shouldBe expected
    }
  }

  "ROUsedAsPPOBAuditEvent" should {

    "have correct auditType and detail" in {
      val detail = ROUsedAsPPOBAuditEventDetail(
        "regId12345", "credId12345", "testCompanyName",
        CHROAddress(
          "testPremises",
          "testAddressLine1",
          Some("testAddressLine2"),
          "testLocality",
          "UK",
          None,
          Some("testPostcode"),
          Some("testRegion")
        )
      )

      val auditEvent = new ROUsedAsPPOBAuditEvent(detail)(HeaderCarrier(), req)
      val result = Json.toJson(auditEvent)

      (result \ "auditType").as[String] shouldBe "registeredOfficeUsedAsPrincipalPlaceOfBusiness"
      (result \ "detail") shouldBe JsDefined(Json.toJson(detail))
    }
  }
}
