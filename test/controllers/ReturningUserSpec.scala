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

package controllers

import builders.AuthBuilder
import controllers.reg.ReturningUserController
import helpers.SCRSSpec
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.play.test.WithFakeApplication

class ReturningUserSpec extends SCRSSpec with AuthBuilder with WithFakeApplication {

  class Setup {
    object TestController extends ReturningUserController{
      val createGGWAccountUrl = "CreateGGWAccountURL"
      val compRegFeUrl = "CompRegFEURL"
      val authConnector = mockAuthConnector

    }
  }

  "Sending a GET request to ReturningUserController" should {
    "return a 303 and redirect you to post-sign-in if you are logged in" in new Setup {
      showWithAuthorisedUser(TestController.show) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/register-your-company/post-sign-in")
      }
    }

    "return a 200 and show the page if you are not logged in" in new Setup {
      showWithUnauthorisedUser(TestController.show) {
        result =>
          status(result) shouldBe OK
      }
    }

    "Sending a POST request to ReturningUserController" should {
      "return a 303 and send user to create account page page when they are starting a new registration" in new Setup {

        val result = TestController.submit()(FakeRequest().withFormUrlEncodedBody("returningUser" -> "true"))
        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get should include("CreateGGWAccountURL/government-gateway-registration-frontend")
        redirectLocation(result).get should include("continue=CompRegFEURL%2Fregister-your-company%2Fpost-sign-in")
      }
      "return a 303 and send user to sign-in page when they are not starting a new registration" in new Setup {

        val result = TestController.submit()(FakeRequest().withFormUrlEncodedBody("returningUser" -> "false"))
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/register-your-company/post-sign-in")
      }
    }
  }
}
