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
package itutil

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.Json
import play.api.libs.ws.WSClient

object WiremockHelper {
  val wiremockPort = 11111
  val wiremockHost = "localhost"
  val url = s"http://$wiremockHost:$wiremockPort"
}

trait WiremockHelper {
  self: GuiceOneServerPerSuite =>

  import WiremockHelper._

  lazy val ws: WSClient = app.injector.instanceOf(classOf[WSClient])

  lazy val wmConfig: WireMockConfiguration = wireMockConfig().port(wiremockPort)
  lazy val wireMockServer = new WireMockServer(wmConfig)

  def startWiremock() = {
    wireMockServer.start()
    WireMock.configureFor(wiremockHost, wiremockPort)
  }

  def stopWiremock() = wireMockServer.stop()

  def resetWiremock() = WireMock.reset()

  def buildClient(path: String) = ws.url(s"http://localhost:$port/register-your-company${path.replace("""/register-your-company""", "")}").withFollowRedirects(false)

  def listAllStubs = listAllStubMappings

  def stubGet(url: String, status: Integer, body: String) =
    stubFor(get(urlMatching(url))
      .willReturn(
        aResponse().
          withStatus(status).
          withBody(body)
      )
    )

  def stubPost(url: String, status: Integer, responseBody: String, responseHeader: (String, String) = ("", "")) =
    stubFor(post(urlMatching(url))
      .willReturn(
        aResponse().
          withStatus(status).
          withBody(responseBody)
          withHeader(responseHeader._1, responseHeader._2)
      )
    )

  def stubPostWithData(url: String, postData: String)(status: Int, responseBody: String): StubMapping =
    stubFor(post(urlMatching(url))
      .withRequestBody(equalTo(postData))
      .willReturn(aResponse()
        .withStatus(status)
        .withBody(responseBody)
      )
    )

  def stubPut(url: String, status: Integer, responseBody: String) =
    stubFor(put(urlMatching(url))
      .willReturn(
        aResponse().
          withStatus(status).
          withBody(responseBody)
      )
    )

  def stubPut(url: String, requestBody: String)(status: Integer, responseBody: String) =
    stubFor(put(urlMatching(url))
      .withRequestBody(equalTo(requestBody))
      .willReturn(
        aResponse().
          withStatus(status).
          withBody(responseBody)
      )
    )

  def stubBusinessRegRetrieveMetaDataNoRegId(responseStatus: Int, expectedBody: String) = {
    stubGet(url = "/business-registration/business-tax-registration", status = responseStatus, body = expectedBody)
  }

  def stubBusinessRegRetrieveMetaDataWithRegId(regID: String, responseStatus: Int, expectedBody: String) = {
    stubGet(url = s"/business-registration/business-tax-registration/$regID", status = responseStatus, body = expectedBody)
  }

  def stubUpdateBusinessRegistrationCompletionCapacity(regID: String, responseStatus: Int, body: String) = {
    stubPost(s"/business-registration/business-tax-registration/update/$regID", responseStatus, body)
  }

  def stubRetrieveCRCompanyDetails(regID: String, responseStatus: Int, body: String = "{}") = {
    stubGet(url = s"/company-registration/corporation-tax-registration/$regID/company-details", responseStatus, body)
  }

  def stubUpdateCRCompanyDetails(regID: String, responseStatus: Int, body: String = "{}") = {
    stubPut(url = s"/company-registration/corporation-tax-registration/$regID/company-details", responseStatus, body)
  }

  def stubHandOffReference(regId: String, responseStatus: Int, transactionId: String = "testTxId"): StubMapping = {
    stubPut(url = s"/company-registration/corporation-tax-registration/$regId/handOff2Reference-ackRef-save", responseStatus, Json.obj("transaction_id" -> transactionId).toString)
  }

  def stubContactDetails(regId: String, responseStatus: Int, body: String = "{}"): StubMapping = {
    stubPost(url = s"/business-registration/$regId/contact-details", responseStatus, body)
  }

  def stubCorporationTaxRegistration(regId: String): StubMapping = {
    stubGet(s"/company-registration/corporation-tax-registration/$regId/corporation-tax-registration", 200,
      s"""{
         |    "registrationID" : "${regId}",
         |    "status" : "draft",
         |        "verifiedEmail" : {
         |        "address" : "user@test.com",
         |        "type" : "GG",
         |        "link-sent" : true,
         |        "verified" : true,
         |        "return-link-email-sent" : false
         |    }
         |}""".stripMargin)
  }

  def stubAudit: StubMapping = {
    stubPost("/write/audit", 200, "{}")
  }


  def stubKeystore(session: String, regId: String, status: Int = 200) = {
    val keystoreUrl = s"/keystore/company-registration-frontend/$session"
    stubFor(get(urlMatching(keystoreUrl))
      .willReturn(aResponse().
        withStatus(status).
        withBody(
          s"""{
             |"id": "$session",
             |"data": { "registrationID": "$regId" }
             |}""".stripMargin
        )
      )
    )
  }

  def stubKeystoreGetWithJson(session: String, regId: String, status: Int = 200, data: String) = {
    val keystoreUrl = s"/keystore/company-registration-frontend/$session"
    stubFor(get(urlMatching(keystoreUrl))
      .willReturn(aResponse().
        withStatus(status).
        withBody(
          s"""{
             |"id": "$session",
             |"data": ${data}
             |}""".stripMargin
        )
      )
    )
  }

  def stubKeystoreSave(session: String, regId: String, status: Int, key: String = "registrationID") = {
    val keystoreUrl = s"/keystore/company-registration-frontend/$session/data/registrationID"
    stubFor(put(urlMatching(keystoreUrl))
      .willReturn(aResponse().
        withStatus(status).
        withBody(
          s"""{
             |"id": "$session",
             |"data": { "registrationID": "$regId" }
             |}""".stripMargin
        )
      )
    )
  }

  def stubUserDetails(userId: String, userDetails: String): StubMapping = {
    val getUserUrl = s"/user-details/id/$userId"
    stubFor(get(urlMatching(getUserUrl))
      .willReturn(
        aResponse().
          withStatus(200).
          withBody(
            userDetails
          )
      )
    )
  }

  def stubFootprint(status: Int, body: String) = {
    val footprintUrl = "/company-registration/throttle/check-user-access"
    stubFor(get(urlMatching(footprintUrl))
      .willReturn(
        aResponse()
          .withStatus(status)
          .withBody(body)
      )
    )
  }
}
