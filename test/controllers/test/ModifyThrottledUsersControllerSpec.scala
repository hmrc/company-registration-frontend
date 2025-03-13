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

package controllers.test

import helpers.UnitSpec
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class ModifyThrottledUsersControllerSpec extends UnitSpec with MockitoSugar with GuiceOneAppPerSuite {

  val mockHttp: HttpClientV2 = mock[HttpClientV2]
  val mockRequestBuilder: RequestBuilder = mock[RequestBuilder]
  lazy val mockMcc: MessagesControllerComponents = app.injector.instanceOf[MessagesControllerComponents]

  implicit val hc: HeaderCarrier = HeaderCarrier()

  class Setup {
    val controller = new ModifyThrottledUsersController(mockMcc) {
      val crUrl: String = "http://test.url"
      val httpClientV2: HttpClientV2 = mockHttp
    }
  }

  "modifyThrottledUsers" should {

    val jsonResponse = Json.parse(s"""{"users_in" : 5}""")

    "return a 200" in new Setup {
      when(mockHttp.get(ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.execute[JsValue](ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(jsonResponse))

      val result = controller.modifyThrottledUsers(5)(FakeRequest())

      status(result) mustBe OK
    }
  }
}