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

package connectors

import helpers.SCRSSpec
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.http.{ForbiddenException, HeaderCarrier, NotFoundException}


import scala.concurrent.{ExecutionContext, Future}

class AddressLookupConnectorSpec extends SCRSSpec {

  trait Setup {
    val connector = new AddressLookupConnector {
      override implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
      override val addressLookupFrontendURL = "testAddressLookupUrl"
      override val wSHttp = mockWSHttp
    }
  }

  val testAddress = Json.obj("foo" -> "bar")

  "AddressLookupConnector" should {
    "use the correct addressLookupFrontendURL" in new Setup {
      connector.addressLookupFrontendURL mustBe "testAddressLookupUrl"
    }
  }

  "getAddress" should {
    "return an address response" in new Setup {
      mockHttpGet[JsObject]("testUrl", testAddress)

      await(connector.getAddress("123")) mustBe testAddress
    }
    "return a Not found response" in new Setup {
      when(mockWSHttp.GET[JsObject](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[ExecutionContext]))
        .thenReturn(Future.failed(new NotFoundException("")))

      intercept[NotFoundException](await(connector.getAddress("123")))
    }
    "return a Forbidden response" in new Setup {
      when(mockWSHttp.GET[JsObject](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[ExecutionContext]))
        .thenReturn(Future.failed(new ForbiddenException("")))

      intercept[ForbiddenException](await(connector.getAddress("123")))
    }
    "return an Exception response" in new Setup {
      when(mockWSHttp.GET[JsObject](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[ExecutionContext]))
        .thenReturn(Future.failed(new RuntimeException("")))

      intercept[RuntimeException](await(connector.getAddress("123")))
    }
  }

}