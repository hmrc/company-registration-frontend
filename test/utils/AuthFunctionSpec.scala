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

package utils

import builders.AuthBuilder
import controllers.auth.AuthFunction
import fixtures.LoginFixture
import helpers.SCRSSpec
import play.api.mvc.Results
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.WithFakeApplication

class AuthFunctionSpec extends SCRSSpec with AuthBuilder with WithFakeApplication with LoginFixture {

  override implicit val hc = HeaderCarrier()
  implicit val fr = FakeRequest()

  trait Setup {
    val support = new AuthFunction {
      override def authConnector = mockAuthConnector
    }
  }

  "LoggedInSupport" should {
    "redirect to post-sign-in if the person is signed in" in new Setup {
      mockAuthorisedUser({})
      val result = support.onlyIfNotSignedIn(Results.Ok)

      val response = await(result)
      response.header.status shouldBe 303
      redirectLocation(result) shouldBe Some("/register-your-company/post-sign-in")
    }

    "return an OK if the person is not signed in" in new Setup {
      mockUnauthorisedUser()
      val result = support.onlyIfNotSignedIn(Results.Ok)

      val response = await(result)
      response.header.status shouldBe OK
    }
  }

  "ctAuthorised" should {
    "redirect to OK if the person is signed in" in new Setup {
      mockAuthorisedUser({})
      val result = support.ctAuthorised(Results.Ok)

      val response = await(result)
      response.header.status shouldBe 200
    }

    "return to post-sign-in if the person is not signed in" in new Setup {
      mockUnauthorisedUser()
      val result = support.ctAuthorised(Results.Ok)

      val response = await(result)
      response.header.status shouldBe 303
      redirectLocation(result) shouldBe Some(authUrl)
    }

    "return an internal server error if an auth error occurs" in new Setup {
      mockAuthFailure()
      val result = support.ctAuthorised(Results.Ok)

      val response = await(result)
      response.header.status shouldBe 500
    }
  }

  "ctAuthorisedHandoff" should {
    "redirect to OK if the person is signed in" in new Setup {
      mockAuthorisedUser({})
      val result = support.ctAuthorisedHandoff("h", "p")(Results.Ok)

      val response = await(result)
      response.header.status shouldBe 200
    }

    "return to post-sign-in with a continue to a handoff if the person is not signed in" in new Setup {
      mockUnauthorisedUser()
      val result = support.ctAuthorisedHandoff("h", "p")(Results.Ok)

      val response = await(result)
      response.header.status shouldBe 303
      redirectLocation(result) shouldBe Some(authUrl("h", "p"))
    }
  }
}
