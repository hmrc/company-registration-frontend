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

import java.time.{LocalDate, LocalDateTime}
import java.util.UUID

import com.github.tomakehurst.wiremock.client.WireMock._
import fixtures.Fixtures
import itutil.{IntegrationSpecBase, LoginStub}
import org.jsoup.Jsoup
import play.api.http.HeaderNames
import play.api.libs.crypto.DefaultCookieSigner
import play.api.libs.json.Json


class AccountingDetailsISpec extends IntegrationSpecBase with LoginStub with Fixtures {
  val userId = "/bar/foo"
  lazy val defaultCookieSigner: DefaultCookieSigner = app.injector.instanceOf[DefaultCookieSigner]

  "GET Accounting Details" should {

    "Return an unpopulated page if CR returns a NotFound response" in {

      stubAuthorisation()
      stubSuccessfulLogin(userId = userId)

      stubKeystore(SessionId, "5")

      stubGet("/company-registration/corporation-tax-registration/5/accounting-details", 404, "")
      val fResponse = buildClient(controllers.reg.routes.AccountingDatesController.show.url).
        withHttpHeaders(HeaderNames.COOKIE -> getSessionCookie(userId = userId)).
        get()

      stubGet("/company-registration/corporation-tax-registration/5/corporation-tax-registration", 200, statusResponseFromCR())

      val response = await(fResponse)

      response.status shouldBe 200
      val mdtpCookieData = getCookieData(response.cookie("mdtp").get)
      mdtpCookieData("csrfToken") shouldNot be("")
      mdtpCookieData("sessionId") shouldBe SessionId
      mdtpCookieData("userId") shouldBe userId

      val document = Jsoup.parse(response.body)
      document.title should include("When will the company start trading?")
      document.getElementById("businessStartDate-whenregistered").attr("checked") shouldBe ""
      document.getElementById("businessStartDate-futuredate").attr("checked") shouldBe ""
      document.getElementById("businessStartDate-notplanningtoyet").attr("checked") shouldBe ""
    }

    "Return an populated page if CR returns a response" in {

      stubAuthorisation()
      stubSuccessfulLogin(userId = userId)

      stubKeystore(SessionId, "5")
      stubGet("/company-registration/corporation-tax-registration/5/accounting-details", 404, "")
      stubGet("/company-registration/corporation-tax-registration/5/corporation-tax-registration", 200, statusResponseFromCR())
      val crResponse = """{"accountingDateStatus":"FUTURE_DATE", "startDateOfBusiness":"2018-01-02", "links": {}}"""

      stubGet("/company-registration/corporation-tax-registration/5/accounting-details", 200, crResponse)

      val fResponse = buildClient(controllers.reg.routes.AccountingDatesController.show.url).
        withHttpHeaders(HeaderNames.COOKIE -> getSessionCookie(userId = userId)).
        get()

      val response = await(fResponse)

      response.status shouldBe 200

      val document = Jsoup.parse(response.body)
      document.title should include("When will the company start trading?")
      document.getElementById("businessStartDate-whenregistered").attr("checked") shouldBe ""
      document.getElementById("businessStartDate-futuredate").attr("checked") shouldBe "checked"
      document.getElementById("businessStartDate-notplanningtoyet").attr("checked") shouldBe ""
      document.getElementById("businessStartDate-futureDate.day").`val` shouldBe "02"
      document.getElementById("businessStartDate-futureDate.month").`val` shouldBe "01"
      document.getElementById("businessStartDate-futureDate.year").`val` shouldBe "2018"
    }

    "Redirect to dashboard if status is NOT draft" in {

      stubAuthorisation()
      stubSuccessfulLogin(userId = userId)

      stubKeystore(SessionId, "5")
      stubGet("/company-registration/corporation-tax-registration/5/accounting-details", 404, "")
      stubGet("/company-registration/corporation-tax-registration/5/corporation-tax-registration", 200, statusResponseFromCR(status = "NOTDRAFT"))
      val crResponse = """{"accountingDateStatus":"FUTURE_DATE", "startDateOfBusiness":"2019-01-02", "links": {}}"""

      stubGet("/company-registration/corporation-tax-registration/5/accounting-details", 200, crResponse)

      val fResponse = buildClient(controllers.reg.routes.AccountingDatesController.show.url).
        withHttpHeaders(HeaderNames.COOKIE -> getSessionCookie(userId = userId)).
        get()


      val response = await(fResponse)

      response.status shouldBe 303
      response.allHeaders("Location") shouldBe Seq("/register-your-company/company-registration-overview")
    }
  }

  "POST Accounting Details" should {
    "Accept information and send to CR" in {

      stubAuthorisation()
      val csrfToken = UUID.randomUUID().toString
      val testYear = LocalDate.now.getYear + 1

      stubKeystore(SessionId, "5")

      val crResponse = """{"accountingDateStatus":"WHEN_REGISTERED", "links": {}}"""
      stubPut("/company-registration/corporation-tax-registration/5/accounting-details", 200, crResponse)

      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)

      val fResponse = buildClient(controllers.reg.routes.AccountingDatesController.submit.url).
        withHttpHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck").
        post(Map(
          "csrfToken" -> Seq("xxx-ignored-xxx"),
          "businessStartDate" -> Seq("futureDate"),
          "businessStartDate-futureDate.year" -> Seq(testYear.toString),
          "businessStartDate-futureDate.month" -> Seq("01"),
          "businessStartDate-futureDate.day" -> Seq("02")
        ))

      val response = await(fResponse)

      response.status shouldBe 303
      response.header(HeaderNames.LOCATION) shouldBe Some("/register-your-company/loan-payments-dividends")

      val crPuts = findAll(putRequestedFor(urlMatching("/company-registration/corporation-tax-registration/5/accounting-details")))
      val captor = crPuts.get(0)
      val json = Json.parse(captor.getBodyAsString)
      (json \ "accountingDateStatus").as[String] shouldBe "FUTURE_DATE"
      (json \ "startDateOfBusiness").as[String] shouldBe s"$testYear-01-02T00:00"
    }
    "return a 400 showing error messages to the user" in {
      stubAuthorisation()
      stubKeystore(SessionId, "5")
      val csrfToken = UUID.randomUUID().toString
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)

      val response = await(buildClient(controllers.reg.routes.AccountingDatesController.submit.url).
        withHttpHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck").
        post(Map(
          "csrfToken" -> Seq("xxx-ignored-xxx"),
          "businessStartDate" -> Seq("futureDate"),
          "businessStartDate-futureDate.year" -> Seq("foo bar"),
          "businessStartDate-futureDate.month" -> Seq("wizz bang"),
          "businessStartDate-futureDate.day" -> Seq("buzz fuzzle")
        )))

      response.status shouldBe 400
      val doc = Jsoup.parse(response.body)
      Option(doc.getElementById("invalidDay-error-summary")).isDefined shouldBe true
      Option(doc.getElementById("invalidMonth-error-summary")).isDefined shouldBe true
      Option(doc.getElementById("invalidYear-error-summary")).isDefined shouldBe true
    }
  }
}