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

package www

import java.util.UUID

import itutil.{IntegrationSpecBase, LoginStub}
import models.{BusinessRegistration, Links}
import org.jsoup.Jsoup
import play.api.http.HeaderNames
import play.api.libs.crypto.DefaultCookieSigner
import play.api.libs.json.Json

class CompletionCapacityControllerISpec extends IntegrationSpecBase with LoginStub {
  val regId = "5"
  val userId = "/bar/foo"
  val csrfToken = UUID.randomUUID().toString
  lazy val defaultCookieSigner: DefaultCookieSigner = app.injector.instanceOf[DefaultCookieSigner]

  val businessRegResponse = Json.toJson(BusinessRegistration(
    regId,
    "2016-08-03T10:49:11Z",
    "en",
    Some("director"),
    Links(Some("foo bar"))
  )).toString

  def statusResponseFromCR(status: String = "draft", rID: String = "5") =
    s"""
       |{
       |    "registrationID" : "${rID}",
       |    "status" : "${status}",
       |        "verifiedEmail" : {
       |        "address" : "user@test.com",
       |        "type" : "GG",
       |        "link-sent" : true,
       |        "verified" : true,
       |        "return-link-email-sent" : false
       |    }
       |}
     """.stripMargin

  s"${controllers.reg.routes.CompletionCapacityController.show.url}" should {
    "return 200" in {
      stubAuthorisation()
      stubSuccessfulLogin()
      stubKeystore(SessionId, regId)
      stubBusinessRegRetrieveMetaDataNoRegId(200, businessRegResponse)
      stubGet(s"/company-registration/corporation-tax-registration/$regId/corporation-tax-registration", 200, statusResponseFromCR())
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)
      val response = await(buildClient(controllers.reg.routes.CompletionCapacityController.show.url)
        .withHeaders(HeaderNames.COOKIE -> sessionCookie)
        .get())
      response.status shouldBe 200

      val doc = Jsoup.parse(response.body)
      doc.getElementById("completionCapacity-director").attr("checked") shouldBe "checked"
      doc.getElementById("completionCapacityOther").`val` shouldBe ""
    }
  }

  s"${controllers.reg.routes.CompletionCapacityController.submit.url}" should {
    "redirect with a status of 303 with valid data" in {
      stubAuthorisation()
      stubSuccessfulLogin()
      stubKeystore(SessionId, regId)
      stubBusinessRegRetrieveMetaDataWithRegId(regId, 200, businessRegResponse)
      stubUpdateBusinessRegistrationCompletionCapacity(regId, 200, businessRegResponse)
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)

      val response = await(buildClient(controllers.reg.routes.CompletionCapacityController.submit.url).
        withHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck").post(
        Map("csrfToken" -> Seq("xxx-ignored-xxx"), "completionCapacity" -> Seq("director"), "completionCapacityOther" -> Seq("")))
      )

      response.status shouldBe 303
      response.header(HeaderNames.LOCATION) shouldBe Some(controllers.handoff.routes.BasicCompanyDetailsController.basicCompanyDetails.url)
    }
    "return 400 to the user and display the appropriate error messages" in {
      stubAuthorisation()
      stubSuccessfulLogin()
      stubKeystore(SessionId, regId)
      stubBusinessRegRetrieveMetaDataWithRegId(regId, 200, businessRegResponse)
      stubUpdateBusinessRegistrationCompletionCapacity(regId, 200, businessRegResponse)
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)

      val response = await(buildClient(controllers.reg.routes.CompletionCapacityController.submit.url).
        withHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck").post(
        Map("csrfToken" -> Seq("xxx-ignored-xxx"), "completionCapacity" -> Seq(""), "completionCapacityOther" -> Seq("bar")))
      )

      response.status shouldBe 400
      val doc = Jsoup.parse(response.body)
      Option(doc.getElementById("completionCapacity-error-summary")).isDefined shouldBe true
    }
  }
}