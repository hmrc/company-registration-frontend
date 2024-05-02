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

package test.www.takeovers

import java.util.UUID

import test.fixtures.Fixtures
import test.itutil.servicestubs.TakeoverStub
import test.itutil.{IntegrationSpecBase, LoginStub, RequestsFinder}
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.HeaderNames
import play.api.libs.crypto.DefaultCookieSigner
import play.api.libs.ws.WSResponse
import play.api.test.Helpers._

class TakeoverInformationNeededControllerISpec extends IntegrationSpecBase with LoginStub with MockitoSugar with RequestsFinder with TakeoverStub with Fixtures {

  class Setup {
    val userId: String = "testUserId"
    val testRegId: String = "testRegId"
    lazy val csrfToken: String = UUID.randomUUID().toString

    lazy val sessionCookie: String = getSessionCookie(Map("csrfToken" -> csrfToken), userId)
  }

  lazy val defaultCookieSigner: DefaultCookieSigner = app.injector.instanceOf[DefaultCookieSigner]

  "show" should {
    "retrieve the existing data from the backend and serve the page" in new Setup {
      stubAuthorisation()
      stubKeystore(SessionId, testRegId)
      stubGet(s"/company-registration/corporation-tax-registration/$testRegId/corporation-tax-registration", 200, statusResponseFromCR())

      val res: WSResponse = await(buildClient(controllers.takeovers.routes.TakeoverInformationNeededController.show.url)
        .withHttpHeaders(HeaderNames.COOKIE -> sessionCookie)
        .get())

      res.status mustBe OK
    }
  }
}
