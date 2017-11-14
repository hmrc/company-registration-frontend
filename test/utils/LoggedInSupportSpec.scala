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

package utils

import builders.AuthBuilder
import helpers.SCRSSpec
import play.api.mvc.Results
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier

class LoggedInSupportSpec extends SCRSSpec with AuthBuilder {

  override implicit val hc = HeaderCarrier()

  trait Setup {
    val support = new LoggedInSupport {
      override val authConnector = mockAuthConnector
    }
  }

  "LoggedInSupport" should {

    "redirect to post-sign-in if the person is signed in" in new Setup {
      AuthBuilder.mockAuthorisedUser("testUserID", mockAuthConnector)
      val result = support.onlyIfNotSignedIn(Results.Ok)

      val response = await(result)
      response.header.status shouldBe 303
      redirectLocation(result) shouldBe Some("/register-your-company/post-sign-in")
    }

    "return an OK if the person is not signed in" in new Setup {
      AuthBuilder.mockUnauthorisedUser("testUserID", mockAuthConnector)
      val result = support.onlyIfNotSignedIn(Results.Ok)

      val response = await(result)
      response.header.status shouldBe OK
    }
  }
}
