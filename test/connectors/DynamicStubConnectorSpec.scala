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

import fixtures.BusinessRegistrationFixture
import helpers.SCRSSpec
import models.test.ETMPNotification
import play.api.http.Status.OK
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http._

import scala.concurrent.{ExecutionContext, Future}

class DynamicStubConnectorSpec extends SCRSSpec with BusinessRegistrationFixture {

  val mockDynStubConnector = mock[DynamicStubConnectorImpl]

  class Setup {
    val connector = new DynamicStubConnector {
      override val busRegDyUrl = "testBusinessRegUrl"
      override val wSHttp = mockWSHttp
      override implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    }
  }

  val registrationId = "reg-12345"
  val etmp = ETMPNotification("", "", "", Some(""), "")

  "DynamicStubConnector" should {
    "use the correct businessRegUrl" in new Setup {
      connector.busRegDyUrl mustBe "testBusinessRegUrl"
    }
  }

  "postETMPNotificationData" should {
    "make a http POST request to post ETMP Notification" in new Setup {
      mockHttpPOST[JsValue, HttpResponse](s"${connector.busRegDyUrl}/cache-etmp-notification", HttpResponse(OK, json = Json.obj(), Map()))

      await(connector.postETMPNotificationData(etmp)).status mustBe OK
    }
  }

  "simulateDesPost" should {
    "make a http GET request to simulate DES Post" in new Setup {
      mockHttpGet(s"${connector.busRegDyUrl}/simulate-des-post/12345", Future.successful(HttpResponse(200, "")))
      val res = await(connector.simulateDesPost("12345")).status
      res mustBe OK
    }
  }
}
