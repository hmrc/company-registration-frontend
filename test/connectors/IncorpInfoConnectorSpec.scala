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

package connectors

import helpers.SCRSSpec
import mocks.MetricServiceMock
import play.api.libs.json.Json

import scala.concurrent.Future

class IncorpInfoConnectorSpec extends SCRSSpec {

    val iiUrl = "testIIUrl"

    class Setup {
        val connector = new IncorpInfoConnector {
          override val incorpInfoUrl = iiUrl
          override val http = mockWSHttp

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
  }
