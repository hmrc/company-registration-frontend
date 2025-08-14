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

package test.www

import java.util.UUID
import com.github.tomakehurst.wiremock.client.WireMock.{findAll, postRequestedFor, urlMatching}
import config.AppConfig
import test.itutil.{IntegrationSpecBase, LoginStub, RequestsFinder}
import org.jsoup.Jsoup
import play.api.http.HeaderNames
import play.api.i18n.{Lang, MessagesApi}
import play.api.libs.crypto.DefaultCookieSigner
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeRequest

class CompanyContactDetailsControllerISpec extends IntegrationSpecBase with LoginStub with RequestsFinder {



  val userId = "/bar/foo"
  val csrfToken = UUID.randomUUID().toString
  val regId = "5"
  val sessionCookie = () => getSessionCookie(Map("csrfToken" -> csrfToken), userId)

  lazy val defaultCookieSigner: DefaultCookieSigner = app.injector.instanceOf[DefaultCookieSigner]

  val errorTemplate = app.injector.instanceOf[views.html.error_template]
  val messages = app.injector.instanceOf[MessagesApi].preferred(Seq(Lang("en")))
  val appConfig = app.injector.instanceOf[AppConfig]

  def statusResponseFromCR(status: String = "draft", rID: String = "5") =
    s"""
       |{
       |    "registrationID" : "${rID}",
       |    "status" : "${status}",
       |    "companyDetails": { "companyName": "fooBAR"},
       |    "verifiedEmail" : {
       |        "address" : "user@test.com",
       |        "type" : "GG",
       |        "link-sent" : true,
       |        "verified" : true,
       |        "return-link-email-sent" : false
       |    }
       |}
     """.stripMargin

  val emailResponseFromCRLowLevel =
    s"""
       |    {
       |        "address" : "user@test.com",
       |        "type" : "GG",
       |        "link-sent" : true,
       |        "verified" : true,
       |        "return-link-email-sent" : false
       |    }

     """.stripMargin

  val nameJson = Json.parse(
    """{ "name": {"name": "foo", "lastName": "bar"}}""".stripMargin).as[JsObject]
  val nameAndCredId = Json.obj("externalId" -> "fooBarWizz1") ++ nameJson

  "show" should {

    "return 200 when no data is returned from backend" in {
      stubSuccessfulLogin(userId = userId)
      stubKeystore(SessionId, regId)
      stubGet(s"/company-registration/corporation-tax-registration/$regId/retrieve-email", 200, emailResponseFromCRLowLevel)
      stubGet(s"/company-registration/corporation-tax-registration/$regId/corporation-tax-registration", 200, statusResponseFromCR())
      stubGet(s"/company-registration/corporation-tax-registration/$regId/contact-details", 404, "")
      stubContactDetails(regId, 404)

      val fResponse = await(buildClient(controllers.reg.routes.CompanyContactDetailsController.show.url)
        .withHttpHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck")
        .get())
      val doc = Jsoup.parse(fResponse.body)
      fResponse.status mustBe 200
    }
    "return 200 with data from backend " in {
      val contactDetailsResp = Json.parse(
        """
          |{
          | "contactDaytimeTelephoneNumber": "12345678",
          | "contactMobileNumber": "45678",
          | "contactEmail": "foo@foo.com",
          | "links": {
          | }
          |}
        """.stripMargin)

      stubAuthorisation()
      stubSuccessfulLogin(userId = userId)
      stubKeystore(SessionId, regId)
      stubGet(s"/company-registration/corporation-tax-registration/$regId/retrieve-email", 200, emailResponseFromCRLowLevel)
      stubGet(s"/company-registration/corporation-tax-registration/$regId/corporation-tax-registration", 200, statusResponseFromCR())
      stubGet(s"/company-registration/corporation-tax-registration/$regId/contact-details", 200, contactDetailsResp.toString())

      val fResponse = await(buildClient(controllers.reg.routes.CompanyContactDetailsController.show.url)
        .withHttpHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck")
        .get())
      val doc = Jsoup.parse(fResponse.body)
      fResponse.status mustBe 200
      doc.getElementById("contactDaytimeTelephoneNumber").`val` mustBe "12345678"
      doc.getElementById("contactMobileNumber").`val` mustBe "45678"
      doc.getElementById("contactEmail").`val` mustBe "foo@foo.com"
    }

    "render the ISE error template when an unexpected error occurs" in {
      stubSuccessfulLogin(userId = userId)
      stubKeystore(SessionId, regId)
      stubGet(s"/company-registration/corporation-tax-registration/$regId/corporation-tax-registration", 404, "")

      val fResponse = await(buildClient(controllers.reg.routes.CompanyContactDetailsController.show.url)
        .withHttpHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck")
        .get())

      fResponse.status mustBe 500
      fResponse.body mustBe errorTemplate(
        "Sorry, we are experiencing technical difficulties - 500",
        "Sorry, we’re experiencing technical difficulties",
        "Please try again in a few minutes."
      )(FakeRequest(), messages, appConfig).toString
    }
  }
  "submit" should {
    "return 303 when all fields are populated" in {
      val contactDetailsResp = Json.parse(
        """
          |{
          | "contactDaytimeTelephoneNumber": "12345678910",
          | "contactMobileNumber": "1234567891011",
          | "contactEmail": "foo@foo.com",
          | "links": {
          | }
          |}
        """.stripMargin)

      stubSuccessfulLogin(userId = userId, otherParamsForAuth = Some(nameAndCredId))
      stubKeystore(SessionId, regId)
      stubGet(s"/company-registration/corporation-tax-registration/$regId/corporation-tax-registration", 200, statusResponseFromCR())
      stubPut(s"/company-registration/corporation-tax-registration/$regId/contact-details", 200, contactDetailsResp.toString())
      stubContactDetails(regId, 200)

      val response = await(buildClient(controllers.reg.routes.CompanyContactDetailsController.submit.url)
        .withHttpHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck").post(
        Map(
          "csrfToken" -> Seq("xxx-ignored-xxx"),
          "contactDaytimeTelephoneNumber" -> Seq("12345678910"),
          "contactMobileNumber" -> Seq("1234567891011"),
          "contactEmail" -> Seq("foo@foo.com")))
      )
      response.status mustBe 303
      response.header(HeaderNames.LOCATION).get mustBe controllers.takeovers.routes.ReplacingAnotherBusinessController.show.url
      val audit = Json.parse(getRequestBody("post", "/write/audit")).as[JsObject] \ "detail" \ "businessContactDetails"
      audit.get mustBe Json.obj("originalEmail" -> "test@test.com", "submittedEmail" -> "foo@foo.com")
      val prePop = Json.parse(getRequestBody("post", s"/business-registration/$regId/contact-details")).as[JsObject]
      prePop mustBe Json.obj("telephoneNumber" -> "12345678910", "mobileNumber" -> "1234567891011", "email" -> "foo@foo.com")
    }
    "return 400 data is invalid" in {
      stubAuthorisation()
      stubSuccessfulLogin(userId = userId, otherParamsForAuth = Some(nameAndCredId))
      stubKeystore(SessionId, regId)
      stubGet(s"/company-registration/corporation-tax-registration/$regId/corporation-tax-registration", 200, statusResponseFromCR())
      val response = await(buildClient(controllers.reg.routes.CompanyContactDetailsController.submit.url).
        withHttpHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck").post(
        Map(
          "csrfToken" -> Seq("xxx-ignored-xxx"),
          "contactDaytimeTelephoneNumber" -> Seq("1"),
          "contactMobileNumber" -> Seq("12"),
          "contactEmail" -> Seq("foo@foo.com")))
      )

      response.status mustBe 400
    }
    "return 303 when minimum amount of fields are populated" in {
      val contactDetailsResp = Json.parse(
        """
          |{ "contactDaytimeTelephoneNumber": "12345678910",
          | "links": {
          | }
          |}
        """.stripMargin)

      stubAuthorisation()
      stubSuccessfulLogin(userId = userId, otherParamsForAuth = Some(nameAndCredId))
      stubKeystore(SessionId, regId)
      stubGet(s"/company-registration/corporation-tax-registration/$regId/corporation-tax-registration", 200, statusResponseFromCR())
      stubPut(s"/company-registration/corporation-tax-registration/$regId/contact-details", 200, contactDetailsResp.toString())
      stubContactDetails(regId, 200)
      val response = await(buildClient(controllers.reg.routes.CompanyContactDetailsController.submit.url).
        withHttpHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck").post(
        Map(
          "csrfToken" -> Seq("xxx-ignored-xxx"),
          "contactDaytimeTelephoneNumber" -> Seq("12345678910"),
          "contactMobileNumber" -> Seq(""),
          "contactEmail" -> Seq("")))
      )

      response.status mustBe 303
      response.header(HeaderNames.LOCATION).get mustBe controllers.takeovers.routes.ReplacingAnotherBusinessController.show.url
      val audit = Json.parse(getRequestBody("post", "/write/audit")).as[JsObject] \ "detail" \ "businessContactDetails"
      audit.get mustBe Json.obj("originalEmail" -> "test@test.com")
    }
    "return 303, when name is submitted and email is the same as auth email, no audit event should be sent" in {
      val contactDetailsResp = Json.parse(
        """
          |{
          | "contactDaytimeTelephoneNumber": "12345678910",
          | "contactEmail":"test@test.com",
          | "links": {
          | }
          |}
        """.stripMargin)

      stubAuthorisation()
      stubSuccessfulLogin(userId = userId, otherParamsForAuth = Some(nameAndCredId))
      stubKeystore(SessionId, regId)
      stubGet(s"/company-registration/corporation-tax-registration/$regId/corporation-tax-registration", 200, statusResponseFromCR())
      stubPut(s"/company-registration/corporation-tax-registration/$regId/contact-details", 200, contactDetailsResp.toString())
      stubContactDetails(regId, 200)
      val response = await(buildClient(controllers.reg.routes.CompanyContactDetailsController.submit.url).
        withHttpHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck").post(
        Map(
          "csrfToken" -> Seq("xxx-ignored-xxx"),
          "contactName" -> Seq("foo bar"),
          "contactDaytimeTelephoneNumber" -> Seq("12345678910"),
          "contactMobileNumber" -> Seq(""),
          "contactEmail" -> Seq("test@test.com")))
      )

      response.status mustBe 303
      response.header(HeaderNames.LOCATION).get mustBe controllers.takeovers.routes.ReplacingAnotherBusinessController.show.url
      intercept[Exception]((Json.parse(getRequestBody("post", "/write/audit")).as[JsObject] \ "detail" \ "businessContactDetails").get)

      findAll(postRequestedFor(urlMatching("/write/audit"))).size() mustBe 1
    }
  }
}