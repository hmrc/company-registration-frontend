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

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, stubFor, urlMatching}
import itutil.{FakeAppConfig, IntegrationSpecBase, LoginStub, WiremockHelper}
import org.jsoup.Jsoup
import play.api.http.HeaderNames
import play.api.test.FakeApplication

class DashboardControllerISpec extends IntegrationSpecBase with LoginStub with FakeAppConfig {
  val mockHost = WiremockHelper.wiremockHost
  val mockPort = WiremockHelper.wiremockPort

  override implicit lazy val app = FakeApplication(additionalConfiguration = fakeConfig(
    "microservice.services.paye-registration.host" -> s"$mockHost",
    "microservice.services.paye-registration.port" -> s"$mockPort",
    "auditing.consumer.baseUri.host" -> s"$mockHost",
    "auditing.consumer.baseUri.port" -> s"$mockPort",

    "auditing.enabled" -> s"true",
    "auditing.traceRequests" -> s"true"
  ))

  val userId = "/bar/foo"
  val enrolmentsURI = "/test/enrolments"

  def statusResponseFromCR(status:String = "draft", rID:String = "5") =
    s"""
       |{
       |    "registrationID" : "$rID",
       |    "status" : "$status",
       |    "accountingDetails" : {
       |        "accountingDateStatus" : "NOT_PLANNING_TO_YET"
       |    },
       |    "accountsPreparation" : {
       |        "businessEndDateChoice" : "HMRC_DEFINED"
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

  "GET Dashboard" should {
    val regId = "5"

    "display the dashboard with a restart URL for PAYE if it is Rejected status" in {
      val payeRestartURL = "/test/restarturl"
      val payeRejected =
        s"""
           |{
           |   "status": "rejected",
           |   "restartURL": "$payeRestartURL"
           |}
       """.stripMargin

      setupFeatures(paye = true)

      stubSuccessfulLogin(userId=userId)
      setupSimpleAuthWithEnrolmentsMocks(enrolmentsURI)

      stubKeystore(SessionId, regId)
      stubGet(s"/company-registration/corporation-tax-registration/$regId/corporation-tax-registration", 200, statusResponseFromCR("held", regId))
      stubGet(s"/company-registration/corporation-tax-registration/$regId/fetch-held-time", 200, "1504774767050")
      stubGet(s"/paye-registration/$regId/status", 200, payeRejected)
      stubGet(s"$enrolmentsURI", 200, "[]")

      val fResponse = buildClient("/dashboard").
        withHeaders(HeaderNames.COOKIE -> getSessionCookie(userId=userId)).
        get()

      val response = await(fResponse)
      response.status shouldBe 200
      val mdtpCookieData = getCookieData(response.cookie("mdtp").get)
      mdtpCookieData("csrfToken") shouldNot be("")
      mdtpCookieData("sessionId") shouldBe SessionId
      mdtpCookieData("userId") shouldBe userId

      val doc = Jsoup.parse(response.body)
      doc.title shouldBe "Your business registration overview"
      doc.getElementById("PAYERej").attr("href") shouldBe payeRestartURL
      doc.getElementById("PAYERej").text shouldBe "Register again"
    }
  }
}
