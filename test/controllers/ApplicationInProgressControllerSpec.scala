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

import controllers.reg.ApplicationInProgressController
import helpers.SCRSSpec
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.play.test.WithFakeApplication

class ApplicationInProgressControllerSpec extends SCRSSpec with WithFakeApplication {

	class Setup {
		val controller = new ApplicationInProgressController{ }
	}

	"Sending a REDIRECT request to ApplicationInProgressController" should {
		"return a 303" in new Setup {
			val result = controller.redirect()(FakeRequest())
			status(result) shouldBe SEE_OTHER
			redirectLocation(result) shouldBe Some("/register-your-company/application-not-complete")
		}
	}
}
