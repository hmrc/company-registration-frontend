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
import config.FrontendAuthConnector
import connectors.KeystoreConnector
import controllers.reg.LimitReachedController
import helpers.SCRSSpec
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

class LimitReachedControllerSpec extends UnitSpec with WithFakeApplication with SCRSSpec with AuthBuilder {



  class Setup {
    val controller = new LimitReachedController {
      val cohoUrl: String = "testGGUrl"
      val authConnector = mockAuthConnector
      val keystoreConnector = mockKeystoreConnector
      override val appConfig = mockAppConfig
    }
  }

  "LimitReachedController" should {
    "use the correct coho Url" in {
      LimitReachedController.cohoUrl shouldBe "http://resources.companieshouse.gov.uk/promo/webincs"
    }
    "use the correct auth connector" in {
      LimitReachedController.authConnector shouldBe FrontendAuthConnector
    }
    "use the correct keystore connector" in {
      LimitReachedController.keystoreConnector shouldBe KeystoreConnector
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
