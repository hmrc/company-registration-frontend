/*
 * Copyright 2021 HM Revenue & Customs
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

import builders.AuthBuilder
import config.FrontendAppConfig
import controllers.reg.ReturningUserController
import helpers.SCRSSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._
import views.html.reg.ReturningUserView
import scala.concurrent.ExecutionContext.Implicits.global

class ReturningUserSpec extends SCRSSpec with AuthBuilder with GuiceOneAppPerSuite {
  lazy val mockMcc = app.injector.instanceOf[MessagesControllerComponents]
  lazy val mockFrontendAppConfig = app.injector.instanceOf[FrontendAppConfig]
  lazy val mockReturningUserView = app.injector.instanceOf[ReturningUserView]

  class Setup {

    val testController = new ReturningUserController(
      mockAuthConnector,
      mockMcc,
      mockReturningUserView
    )(
      global,
      mockFrontendAppConfig
    ) {
      override lazy val createGGWAccountUrl = "CreateGGWAccountURL"
      override lazy val eligUri = "/eligibility-for-setting-up-company"
      override lazy val eligBaseUrl = "EligURL"
      override lazy val compRegFeUrl = "CompRegFEURL"
    }
  }

  "Sending a GET request to ReturningUserController" should {
    "return a 200 and show the page if you are logged in" in new Setup {
      showWithAuthorisedUser(testController.show) {
        result =>
          status(result) shouldBe OK
      }
    }

    "return a 200 and show the page if you are not logged in" in new Setup {
      showWithUnauthorisedUser(testController.show) {
        result =>
          status(result) shouldBe OK
      }
    }

    "Sending a POST request to ReturningUserController" should {
      "return a 303 and send user to company registration eligibility when they start a new registration" in new Setup {

        val result = testController.submit()(FakeRequest().withFormUrlEncodedBody("returningUser" -> "true"))
        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get should include("EligURL")
        redirectLocation(result).get should include("/eligibility-for-setting-up-company")
      }
      "return a 303 and send user to sign-in page when they are not starting a new registration" in new Setup {

        val result = testController.submit()(FakeRequest().withFormUrlEncodedBody("returningUser" -> "false"))
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/register-your-company/post-sign-in")
      }
    }
  }
}