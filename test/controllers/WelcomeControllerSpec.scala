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

package controllers

import config.FrontendAppConfig
import controllers.reg.WelcomeController
import helpers.SCRSSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.MessagesApi
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._

class WelcomeControllerSpec extends SCRSSpec with GuiceOneAppPerSuite {

  val mockMcc = app.injector.instanceOf[MessagesControllerComponents]

  class Setup {

    object TestController extends WelcomeController(mockMcc) {
      implicit val appConfig: FrontendAppConfig = app.injector.instanceOf[FrontendAppConfig]
      override val messagesApi = app.injector.instanceOf[MessagesApi]
    }

  }

  "Sending a GET request to WelcomeController" should {
    "send the user to post-sign-in if signposting is enabled" when {
      "they show the page" in new Setup {
        val result = TestController.show()(FakeRequest())

        status(result) shouldBe PERMANENT_REDIRECT
        await(result).header.headers.get(LOCATION) shouldBe Some("/register-your-company/setting-up-new-limited-company")
      }
    }
  }

  "Sending a POST request to WelcomeController" should {
    "return a 303 and send user to set up new limited company page when not signed in." in new Setup {
      val result = TestController.submit()(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/register-your-company/setting-up-new-limited-company")
    }
  }
}