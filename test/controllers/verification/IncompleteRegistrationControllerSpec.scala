/*
 * Copyright 2023 HM Revenue & Customs
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

package controllers.verification

import config.AppConfig
import controllers.reg.IncompleteRegistrationController
import helpers.UnitSpec
import mocks.SCRSMocks
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.MessagesApi
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._
import views.html.reg.IncompleteRegistration

class IncompleteRegistrationControllerSpec extends UnitSpec with GuiceOneAppPerSuite with SCRSMocks with MockitoSugar {

  val mockMcc = app.injector.instanceOf[MessagesControllerComponents]


  class Setup {

    val page = app.injector.instanceOf[IncompleteRegistration]

    object TestController extends IncompleteRegistrationController(mockMcc, page) {
      override implicit val appConfig = app.injector.instanceOf[AppConfig]
      override val messagesApi = app.injector.instanceOf[MessagesApi]
    }

  }

  "Sending a GET request to IncompleteRegistrationController" should {
    "return a 200" in new Setup {
      val result = TestController.show(FakeRequest())
      status(result) mustBe OK
    }
  }

  "Sending a POST request to IncompleteRegistrationController" should {
    "return a 303" in new Setup {
      val result = TestController.submit(FakeRequest())
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/register-your-company/relationship-to-company")
    }
  }
}