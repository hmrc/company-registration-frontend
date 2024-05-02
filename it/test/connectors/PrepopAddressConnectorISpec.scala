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

package test.connectors

import connectors.PrepopAddressConnector
import test.itutil.IntegrationSpecBase
import test.itutil.servicestubs.BusinessRegistrationStub
import models.{Address, NewAddress}
import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier

class PrepopAddressConnectorISpec extends IntegrationSpecBase with BusinessRegistrationStub {

  lazy val connector: PrepopAddressConnector = app.injector.instanceOf[PrepopAddressConnector]
  implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

  val testRegId = "12345"

  trait PrepopAddressTest {
    val addrLine1 = "line 1"
    val addrLine2 = "line 2"
    val addrLine3 = Some("line 3")
    val addrLine4 = Some("line 4")
    val postcode = Some("AB12 3YZ")
    val country = Some("United Kingdom")
    val testRegId = "12345"

    def prepopAddressSeq(n: Int): Seq[NewAddress] =
      1 to n map (_ => NewAddress(addrLine1, addrLine2, addrLine3, addrLine4, postcode, country))
  }

  s"GET /business-registration/$testRegId/addresses" should {
    "return a list of addresses" when {
      "there are addresses for the specified registration ID" in new PrepopAddressTest {
        val addressSeq = prepopAddressSeq(2)
        stubGetPrepopAddresses(testRegId, OK, addressSeq)

        val result = await(connector.getPrepopAddresses(testRegId)(headerCarrier))

        result mustBe addressSeq
      }
      "there aren't any addresses for the specified registration ID" in new PrepopAddressTest {
        stubGetPrepopAddresses(testRegId, NOT_FOUND, Nil)

        val result = await(connector.getPrepopAddresses(testRegId)(headerCarrier))

        result mustBe empty
      }
    }
    "return None" when {
      "the user is not authorised" in {
        stubGetPrepopAddresses(testRegId, FORBIDDEN, Nil)

        val result = await(connector.getPrepopAddresses(testRegId)(headerCarrier))

        result mustBe empty
      }
      "an unexpected error occurs" in {
        stubGetPrepopAddresses(testRegId, INTERNAL_SERVER_ERROR, Nil)

        val result = await(connector.getPrepopAddresses(testRegId)(headerCarrier))

        result mustBe empty
      }
      "the json is invalid" in new PrepopAddressTest {
        val addressJson = Json.obj()
        stubGetPrepopAddresses(testRegId, OK, addressJson)

        val result = await(connector.getPrepopAddresses(testRegId)(headerCarrier))

        result mustBe empty
      }
    }
  }

  s"POST /business-registration/$testRegId/addresses" should {
    "return true on a success" in new PrepopAddressTest {

      stubPrepopAddressPostResponse(testRegId, OK)

      val postData = NewAddress(addrLine1, addrLine2, addrLine3, addrLine4, postcode, country)
      val result = await(connector.updatePrepopAddress(testRegId, postData))

      result mustBe true
    }
    "return false on a failure" in new PrepopAddressTest {
      stubPrepopAddressPostResponse(testRegId, INTERNAL_SERVER_ERROR)

      val postData = NewAddress(addrLine1, addrLine2, addrLine3, addrLine4, postcode, country)
      val result = await(connector.updatePrepopAddress(testRegId, postData))

      result mustBe false
    }
  }

}
