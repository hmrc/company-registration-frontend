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

package controllers.test

import org.mockito.Matchers
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.{JsValue, Json}
import play.api.test.FakeRequest
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet}
import uk.gov.hmrc.play.http.ws.WSHttp
import uk.gov.hmrc.play.test.UnitSpec
import org.mockito.Mockito._
import play.api.test.Helpers._

import scala.concurrent.Future

class ModifyThrottledUsersControllerSpec extends UnitSpec with MockitoSugar {

  val mockHttp = mock[WSHttp]

  implicit val hc = HeaderCarrier()

  class Setup {
    val controller = new ModifyThrottledUsersController {
      val crUrl: String = "test.url"
      val http: HttpGet = mockHttp
    }
  }

  "modifyThrottledUsers" should {

    val jsonResponse = Json.parse(s"""{"users_in" : 5}""")

    "return a 200" in new Setup {
      when(mockHttp.GET[JsValue](Matchers.anyString())(Matchers.any(), Matchers.any())).thenReturn(Future.successful(jsonResponse))

      val result = controller.modifyThrottledUsers(5)(FakeRequest())

      status(result) shouldBe OK
    }
  }
}
