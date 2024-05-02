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
import forms.takeovers.WhoAgreedTakeoverForm._
import test.itutil.servicestubs.TakeoverStub
import test.itutil.{IntegrationSpecBase, LoginStub, RequestsFinder}
import models.{NewAddress, TakeoverDetails}
import org.jsoup.Jsoup
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.HeaderNames
import play.api.libs.crypto.DefaultCookieSigner
import play.api.libs.ws.WSResponse
import play.api.test.Helpers._

class WhoAgreedTakeoverControllerISpec extends IntegrationSpecBase with LoginStub with MockitoSugar with RequestsFinder with TakeoverStub with Fixtures {

  val userId: String = "testUserId"
  val testRegId: String = "testRegId"
  val testBusinessName: String = "test name"
  val testBusinessAddress: NewAddress = NewAddress("testLine1", "testLine2", None, None, Some("Z11 11Z"), Some("testCountry"))
  val testPreviousOwnersName: String = "test owners name"
  val testTakeoverDetails: TakeoverDetails = TakeoverDetails(
    replacingAnotherBusiness = true,
    businessName = Some(testBusinessName),
    businessTakeoverAddress = Some(testBusinessAddress)
  )
  lazy val csrfToken: String = UUID.randomUUID().toString
  lazy val sessionCookie: String = getSessionCookie(Map("csrfToken" -> csrfToken), userId)
  lazy val defaultCookieSigner: DefaultCookieSigner = app.injector.instanceOf[DefaultCookieSigner]

  "show" should {
    "display the page" in {
      stubAuthorisation()
      stubKeystore(SessionId, testRegId)
      stubGet(s"/company-registration/corporation-tax-registration/$testRegId/corporation-tax-registration", 200, statusResponseFromCR())
      stubGetTakeoverDetails(testRegId, OK, Some(testTakeoverDetails))

      val res: WSResponse = await(buildClient(controllers.takeovers.routes.WhoAgreedTakeoverController.show.url)
        .withHttpHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck")
        .get())

      res.status mustBe OK
    }
    "display and prepop the page" in {
      stubAuthorisation()
      stubKeystore(SessionId, testRegId)
      stubGet(s"/company-registration/corporation-tax-registration/$testRegId/corporation-tax-registration", 200, statusResponseFromCR())
      stubGetTakeoverDetails(testRegId, OK, Some(testTakeoverDetails.copy(previousOwnersName = Some(testPreviousOwnersName))))

      val res: WSResponse = await(buildClient(controllers.takeovers.routes.WhoAgreedTakeoverController.show.url)
        .withHttpHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck")
        .get())

      res.status mustBe OK
      Jsoup.parse(res.body).getElementById("whoAgreedTakeover").attr("value") mustBe testPreviousOwnersName
    }
  }

  "submit" should {
    "update the takeover block and redirect to previous owners address page" in {
      stubAuthorisation()
      stubKeystore(SessionId, testRegId)
      stubGet(s"/company-registration/corporation-tax-registration/$testRegId/corporation-tax-registration", 200, statusResponseFromCR())
      stubGetTakeoverDetails(testRegId, OK, Some(testTakeoverDetails))
      stubPutTakeoverDetails(testRegId, OK, testTakeoverDetails.copy(previousOwnersName = Some(testPreviousOwnersName)))

      val res: WSResponse = await(buildClient(controllers.takeovers.routes.WhoAgreedTakeoverController.submit.url)
        .withHttpHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck")
        .post(Map(whoAgreedTakeoverKey -> Seq(testPreviousOwnersName)))
      )

      res.status mustBe SEE_OTHER
      res.redirectLocation must contain(controllers.takeovers.routes.PreviousOwnersAddressController.show.url)
    }
  }
}
