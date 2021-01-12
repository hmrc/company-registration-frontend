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

package connectors

import itutil.IntegrationSpecBase
import itutil.servicestubs.TakeoverStub
import models.TakeoverDetails
import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier

class TakeoverConnectorISpec extends IntegrationSpecBase with TakeoverStub {

  lazy val takeoverConnector: TakeoverConnector = app.injector.instanceOf[TakeoverConnector]
  private implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

  val testRegistrationId = "12345"
  val testBusinessName = "Foo"
  val testTakeoverDetails = TakeoverDetails(
    replacingAnotherBusiness = true,
    businessName = Some(testBusinessName),
    businessTakeoverAddress = None,
    previousOwnersName = None,
    previousOwnersAddress = None
  )

  "getTakeoverDetails" should {
    "return the retrieved takeover details" when {
      "company registration returns successful date" in {
        stubGetTakeoverDetails(testRegistrationId, OK, Some(testTakeoverDetails))

        val res = await(takeoverConnector.getTakeoverDetails(testRegistrationId))

        res should contain(testTakeoverDetails)
      }
    }
    "return None" when {
      "company registration returns a NO_CONTENT" in {
        stubGetTakeoverDetails(testRegistrationId, NO_CONTENT)

        val res = await(takeoverConnector.getTakeoverDetails(testRegistrationId))

        res shouldBe empty
      }
      "company registration returns invalid JSON" in {
        stubGetTakeoverDetails(testRegistrationId, OK)

        val res = await(takeoverConnector.getTakeoverDetails(testRegistrationId))

        res shouldBe empty
      }
    }
  }

  "putTakeoverDetails" should {
    "return the retrieved takeover details" when {
      "company registration returns successful date" in {
        stubPutTakeoverDetails(testRegistrationId, OK, testTakeoverDetails)

        val res = await(takeoverConnector.updateTakeoverDetails(testRegistrationId, testTakeoverDetails))

        res shouldBe testTakeoverDetails
      }
    }
    "throw an exception" when {
      "company registration returns any other status" in {
        stubPutTakeoverDetails(testRegistrationId, INTERNAL_SERVER_ERROR, testTakeoverDetails)

        intercept[Exception](await(takeoverConnector.updateTakeoverDetails(testRegistrationId, testTakeoverDetails)))
      }
      "company registration returns invalid JSON" in {
        stubPut(takeoverUrl(testRegistrationId), Json.toJson(testTakeoverDetails).toString)(OK, "{}")

        intercept[Exception](await(takeoverConnector.updateTakeoverDetails(testRegistrationId, testTakeoverDetails)))
      }
    }
  }
}
