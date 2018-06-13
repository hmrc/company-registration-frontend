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

package submissionapi

import com.github.tomakehurst.wiremock.client.WireMock._
import itutil.{FakeAppConfig, IntegrationSpecBase, LoginStub, WiremockHelper}
import play.api.test.FakeApplication

class SubmissionApiISpec extends IntegrationSpecBase with FakeAppConfig with LoginStub {

  override implicit lazy val app = FakeApplication(additionalConfiguration = fakeConfig(
    "application.router" -> "testOnlyDoNotUseInAppConf.Routes"
  ))

  val destinationUrl = "/company-registration/test-only/submission-check"

  "Submission trigger" should {

    "return a 200 response" in {
      setupSimpleAuthMocks()

      stubFor(get(urlMatching(destinationUrl))
        .willReturn(
          aResponse()
              .withStatus(200)
              .withBody("body")
        )
      )

      val response = buildClient("/test-only/submission-check-trigger").get.futureValue
      response.status shouldBe 200
      response.body shouldBe "body"
    }

    "return a 400 response" in {
      setupSimpleAuthMocks()

      stubFor(get(urlMatching(destinationUrl))
        .willReturn(
          aResponse()
            .withStatus(400)
            .withBody("reason")
        )
      )

      val response = buildClient("/test-only/submission-check-trigger").get.futureValue
      response.status shouldBe 400
      response.body shouldBe s"""GET of 'http://localhost:11111/company-registration/test-only/submission-check' returned 400 (Bad Request). Response body 'reason'"""
    }
  }
}
