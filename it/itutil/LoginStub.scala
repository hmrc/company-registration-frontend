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

package itutil

import java.net.{URLDecoder, URLEncoder}
import java.nio.charset.StandardCharsets
import java.util.UUID

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.http.HeaderNames
import play.api.libs.Crypto
import play.api.libs.crypto.DefaultCookieSigner
import play.api.libs.json.{JsObject, Json}
import play.api.libs.ws.WSCookie
import uk.gov.hmrc.crypto.{CompositeSymmetricCrypto, Crypted, PlainText}
import uk.gov.hmrc.http.SessionKeys

trait LoginStub extends SessionCookieBaker {
  private val defaultUser = "/foo/bar"

  val SessionId         = s"stubbed-${UUID.randomUUID}"
  val invalidSessionId  = s"FAKE_PRF::NON-COMPSDOJ%OMSDDf"

  val userIdKey: String = "userId"
  val tokenKey: String = "token"
  val authProviderKey: String = "ap"

  private def cookieData(additionalData: Map[String, String], userId: String = defaultUser, sessionId: String = SessionId): Map[String, String] = {
    Map(
      SessionKeys.sessionId -> sessionId,
      userIdKey -> userId,
      tokenKey -> "token",
      authProviderKey -> "GGW",
      SessionKeys.lastRequestTimestamp -> new java.util.Date().getTime.toString
    ) ++ additionalData
  }

  def stubPostAuth(url: String, status: Integer, body: Option[String]): StubMapping = {
    stubFor(post(urlMatching(url)).willReturn {
      val resp = aResponse().withStatus(status)
      val respHeaders = if (status == 401) resp.withHeader(HeaderNames.WWW_AUTHENTICATE, """MDTP detail="MissingBearerToken"""") else resp

      body.fold(respHeaders)(b => respHeaders.withBody(b))
    })
  }

  def stubAuthorisation(status: Int = 200, resp: Option[String] = None): StubMapping = {
    stubPostAuth("/write/audit", 200, Some("""{"x":2}"""))
    stubPostAuth(
      url = "/auth/authorise",
      status = status,
      body = resp match {
        case Some(_) => resp
        case None    => Some(Json.obj("authorise" -> Json.arr(), "retrieve" -> Json.arr()).toString())
      }
    )
  }

  def getSessionCookie(additionalData: Map[String, String] = Map(), userId: String = defaultUser, sessionId: String = SessionId): String = {
    cookieValue(cookieData(additionalData, userId, sessionId))
  }

  def stubSuccessfulLogin(withSignIn: Boolean = false, userId: String = defaultUser, otherParamsForAuth: Option[JsObject] = None): StubMapping = {

    if( withSignIn ) {
      val continueUrl = "/wibble"
      stubFor(get(urlEqualTo(s"/gg/sign-in?continue=${continueUrl}"))
        .willReturn(aResponse()
          .withStatus(303)
          .withHeader(HeaderNames.SET_COOKIE, getSessionCookie())
          .withHeader(HeaderNames.LOCATION, continueUrl)))
    }

    val authJson = Json.parse(s"""
                                 |{
                                 |    "uri": "${userId}",
                                 |    "internalId": "some-id",
                                 |    "affinityGroup": "Organisation",
                                 |    "loginTimes": {
                                 |      "currentLogin": "2014-06-09T14:57:09.522Z",
                                 |      "previousLogin": "2014-06-09T14:48:24.841Z"
                                 |    },
                                 |    "credentials": {
                                 |      "providerId": "12345-credId",
                                 |      "providerType": "GovernmmentGateway"
                                 |    },
                                 |    "email":"test@test.com",
                                 |    "allEnrolments": [],
                                 |    "confidenceLevel" : 50,
                                 |    "credentialStrength": "weak"
                                 |}
            """.stripMargin).as[JsObject].deepMerge(otherParamsForAuth.fold(Json.obj())(identity))


    stubAuthorisation(200, Some(authJson.toString()))
  }

  def setupSimpleAuthMocks(userId: String = defaultUser) = {


    stubFor(get(urlMatching("/auth/authority"))
      .willReturn(
        aResponse().
          withStatus(200).
          withBody(s"""
                      |{
                      |"uri":"$userId",
                      |"accounts":{},
                      |"levelOfAssurance": "2",
                      |"confidenceLevel" : 50,
                      |"credentialStrength": "strong",
                      |"userDetailsUri" : "/user-details/id$userId",
                      |"legacyOid":"1234567890"
                      |}""".stripMargin)
      )
    )
  }
}

trait SessionCookieBaker {
  val defaultCookieSigner: DefaultCookieSigner
  val cookieKey = "gvBoGdgzqG1AarzF1LY0zQ=="
  def cookieValue(sessionData: Map[String,String]) = {
    def encode(data: Map[String, String]): PlainText = {
      val encoded = data.map {
        case (k, v) => URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
      }.mkString("&")
      val key = "yNhI04vHs9<_HWbC`]20u`37=NGLGYY5:0Tg5?y`W<NoJnXWqmjcgZBec@rOxb^G".getBytes
      PlainText(defaultCookieSigner.sign(encoded, key) + "-" + encoded)
    }

    val encodedCookie = encode(sessionData)
    val encrypted = CompositeSymmetricCrypto.aesGCM(cookieKey, Seq()).encrypt(encodedCookie).value

    s"""mdtp="$encrypted"; Path=/; HTTPOnly"; Path=/; HTTPOnly"""
  }

  def getCookieData(cookie: WSCookie): Map[String, String] = {
    getCookieData(cookie.value)
  }

  def getCookieData(cookieData: String): Map[String, String] = {

    val decrypted = CompositeSymmetricCrypto.aesGCM(cookieKey, Seq()).decrypt(Crypted(cookieData)).value
    val result = decrypted.split("&")
      .map(_.split("="))
      .map { case Array(k, v) => (k, URLDecoder.decode(v, StandardCharsets.UTF_8.name()))}
      .toMap

    result
  }
}
