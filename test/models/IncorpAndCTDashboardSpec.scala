/*
 * Copyright 2020 HM Revenue & Customs
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

class IncorpAndCTDashboardSpec extends UnitSpec {

  "IncorpAndCTDashboard" should {

    val baseJson = Json.parse(
      """
        |{
        |    "internalId" : "Int-xxx-xxx-xxx",
        |    "registrationID" : "1",
        |    "status" : "acknowledged",
        |    "formCreationTimestamp" : "2017-04-24T17:02:09+01:00",
        |    "language" : "en",
        |    "registrationProgress" : "HO5",
        |    "confirmationReferences" : {
        |        "acknowledgement-reference" : "ABCD00000000001",
        |        "transaction-id" : "TRANS_ID-12345",
        |        "payment-reference" : "PAY_REF-123456789",
        |        "payment-amount" : "12"
        |    },
        |    "accountingDetails" : {
        |        "accountingDateStatus" : "WHEN_REGISTERED"
        |    },
        |    "accountsPreparation" : {
        |        "businessEndDateChoice" : "HMRC_DEFINED"
        |    },
        |    "verifiedEmail" : {
        |        "address" : "foo@bar.com",
        |        "type" : "GG",
        |        "link-sent" : true,
        |        "verified" : true,
        |        "return-link-email-sent" : true
        |    },
        |    "createdTime" : 1493049729307,
        |    "lastSignedIn" : 1493049749208
        |}
      """.stripMargin).as[JsObject]

    "read into a case class as expected when there are acknowledgement references" in {

      val expected = IncorpAndCTDashboard("acknowledged", None, Some("TRANS_ID-12345"), Some("PAY_REF-123456789"), None, None, Some("ABCD00000000001"), Some("01"), Some("xxx"))

      val json = baseJson ++ Json.parse(
        """
          |{
          |    "acknowledgementReferences" : {
          |        "ctUtr" : "xxx",
          |        "timestamp" : "2016-11-12T13:45:29Z",
          |        "status" : "01"
          |    }
          |}
        """.stripMargin).as[JsObject]

      json.as[IncorpAndCTDashboard](IncorpAndCTDashboard.reads(None)) shouldBe expected

      val res = json.as[JsObject]
      res.value.exists(_._1 == "acknowledgementReferences") shouldBe true
    }

    "read into a case class as expected when there are no acknowledgement references" in {

      val expected = IncorpAndCTDashboard("acknowledged", None, Some("TRANS_ID-12345"), Some("PAY_REF-123456789"), None, None, Some("ABCD00000000001"), None, None)

      val json = baseJson

      json.as[IncorpAndCTDashboard](IncorpAndCTDashboard.reads(None)) shouldBe expected

      val res = json.as[JsObject]
      res.value.exists(_._1 == "acknowledgementReferences") shouldBe false
    }
  }
}
