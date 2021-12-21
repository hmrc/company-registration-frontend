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
import controllers.reg.{ControllerErrorHandler, LimitReachedController}
import helpers.{SCRSSpec, UnitSpec}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._
import views.html.reg.{LimitReached => LimitReachedView}
import scala.concurrent.ExecutionContext.Implicits.global

class LimitReachedControllerSpec extends UnitSpec with SCRSSpec with AuthBuilder with GuiceOneAppPerSuite {

  lazy val mockMcc = app.injector.instanceOf[MessagesControllerComponents]
  lazy val mockFrontendAppConfig = app.injector.instanceOf[FrontendAppConfig]
  lazy val mockControllerErrorHandler = app.injector.instanceOf[ControllerErrorHandler]
  lazy val mockLimitReachedView = app.injector.instanceOf[LimitReachedView]

  class Setup {
    val controller = new LimitReachedController(
      mockAuthConnector,
      mockKeystoreConnector,
      mockMcc,
      mockLimitReachedView
    )(
      mockFrontendAppConfig,
      global
    ){
      override val cohoUrl: String = "testGGUrl"
    }
  }

  "LimitReachedController" should {
    "use the correct coho Url" in new Setup {
      controller.cohoUrl shouldBe "testGGUrl"
    }
  }

  "Sending a GET request to LimitReachedController" should {
    "return a 200" in new Setup {
      showWithAuthorisedUser(controller.show) {
        result =>
          status(result) shouldBe OK
      }
    }

    "Sending a POST request to LimitReachedController" should {
      "return a 303" in new Setup {
        val result = controller.submit()(FakeRequest())
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(controller.cohoUrl)
      }
    }
  }
}