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

package controllers.verification

import controllers.reg.IncompleteRegistrationController
import mocks.SCRSMocks
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import play.api.i18n.MessagesApi
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

class IncompleteRegistrationControllerSpec extends UnitSpec with WithFakeApplication with SCRSMocks with MockitoSugar {

  class Setup {
    object TestController extends IncompleteRegistrationController {
      override val appConfig = mockAppConfig
      override val messagesApi = fakeApplication.injector.instanceOf[MessagesApi]
    }
    when(mockAppConfig.piwikURL).thenReturn(None)
  }

  "Sending a GET request to IncompleteRegistrationController" should {
    "return a 200" in new Setup {
      val result = TestController.show()(FakeRequest())
      status(result) shouldBe OK
    }
  }

  "Sending a POST request to IncompleteRegistrationController" should {
    "return a 303" in new Setup {
      val result = TestController.submit()(FakeRequest())
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/register-your-company/relationship-to-company")
    }
  }
}