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

package controllers

import controllers.reg.WelcomeController
import helpers.SCRSSpec
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.play.test.WithFakeApplication

/**
  * Created by crispy on 20/06/16.
  */
class WelcomeControllerSpec extends SCRSSpec with WithFakeApplication {

	class Setup {
		object TestController extends WelcomeController
	}

	"Sending a GET request to WelcomeController" should {
		"return a 200" in new Setup {

			val result = TestController.show()(FakeRequest())
			status(result) shouldBe OK
		}
	}

	"Sending a POST request to WelcomeController" should {
		"return a 303 and send user to returning-user page when not signed in." in new Setup {

			val result = TestController.submit()(FakeRequest())
			status(result) shouldBe SEE_OTHER
			redirectLocation(result) shouldBe Some("/register-your-company/returning-user")
		}
	}

	}
