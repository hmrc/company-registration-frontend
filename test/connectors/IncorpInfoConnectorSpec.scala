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
import mocks.MetricServiceMock
import play.api.libs.json.Json
import uk.gov.hmrc.http.{HttpResponse, NotFoundException, Upstream5xxResponse}

import scala.concurrent.Future

class IncorpInfoConnectorSpec extends SCRSSpec {

    val iiUrl = "testIIUrl"

    class Setup {
        val connector = new IncorpInfoConnector {
          override val incorpInfoUrl = iiUrl
          override val wSHttp = mockWSHttp
          override val metricsService = MetricServiceMock
         }
     }

    val transId = "txID-12345"
    val companyProfileUrl = s"$iiUrl/$transId/company-profile"

  "getCompanyProfile" should {

    val json = Json.parse("""{"test":"json"}""")

    "return a Json object" in new Setup {
      mockHttpGet(companyProfileUrl, Future.successful(json))
      val res = await(connector.getCompanyProfile(transId))
      res shouldBe json
    }
  }

  "getCompanyName" should {

    val json = Json.parse("""{"company_name":"testCompanyName"}""")

    "return a company name from the fetched json" in new Setup {
      mockHttpGet(companyProfileUrl, Future.successful(json))
      val res = await(connector.getCompanyName(transId))
      res shouldBe "testCompanyName"
    }
  }

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
    "recover any exceptions returned by II" in new Setup {
      mockHttpGet(s"$iiUrl/test-only/add-incorp-update/?txId=$transId&date=2018-1-1&success=false", Future.failed(new NotFoundException("404")))
      val res = await(connector.injectTestIncorporationUpdate(transId, isSuccess = false))
      res shouldBe false
    }
  }

  "manuallyTriggerIncorporationUpdate" should {
    "trigger subscriptions to be fired" in new Setup {
      mockHttpGet(s"$iiUrl/test-only/manual-trigger/fireSubs", Future.successful(HttpResponse(200)))
      val res = await(connector.manuallyTriggerIncorporationUpdate)
      res shouldBe true
    }
    "persist any exceptions returned by II" in new Setup {
      mockHttpGet(s"$iiUrl/test-only/manual-trigger/fireSubs", Future.failed(new Upstream5xxResponse("502", 502, 502)))
      val res = await(connector.manuallyTriggerIncorporationUpdate)
      res shouldBe false
    }
  }
}


