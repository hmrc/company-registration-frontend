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

package connectors

import helpers.SCRSSpec
import org.mockito.Matchers
import org.mockito.Mockito._
import play.api.i18n.MessagesApi
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Call
import uk.gov.hmrc.http.{ForbiddenException, HeaderCarrier, HttpResponse, NotFoundException}
import uk.gov.hmrc.play.test.WithFakeApplication

import scala.concurrent.{ExecutionContext, Future}

class AddressLookupConnectorSpec extends SCRSSpec with WithFakeApplication {

  trait Setup {
    val connector = new AddressLookupConnector {
      override val addressLookupFrontendURL = "testAddressLookupUrl"
      override val wSHttp = mockWSHttp
    }
  }

  val testAddress = Json.obj("foo" -> "bar")

  "AddressLookupConnector" should {
    "use the correct addressLookupFrontendURL" in new Setup {
      connector.addressLookupFrontendURL shouldBe "testAddressLookupUrl"
    }
  }

  "getAddress" should {
    "return an address response" in new Setup {
      mockHttpGet[JsObject]("testUrl", testAddress)

      await(await(connector.getAddress("123"))) shouldBe testAddress
    }
    "return a Not found response" in new Setup {
      when(mockWSHttp.GET[JsObject](Matchers.anyString())(Matchers.any(), Matchers.any[HeaderCarrier](), Matchers.any[ExecutionContext]))
        .thenReturn(Future.failed(new NotFoundException("")))

      intercept[NotFoundException](await(connector.getAddress("123")))
    }
    "return a Forbidden response" in new Setup {
      when(mockWSHttp.GET[JsObject](Matchers.anyString())(Matchers.any(), Matchers.any[HeaderCarrier](), Matchers.any[ExecutionContext]))
        .thenReturn(Future.failed(new ForbiddenException("")))

      intercept[ForbiddenException](await(connector.getAddress("123")))
    }
    "return an Exception response" in new Setup {
      when(mockWSHttp.GET[JsObject](Matchers.anyString())(Matchers.any(), Matchers.any[HeaderCarrier](), Matchers.any[ExecutionContext]))
        .thenReturn(Future.failed(new RuntimeException("")))

      intercept[RuntimeException](await(connector.getAddress("123")))
    }
  }

  "getOnRampURL" should {

    val redirectUrl = "test/return/Url"
    val call = Call("Redirect", redirectUrl)
    def alfResponse(withHeader: Boolean = true) = HttpResponse(200, None, responseHeaders = if(withHeader) Map("Location" -> List(redirectUrl)) else Map())

    "return url" in new Setup {
      mockHttpPOST[JsObject, HttpResponse]("testUrl", alfResponse())

      await(await(connector.getOnRampURL(Json.obj()))) shouldBe redirectUrl
    }
    "return an ALFLocationHeaderNotSet" in new Setup {
      mockHttpPOST[JsObject, HttpResponse]("testUrl", alfResponse(false))

      intercept[ALFLocationHeaderNotSet](await(connector.getOnRampURL(Json.obj())))
    }
  }
}