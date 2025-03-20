/*
 * Copyright 2023 HM Revenue & Customs
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

import helpers.SCRSSpec
import play.api.libs.json.Json
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{ForbiddenException, NotFoundException}

import scala.concurrent.ExecutionContext

class AddressLookupConnectorSpec extends SCRSSpec {

  trait Setup {
    val address = "http://testAddressLookupUrl"

    val connector = new AddressLookupConnector {
      override implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
      override val addressLookupFrontendURL = address
      override val httpClientV2: HttpClientV2 = mockHttpClientV2
    }
  }

  val testAddress = Json.obj("foo" -> "bar")

  "AddressLookupConnector" should {
    "use the correct addressLookupFrontendURL" in new Setup {
      connector.addressLookupFrontendURL mustBe address
    }
  }

  "getAddress" should {

    "return an address response" in new Setup {
      mockHttpGET(testAddress)
      await(connector.getAddress("123")) mustBe testAddress
    }

    "return a Not found response" in new Setup {
      mockHttpFailedGET(new NotFoundException(""))
      intercept[NotFoundException](await(connector.getAddress("123")))
    }
    "return a Forbidden response" in new Setup {
      mockHttpFailedGET(new ForbiddenException(""))
      intercept[ForbiddenException](await(connector.getAddress("123")))
    }
    "return an Exception response" in new Setup {
      mockHttpFailedGET(new RuntimeException(""))
      intercept[RuntimeException](await(connector.getAddress("123")))
    }
  }

}