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

import com.github.tomakehurst.wiremock.client.WireMock._
import itutil.{IntegrationSpecBase, LoginStub}
import org.joda.time.LocalDate
import org.jsoup.Jsoup
import play.api.http.HeaderNames
import play.api.libs.crypto.DefaultCookieSigner

class DashboardControllerISpec extends IntegrationSpecBase with LoginStub {

  val regId = "5"
  val localDate = LocalDate.now()
  val userId = "/bar/foo"
  val enrolmentsURI = "/test/enrolments"
  val timestamp = "2017-05-16T16:01:55Z"

  lazy val defaultCookieSigner: DefaultCookieSigner = app.injector.instanceOf[DefaultCookieSigner]

  val jsonOtherRegStatusDraft =
    s"""{
       |   "status": "draft",
       |   "lastUpdate": "$timestamp",
       |   "cancelURL": "testCancelURL/$regId/del"
       |}""".stripMargin

  val emailResult = """{ "address": "a@a.a", "type": "GG", "link-sent": true, "verified": false , "return-link-email-sent" : false}"""

  def statusResponseFromCR(status: String = "draft", rID: String = "5") =
    s"""
       |{
       |    "registrationID" : "$rID",
       |    "status" : "$status",
       |    "accountingDetails" : {
       |        "accountingDateStatus" : "NOT_PLANNING_TO_YET"
       |    },
       |    "accountsPreparation" : {
       |        "businessEndDateChoice" : "HMRC_DEFINED"
       |    },
       |        "verifiedEmail" : {
       |        "address" : "user@test.com",
       |        "type" : "GG",
       |        "link-sent" : true,
       |        "verified" : true,
       |        "return-link-email-sent" : false
       |    }
       |}
     """.stripMargin

  def setupSimpleAuthWithEnrolmentsMocks(enrolments: String = "/test/enrolments") = {
    stubPost("/write/audit", 200, """{"x":2}""")
    stubGet("/auth/authority", 200,
      s"""
         |{
         |"uri":"${userId}",
         |"accounts":{},
         |"levelOfAssurance": "2",
         |"confidenceLevel" : 50,
         |"credentialStrength": "strong",
         |"legacyOid":"1234567890",
         |"enrolments":"$enrolments"
         |}""".stripMargin
    )
  }

  def stubKeystoreDashboard(session: String, regId: String, email: String) = {
    val keystoreUrl = s"/keystore/company-registration-frontend/${session}"

    stubFor(get(urlMatching(keystoreUrl))
      .willReturn(
        aResponse().
          withStatus(200).
          withBody(
            s"""{
               |"id": "${session}",
               |"data": {
               |  "registrationID": "${regId}",
               |  "email" : "${email}"
               |}
               |}""".stripMargin
          )
      )
    )
  }

  def stubVATThresholdAmount(date: LocalDate) = {
    val vatThresholdUrl = s"/vatreg/threshold/${date}"
    stubFor(get(urlMatching(vatThresholdUrl))
      .willReturn(
        aResponse().
          withStatus(200).
          withBody(
            s"""{
               |"taxable-threshold": "85000",
               |"since": "2017-04-01"
               |}""".stripMargin
          )
      )
    )
  }

  def stubIncorrectVATThresholdAmount(date: LocalDate) = {
    val vatThresholdUrl = s"/vatreg/threshold/${date}"
    stubFor(get(urlMatching(vatThresholdUrl))
      .willReturn(
        aResponse().
          withStatus(200).
          withBody(
            s"""{
               |"taxable-threshold": "85000pounds",
               |"since": "2017-04-01"
               |}""".stripMargin
          )
      )
    )
  }


  def stubKeystoreDashboardMismatchedResult(session: String, regId: String, email: String, mismatchResult: Boolean) = {
    val keystoreUrl = s"/keystore/company-registration-frontend/${session}"

    stubFor(get(urlMatching(keystoreUrl))
      .willReturn(
        aResponse().
          withStatus(200).
          withBody(
            s"""{
               |"id": "${session}",
               |"data": {
               |  "registrationID": "${regId}",
               |  "email" : "${email}",
               |  "emailMismatchAudit" : $mismatchResult
               |}
               |}""".stripMargin
          )
      )
    )
  }

  def stubKeystoreCache(sessionId: String, key: String) = {
    stubFor(put(urlMatching(s"/keystore/company-registration-frontend/$sessionId/data/$key"))
      .willReturn(
        aResponse()
          .withStatus(200).
          withBody(
            s"""{
               |"id": "$sessionId",
               |"data": {}
               |}""".stripMargin
          )
      )
    )
  }

  "GET Dashboard" should {

    "display the dashboard with a restart URL for PAYE if it is Rejected status" in {
      val payeRestartURL = "/test/restarturl"
      val payeRejected =
        s"""
           |{
           |   "status": "rejected",
           |   "restartURL": "$payeRestartURL"
           |}
       """.stripMargin

      val csrfToken = UUID.randomUUID().toString

      setupFeatures(vat = false)

      stubSuccessfulLogin(userId = userId)
      setupSimpleAuthWithEnrolmentsMocks(enrolmentsURI)

      stubKeystoreDashboard(SessionId, regId, "|||fake|||email")
      stubKeystoreCache(SessionId, "emailMismatchAudit")
      stubVATThresholdAmount(LocalDate.now())

      stubGet(s"/company-registration/corporation-tax-registration/$regId/corporation-tax-registration", 200, statusResponseFromCR("held", regId))
      stubGet(s"/company-registration/corporation-tax-registration/$regId/fetch-held-time", 200, "1504774767050")
      stubGet(s"/company-registration/corporation-tax-registration/$regId/retrieve-email", 200, emailResult)
      stubGet(s"/paye-registration/$regId/status", 200, payeRejected)
      stubGet(s"$enrolmentsURI", 200, "[]")

      val fResponse = buildClient("/company-registration-overview").
        withHttpHeaders(HeaderNames.COOKIE -> getSessionCookie(Map("csrfToken" -> csrfToken), userId = userId), "Csrf-Token" -> "nocheck").
        get()

      val response = await(fResponse)
      response.status shouldBe 200
      val mdtpCookieData = getCookieData(response.cookie("mdtp").get)
      mdtpCookieData("csrfToken") shouldNot be("")
      mdtpCookieData("sessionId") shouldBe SessionId
      mdtpCookieData("userId") shouldBe userId

      val doc = Jsoup.parse(response.body)
      doc.title should include("Company registration overview")
    }

    "display the dashboard and not send out an audit event if the result of the email mismatch event was already saved" in {
      val payeRestartURL = "/test/restarturl"
      val payeRejected =
        s"""
           |{
           |   "status": "rejected",
           |   "restartURL": "$payeRestartURL"
           |}
       """.stripMargin

      val csrfToken = UUID.randomUUID().toString

      stubSuccessfulLogin(userId = userId)
      setupSimpleAuthWithEnrolmentsMocks(enrolmentsURI)
      stubVATThresholdAmount(LocalDate.now())
      stubKeystoreDashboardMismatchedResult(SessionId, regId, "|||fake|||email", true)
      stubGet(s"/company-registration/corporation-tax-registration/$regId/corporation-tax-registration", 200, statusResponseFromCR("held", regId))
      stubGet(s"/company-registration/corporation-tax-registration/$regId/fetch-held-time", 200, "1504774767050")
      stubGet(s"/company-registration/corporation-tax-registration/$regId/retrieve-email", 200, emailResult)
      stubGet(s"/paye-registration/$regId/status", 200, payeRejected)
      stubGet(s"$enrolmentsURI", 200, "[]")

      val fResponse = buildClient("/company-registration-overview").
        withHttpHeaders(HeaderNames.COOKIE -> getSessionCookie(Map("csrfToken" -> csrfToken), userId = userId), "Csrf-Token" -> "nocheck").
        get()

      val response = await(fResponse)
      response.status shouldBe 200
      val mdtpCookieData = getCookieData(response.cookie("mdtp").get)
      mdtpCookieData("csrfToken") shouldNot be("")
      mdtpCookieData("sessionId") shouldBe SessionId
      mdtpCookieData("userId") shouldBe userId

      val doc = Jsoup.parse(response.body)
      doc.title should include("Company registration overview")
    }

    "not display the VAT block when the vat feature switch is OFF" in {
      stubSuccessfulLogin(userId = userId)
      setupSimpleAuthWithEnrolmentsMocks(enrolmentsURI)
      stubVATThresholdAmount(LocalDate.now())
      stubKeystoreDashboard(SessionId, regId, "|||fake|||email")
      stubKeystoreCache(SessionId, "emailMismatchAudit")

      stubGet(s"/company-registration/corporation-tax-registration/$regId/corporation-tax-registration", 200, statusResponseFromCR("submitted", regId))
      stubGet(s"/company-registration/corporation-tax-registration/$regId/fetch-held-time", 200, "1504774767050")
      stubGet(s"/company-registration/corporation-tax-registration/$regId/retrieve-email", 200, emailResult)
      stubGet(s"/paye-registration/$regId/status", 200, jsonOtherRegStatusDraft)
      stubGet(s"$enrolmentsURI", 200, """[]""")

      val fResponse = buildClient("/company-registration-overview").
        withHttpHeaders(HeaderNames.COOKIE -> getSessionCookie(userId = userId)).
        get()

      val response = await(fResponse)
      response.status shouldBe 200

      val doc = Jsoup.parse(response.body)
      a[NullPointerException] shouldBe thrownBy(doc.getElementById("vatThreshold").text)
    }


    "not display the dashboard if we get a non int value for the threshold" in {
      stubSuccessfulLogin(userId = userId)
      setupSimpleAuthWithEnrolmentsMocks(enrolmentsURI)
      stubIncorrectVATThresholdAmount(LocalDate.now())
      stubKeystoreDashboard(SessionId, regId, "|||fake|||email")
      stubKeystoreCache(SessionId, "emailMismatchAudit")

      stubGet(s"/company-registration/corporation-tax-registration/$regId/corporation-tax-registration", 200, statusResponseFromCR("submitted", regId))
      stubGet(s"/company-registration/corporation-tax-registration/$regId/fetch-held-time", 200, "1504774767050")
      stubGet(s"/company-registration/corporation-tax-registration/$regId/retrieve-email", 200, emailResult)
      stubGet(s"/paye-registration/$regId/status", 200, jsonOtherRegStatusDraft)
      stubGet(s"$enrolmentsURI", 200, """[]""")

      val fResponse = buildClient("/company-registration-overview").
        withHttpHeaders(HeaderNames.COOKIE -> getSessionCookie(userId = userId)).
        get()

      val response = await(fResponse)
      response.status shouldBe 500

    }


    "correctly display the VAT block" when {
      "the vat feature switch is ON and status is SUBMITTED" in {
        setupFeatures(vat = true)

        stubSuccessfulLogin(userId = userId)
        setupSimpleAuthWithEnrolmentsMocks(enrolmentsURI)
        stubVATThresholdAmount(LocalDate.now())
        stubKeystoreDashboard(SessionId, regId, "|||fake|||email")
        stubKeystoreCache(SessionId, "emailMismatchAudit")

        stubGet(s"/company-registration/corporation-tax-registration/$regId/corporation-tax-registration", 200, statusResponseFromCR("submitted", regId))
        stubGet(s"/company-registration/corporation-tax-registration/$regId/fetch-held-time", 200, "1504774767050")
        stubGet(s"/company-registration/corporation-tax-registration/$regId/retrieve-email", 200, emailResult)
        stubGet(s"/paye-registration/$regId/status", 200, jsonOtherRegStatusDraft)
        stubGet(s"/vatreg/$regId/status", 200, jsonOtherRegStatusDraft)
        stubGet(s"$enrolmentsURI", 200, "[]")

        val fResponse = buildClient("/company-registration-overview").
          withHttpHeaders(HeaderNames.COOKIE -> getSessionCookie(userId = userId)).
          get()

        val response = await(fResponse)
        response.status shouldBe 200
      }

      "the vat feature switch is ON and status is HELD" in {
        setupFeatures(vat = true)

        stubSuccessfulLogin(userId = userId)
        setupSimpleAuthWithEnrolmentsMocks(enrolmentsURI)
        stubVATThresholdAmount(LocalDate.now())
        stubKeystoreDashboard(SessionId, regId, "|||fake|||email")
        stubKeystoreCache(SessionId, "emailMismatchAudit")

        stubGet(s"/company-registration/corporation-tax-registration/$regId/corporation-tax-registration", 200, statusResponseFromCR("held", regId))
        stubGet(s"/company-registration/corporation-tax-registration/$regId/fetch-held-time", 200, "1504774767050")
        stubGet(s"/company-registration/corporation-tax-registration/$regId/retrieve-email", 200, emailResult)
        stubGet(s"/paye-registration/$regId/status", 200, jsonOtherRegStatusDraft)
        stubGet(s"/vatreg/$regId/status", 200, jsonOtherRegStatusDraft)
        stubGet(s"$enrolmentsURI", 200, "[]")

        val fResponse = buildClient("/company-registration-overview").
          withHttpHeaders(HeaderNames.COOKIE -> getSessionCookie(userId = userId)).
          get()

        val response = await(fResponse)
        response.status shouldBe 200
      }

      "the vat feature switch is OFF and status is HELD" in {
        setupFeatures(vat = false)

        stubSuccessfulLogin(userId = userId)
        setupSimpleAuthWithEnrolmentsMocks(enrolmentsURI)
        stubVATThresholdAmount(LocalDate.now())
        stubKeystoreDashboard(SessionId, regId, "|||fake|||email")
        stubKeystoreCache(SessionId, "emailMismatchAudit")

        stubGet(s"/company-registration/corporation-tax-registration/$regId/corporation-tax-registration", 200, statusResponseFromCR("held", regId))
        stubGet(s"/company-registration/corporation-tax-registration/$regId/fetch-held-time", 200, "1504774767050")
        stubGet(s"/company-registration/corporation-tax-registration/$regId/retrieve-email", 200, emailResult)
        stubGet(s"/paye-registration/$regId/status", 200, jsonOtherRegStatusDraft)
        stubGet(s"/vatreg/$regId/status", 200, jsonOtherRegStatusDraft)
        stubGet(s"$enrolmentsURI", 200, "[]")

        val fResponse = buildClient("/company-registration-overview").
          withHttpHeaders(HeaderNames.COOKIE -> getSessionCookie(userId = userId)).
          get()

        val response = await(fResponse)
        response.status shouldBe 200

        val doc = Jsoup.parse(response.body)
        a[NullPointerException] shouldBe thrownBy(doc.getElementById("vatThreshold").text)
      }

      "there is no VAT registration" in {
        setupFeatures(vat = true)

        stubSuccessfulLogin(userId = userId)
        setupSimpleAuthWithEnrolmentsMocks(enrolmentsURI)
        stubVATThresholdAmount(LocalDate.now())
        stubKeystoreDashboard(SessionId, regId, "|||fake|||email")
        stubKeystoreCache(SessionId, "emailMismatchAudit")

        stubGet(s"/company-registration/corporation-tax-registration/$regId/corporation-tax-registration", 200, statusResponseFromCR("submitted", regId))
        stubGet(s"/company-registration/corporation-tax-registration/$regId/fetch-held-time", 200, "1504774767050")
        stubGet(s"/company-registration/corporation-tax-registration/$regId/retrieve-email", 200, emailResult)
        stubGet(s"/paye-registration/$regId/status", 200, jsonOtherRegStatusDraft)
        stubGet(s"/vatreg/$regId/status", 404, "")
        stubGet(s"$enrolmentsURI", 200, "[]")

        val fResponse = buildClient("/company-registration-overview").
          withHttpHeaders(HeaderNames.COOKIE -> getSessionCookie(userId = userId)).
          get()

        val response = await(fResponse)
        response.status shouldBe 200
      }

      "there is a VAT registration in draft status" in {
        setupFeatures(vat = true)

        stubSuccessfulLogin(userId = userId)
        setupSimpleAuthWithEnrolmentsMocks(enrolmentsURI)
        stubVATThresholdAmount(LocalDate.now())
        stubKeystoreDashboard(SessionId, regId, "|||fake|||email")
        stubKeystoreCache(SessionId, "emailMismatchAudit")

        stubGet(s"/company-registration/corporation-tax-registration/$regId/corporation-tax-registration", 200, statusResponseFromCR("submitted", regId))
        stubGet(s"/company-registration/corporation-tax-registration/$regId/fetch-held-time", 200, "1504774767050")
        stubGet(s"/company-registration/corporation-tax-registration/$regId/retrieve-email", 200, emailResult)
        stubGet(s"/paye-registration/$regId/status", 200, jsonOtherRegStatusDraft)
        stubGet(s"/vatreg/$regId/status", 200, jsonOtherRegStatusDraft)
        stubGet(s"$enrolmentsURI", 200, "[]")

        val fResponse = buildClient("/company-registration-overview").
          withHttpHeaders(HeaderNames.COOKIE -> getSessionCookie(userId = userId)).
          get()

        val response = await(fResponse)
        response.status shouldBe 200
      }
    }
  }
}
