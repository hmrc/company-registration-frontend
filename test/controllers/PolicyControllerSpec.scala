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

package controllers

import config.FrontendAppConfig
import helpers.SCRSSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._
import views.html.policies
import scala.concurrent.ExecutionContext.Implicits.global

class PolicyControllerSpec extends SCRSSpec with GuiceOneAppPerSuite {

  lazy val mockMcc = app.injector.instanceOf[MessagesControllerComponents]
  lazy val mockFrontendAppConfig = app.injector.instanceOf[FrontendAppConfig]
  lazy val mockPolicies = app.injector.instanceOf[policies]


  class Setup {
    val controller = new PolicyController(
      mockMcc,
      mockPolicies
    )
    (
      mockFrontendAppConfig,
      global
    )
  }

  "Sending a GET request to the PolicyController" should {
    "return a 200 when user is unauthenticated" in new Setup {
      val result = controller.policyLinks()(FakeRequest())
      status(result) shouldBe OK
    }
  }
}