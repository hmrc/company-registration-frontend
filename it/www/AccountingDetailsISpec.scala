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
package www

import java.util.UUID

import com.github.tomakehurst.wiremock.client.WireMock._
import itutil.{FakeAppConfig, IntegrationSpecBase, LoginStub, WiremockHelper}
import org.jsoup.Jsoup
import play.api.libs.json.Json
import play.api.libs.ws.WS
import play.api.test.FakeApplication
import play.api.http.HeaderNames


class AccountingDetailsISpec extends IntegrationSpecBase with LoginStub with FakeAppConfig {

  val mockHost = WiremockHelper.wiremockHost
  val mockPort = WiremockHelper.wiremockPort

  override implicit lazy val app = FakeApplication(additionalConfiguration = fakeConfig())

  private def client(path: String) = ws.url(s"http://localhost:$port/register-your-company$path").withFollowRedirects(false)

  val userId = "/bar/foo"

  def statusResponseFromCR(status:String = "draft", rID:String = "5") =
    s"""
       |{
       |    "registrationID" : "${rID}",
       |    "status" : "${status}",
       |    "accountingDetails" : {
       |        "accountingDateStatus" : "NOT_PLANNING_TO_YET"
       |    },
       |    "accountsPreparation" : {
       |        "businessEndDateChoice" : "HMRC_DEFINED"
       |    }
       |}
     """.stripMargin

  def stubKeystore(session: String, regId: String) = {
    val keystoreUrl = s"/keystore/company-registration-frontend/${session}"
    stubFor(get(urlMatching(keystoreUrl))
      .willReturn(
        aResponse().
          withStatus(200).
          withBody(
            s"""{
                |"id": "${session}",
                |"data": { "registrationID": "${regId}" }
                |}""".stripMargin
          )
        )
      )
  }



  "GET Accounting Details" should {

    "Return an unpopulated page if CR returns a NotFound response" in {
      setupSimpleAuthMocks(userId)

      stubSuccessfulLogin(userId=userId)

      stubKeystore(SessionId, "5")

      stubGet("/company-registration/corporation-tax-registration/5/accounting-details", 404, "")
      val fResponse = client("/accounting-dates").
        withHeaders(HeaderNames.COOKIE -> getSessionCookie(userId=userId)).
        get()

      stubGet("/company-registration/corporation-tax-registration/5/corporation-tax-registration", 200, statusResponseFromCR())

      val response = await(fResponse)

      response.status shouldBe 200
      val mdtpCookieData = getCookieData(response.cookie("mdtp").get)
      mdtpCookieData("csrfToken") shouldNot be("")
      mdtpCookieData("sessionId") shouldBe SessionId
      mdtpCookieData("userId") shouldBe userId

      val document = Jsoup.parse(response.body)
      document.title() shouldBe "When will the company start doing business?"
      document.getElementById("businessStartDate-whenregistered").attr("checked") shouldBe ""
      document.getElementById("businessStartDate-futuredate").attr("checked") shouldBe ""
      document.getElementById("businessStartDate-notplanningtoyet").attr("checked") shouldBe ""
    }

    "Return an populated page if CR returns a response" in {
      setupSimpleAuthMocks(userId)

      stubSuccessfulLogin(userId=userId)

      stubKeystore(SessionId, "5")
      stubGet("/company-registration/corporation-tax-registration/5/accounting-details", 404, "")
      stubGet("/company-registration/corporation-tax-registration/5/corporation-tax-registration", 200, statusResponseFromCR())
      val crResponse = """{"accountingDateStatus":"FUTURE_DATE", "startDateOfBusiness":"2018-01-02", "links": []}"""

      stubGet("/company-registration/corporation-tax-registration/5/accounting-details", 200, crResponse)

      val fResponse = client("/accounting-dates").
        withHeaders(HeaderNames.COOKIE -> getSessionCookie(userId=userId)).
        get()


      val response = await(fResponse)

      response.status shouldBe 200

      val document = Jsoup.parse(response.body)
      document.title() shouldBe "When will the company start doing business?"
      document.getElementById("businessStartDate-whenregistered").attr("checked") shouldBe ""
      document.getElementById("businessStartDate-futuredate").attr("checked") shouldBe "checked"
      document.getElementById("businessStartDate-notplanningtoyet").attr("checked") shouldBe ""
      document.getElementById("businessStartDate-futureDate.day").`val` shouldBe "02"
      document.getElementById("businessStartDate-futureDate.month").`val` shouldBe "01"
      document.getElementById("businessStartDate-futureDate.year").`val` shouldBe "2018"
    }

    "Redirect to dashboard if status is NOT draft" in {
      setupSimpleAuthMocks(userId)

      stubSuccessfulLogin(userId=userId)

      stubKeystore(SessionId, "5")
      stubGet("/company-registration/corporation-tax-registration/5/accounting-details", 404, "")
      stubGet("/company-registration/corporation-tax-registration/5/corporation-tax-registration", 200, statusResponseFromCR(status = "NOTDRAFT"))
      val crResponse = """{"accountingDateStatus":"FUTURE_DATE", "startDateOfBusiness":"2018-01-02", "links": []}"""

      stubGet("/company-registration/corporation-tax-registration/5/accounting-details", 200, crResponse)

      val fResponse = client("/accounting-dates").
        withHeaders(HeaderNames.COOKIE -> getSessionCookie(userId=userId)).
        get()


      val response = await(fResponse)

      response.status shouldBe 303
      response.allHeaders("Location") shouldBe Seq("/register-your-company/dashboard")
    }

  }

  "POST Accounting Details" should {
    "Accept information and send to CR" in {
      setupSimpleAuthMocks(userId)

      val csrfToken = UUID.randomUUID().toString

      stubKeystore(SessionId, "5")

      val crResponse = """{"accountingDateStatus":"WHEN_REGISTERED", "links": []}"""
      stubPut("/company-registration/corporation-tax-registration/5/accounting-details", 200, crResponse)

      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)

      val fResponse = client("/accounting-dates").
        withHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck").
        post(Map(
          "csrfToken"->Seq("xxx-ignored-xxx"),
          "businessStartDate"->Seq("futureDate"),
          "businessStartDate-futureDate.year"->Seq("2018"),
          "businessStartDate-futureDate.month"->Seq("1"),
          "businessStartDate-futureDate.day"->Seq("2")
        ))

      val response = await(fResponse)

      response.status shouldBe 303
      response.header(HeaderNames.LOCATION) shouldBe Some("/register-your-company/trading-details")

      val crPuts = findAll(putRequestedFor(urlMatching("/company-registration/corporation-tax-registration/5/accounting-details")))
      val captor = crPuts.get(0)
      val json = Json.parse(captor.getBodyAsString)
      (json \ "accountingDateStatus").as[String] shouldBe "FUTURE_DATE"
      (json \ "startDateOfBusiness").as[String] shouldBe "2018-01-02"
    }
  }

}