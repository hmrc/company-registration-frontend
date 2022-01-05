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

package www.takeovers

import java.util.UUID

import fixtures.Fixtures
import forms.takeovers.OtherBusinessNameForm._
import itutil.servicestubs.TakeoverStub
import itutil.{IntegrationSpecBase, LoginStub, RequestsFinder}
import models.TakeoverDetails
import org.jsoup.Jsoup
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.HeaderNames
import play.api.libs.crypto.DefaultCookieSigner
import play.api.libs.ws.WSResponse
import play.api.test.Helpers._

class OtherBusinessNameControllerISpec extends IntegrationSpecBase with LoginStub with MockitoSugar with RequestsFinder with TakeoverStub with Fixtures {

  class Setup {
    val userId: String = "testUserId"
    val testRegId: String = "testRegId"
    val testBusinessName: String = "test name"
    lazy val csrfToken: String = UUID.randomUUID().toString
    lazy val sessionCookie: String = getSessionCookie(Map("csrfToken" -> csrfToken), userId)
  }

  lazy val defaultCookieSigner: DefaultCookieSigner = app.injector.instanceOf[DefaultCookieSigner]

  "show" should {
    "display the page" in new Setup {
      stubAuthorisation()
      stubKeystore(SessionId, testRegId)
      setupFeatures(takeovers = true)
      stubGet(s"/company-registration/corporation-tax-registration/$testRegId/corporation-tax-registration", 200, statusResponseFromCR())
      stubGetTakeoverDetails(testRegId, OK, Some(TakeoverDetails(replacingAnotherBusiness = true)))

      val res: WSResponse = await(buildClient(controllers.takeovers.routes.OtherBusinessNameController.show.url)
        .withHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck")
        .get())

      res.status shouldBe OK
    }

    "display and prepop the page" in new Setup {
      stubAuthorisation()
      stubKeystore(SessionId, testRegId)
      setupFeatures(takeovers = true)
      stubGet(s"/company-registration/corporation-tax-registration/$testRegId/corporation-tax-registration", 200, statusResponseFromCR())
      val testPrepopBusinessName: String = "another test name"
      stubGetTakeoverDetails(testRegId, OK, Some(TakeoverDetails(replacingAnotherBusiness = true, Some(testPrepopBusinessName))))

      val res: WSResponse = await(buildClient(controllers.takeovers.routes.OtherBusinessNameController.show.url)
        .withHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck")
        .get())

      res.status shouldBe OK
      Jsoup.parse(res.body).getElementById("otherBusinessName").attr("value") shouldBe testPrepopBusinessName
    }
  }

  "submit" should {
    "update the takeover data in the backend and redirect to next page" in new Setup {
      stubAuthorisation()
      stubKeystore(SessionId, testRegId)
      setupFeatures(takeovers = true)
      stubGet(s"/company-registration/corporation-tax-registration/$testRegId/corporation-tax-registration", 200, statusResponseFromCR())
      stubGetTakeoverDetails(testRegId, OK, Some(TakeoverDetails(replacingAnotherBusiness = true)))
      stubPutTakeoverDetails(testRegId, OK, TakeoverDetails(replacingAnotherBusiness = true, Some(testBusinessName)))

      val res: WSResponse = await(buildClient(controllers.takeovers.routes.OtherBusinessNameController.submit.url)
        .withHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck")
        .post(Map(otherBusinessNameKey -> Seq(testBusinessName)))
      )

      res.status shouldBe SEE_OTHER
      res.redirectLocation should contain(controllers.takeovers.routes.OtherBusinessAddressController.show.url)
    }
  }
}
