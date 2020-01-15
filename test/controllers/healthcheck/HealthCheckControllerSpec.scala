/*
 * Copyright 2020 HM Revenue & Customs
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

package controllers.healthcheck

import config.FrontendAppConfig
import helpers.SCRSSpec
import play.api.i18n.MessagesApi
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.WithFakeApplication

class HealthCheckControllerSpec extends SCRSSpec with WithFakeApplication {

  class Setup(featureEnabled: Boolean = false) {
    val controller = new HealthCheckController {
      override def healthCheckFeature: Boolean = featureEnabled
      implicit val messagesApi: MessagesApi = fakeApplication.injector.instanceOf[MessagesApi]
      implicit val appConfig: FrontendAppConfig = fakeApplication.injector.instanceOf[FrontendAppConfig]
    }
  }

  "calling any route" should {
    "return OK" when {
      "feature flag is off" in new Setup(true) {
        status(controller.checkHealth()(FakeRequest())) shouldBe 200
      }
      "feature flag is on and query string is OK" in new Setup {
        status(controller.checkHealth(Some(200))(FakeRequest())) shouldBe 200
      }
    }
    "feature flag is on" when {
      "return ServiceUnavailable when no query is provided" in new Setup {
        status(controller.checkHealth()(FakeRequest())) shouldBe 503
      }
      "return when ServiceUnavailable specified in the query" in new Setup {
        status(controller.checkHealth(Some(503))(FakeRequest())) shouldBe 503
      }
      "return any given input" in new Setup {
        val statuses = List(
          404,
          400,
          500,
          502,
          501,
          999999,
          -1
        )

        statuses foreach { code =>
          status(controller.checkHealth(Some(code))(FakeRequest())) shouldBe code
        }
      }
    }
  }
}