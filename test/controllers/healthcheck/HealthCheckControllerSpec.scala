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

package controllers.healthcheck

import config.AppConfig
import helpers.SCRSSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import views.html.healthcheck.HealthCheck

class HealthCheckControllerSpec extends SCRSSpec with GuiceOneAppPerSuite {

  val mockMcc = app.injector.instanceOf[MessagesControllerComponents]
  val view = app.injector.instanceOf[HealthCheck]
  class Setup() {
    val controller = new HealthCheckController(mockMcc, view) {
      implicit override val appConfig: AppConfig = app.injector.instanceOf[AppConfig]
    }
  }

  "calling any route" should {
    "return OK" when {
      "checking health" in new Setup() {
        status(controller.checkHealth()(FakeRequest())) mustBe 200
      }
    }
  }
}