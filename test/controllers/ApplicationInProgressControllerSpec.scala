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

import controllers.reg.ApplicationInProgressController
import helpers.SCRSSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._

class ApplicationInProgressControllerSpec extends SCRSSpec with GuiceOneAppPerSuite {

  class Setup {
    val controller = new ApplicationInProgressController(app.injector.instanceOf[MessagesControllerComponents]) {}
  }

  "Sending a REDIRECT request to ApplicationInProgressController" should {
    "return a 303" in new Setup {
      val result = controller.redirect()(FakeRequest())
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/register-your-company/sign-in-complete-application")
    }
  }
}