/*
 * Copyright 2018 HM Revenue & Customs
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
import uk.gov.hmrc.http.{HttpResponse, NotFoundException, Upstream5xxResponse}
import scala.concurrent.Future

class IncorpInfoConnectorSpec extends SCRSSpec {

    val iiUrl = "testIIUrl"

    class Setup {
        val connector = new IncorpInfoConnector {
          override val incorpInfoUrl = iiUrl
          override val http = mockWSHttp
         }
     }

    val transId = "txID-12345"
    val companyProfileUrl = s"$iiUrl/$transId/company-profile"

  "injectTestIncorporationUpdate" should {
    "set up a successful incorporation update" in new Setup {
      mockHttpGet(s"$iiUrl/test-only/add-incorp-update/?txId=$transId&date=2018-1-1&crn=12345678&success=true", Future.successful(HttpResponse(200)))
      val res = await(connector.injectTestIncorporationUpdate(transId, isSuccess = true))
      res shouldBe true
    }
    "set up a rejected incorporation update" in new Setup {
      mockHttpGet(s"$iiUrl/test-only/add-incorp-update/?txId=$transId&date=2018-1-1&success=false", Future.successful(HttpResponse(200)))
      val res = await(connector.injectTestIncorporationUpdate(transId, isSuccess = false))
      res shouldBe true
    }
    "return any exceptions returned by II" in new Setup {
      mockHttpGet(s"$iiUrl/test-only/add-incorp-update/?txId=$transId&date=2018-1-1&success=false", Future.failed(new NotFoundException("404")))
      intercept[NotFoundException](await(connector.injectTestIncorporationUpdate(transId, isSuccess = false)))
    }
  }

  "manuallyTriggerIncorporationUpdate" should {
    "trigger subscriptions to be fired" in new Setup {
      mockHttpGet(s"$iiUrl/test-only/manual-trigger/fireSubs", Future.successful(HttpResponse(200)))
      val res = await(connector.manuallyTriggerIncorporationUpdate)
      res shouldBe true
    }
    "return any exceptions returned by II" in new Setup {
      mockHttpGet(s"$iiUrl/test-only/manual-trigger/fireSubs", Future.failed(new Upstream5xxResponse("502", 502, 502)))
      intercept[Upstream5xxResponse](await(connector.manuallyTriggerIncorporationUpdate))
    }
  }
}