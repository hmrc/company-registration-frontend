
package www

import java.util.UUID

import com.github.tomakehurst.wiremock.stubbing.StubMapping
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, stubFor, urlMatching}
import itutil.{FakeAppConfig, IntegrationSpecBase, LoginStub, WiremockHelper}
import models.handoff._
import play.api.http.HeaderNames
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeApplication
import play.modules.reactivemongo.ReactiveMongoComponent
import repositories.NavModelRepo
import uk.gov.hmrc.mongo.MongoSpecSupport
import utils.Jwe

import scala.concurrent.ExecutionContext.Implicits.global

class BasicCompanyDetailsControllerISpec extends IntegrationSpecBase with MongoSpecSupport with LoginStub with FakeAppConfig {

  val mockHost = WiremockHelper.wiremockHost
  val mockPort = WiremockHelper.wiremockPort
  val testkey = "Fak3-t0K3n-f0r-pUBLic-r3p0SiT0rY"

  override implicit lazy val app = FakeApplication(additionalConfiguration = fakeConfig("microservice.services.JWE.key" -> testkey))

  private def client(path: String) = ws.url(s"http://localhost:$port/register-your-company$path").withFollowRedirects(false)

  val userId = "/bar/foo"
  val regId = "regId5"

  class Setup {
    val rc = app.injector.instanceOf[ReactiveMongoComponent]
    val repo = new NavModelRepo(rc)
    await(repo.repository.ensureIndexes)
  }

  def returnEncryptedRequest(encrypted : String) = s"/return-to-about-you?request=$encrypted"

  val forwardPayloadString =
    s"""
       |{
       |  "email_address" : "test@me.com",
       |  "user_id" : "Ext-xxx",
       |  "journey_id" : "$regId",
       |  "name" : "name",
       |  "hmrc" : {},
       |  "session" : {
       |    "timeout" : 870,
       |    "keepalive_url" : "http://localhost:9970/register-your-company${controllers.reg.routes.SignInOutController.renewSession().url}",
       |    "signedout_url" : "http://localhost:9970/register-your-company${controllers.reg.routes.SignInOutController.destroySession().url}"
       |  },
       |  "return_url" : "/initial-coho-link",
       |  "links" : {
       |    "forward" : "link-to-about-you",
       |    "reverse" : "link-to-principal-place"
       |  }
       |}
      """.stripMargin

  val forwardPayloadJson = Json.parse(forwardPayloadString).as[JsObject]

  val returnPaylodString =
    s"""
       |{
       |  "user_id" : "Ext-xxx",
       |  "journey_id" : "$regId",
       |  "hmrc" : {},
       |  "ch" : {},
       |  "links" : {}
       |}
     """.stripMargin

  val returnPayloadJson = Json.parse(forwardPayloadString).as[JsObject]

  val handOffNavModel = HandOffNavModel(
    Sender(
      Map(
        "1" -> NavLinks(
          "link-to-about-you",
          "link-to-principal-place"
        )
      )
    ),
    Receiver(
      Map(
        "0" -> NavLinks(
          "/initial-coho-link",
          "testReverseLinkFromReceiver0"
        )
      )
    )
  )

  val userDetails =
    s"""
       |{
       |  "name":"name",
       |  "email":"test@me.com",
       |  "affinityGroup" : "affinityGroup",
       |  "description" : "description",
       |  "lastName":"test",
       |  "dateOfBirth":"1980-06-30",
       |  "postCode":"NW94HD",
       |  "authProviderId": "12345-PID",
       |  "authProviderType": "Verify"
       |}
     """.stripMargin

  def stubKeystore(session: String, regId: String): StubMapping = {
    val keystoreUrl = s"/keystore/company-registration-frontend/${session}"
    stubFor(get(urlMatching(keystoreUrl))
      .willReturn(
        aResponse().
          withStatus(200).
          withBody(
            s"""{
               |"id": "${session}",
               |"data": {
               |    "registrationID": "${regId}" }
               |}""".stripMargin
          )
      )
    )
  }

  def stubGetUserDetails(userId: String): StubMapping = {
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

  "basicCompanyDetails" should {

    "call coho with a request that contains a session block" in new Setup {
      setupSimpleAuthMocks()
      stubSuccessfulLogin(userId = userId)

      val csrfToken = UUID.randomUUID().toString
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)

      stubKeystore(SessionId, regId)
      await(repo.repository.insertNavModel(regId,handOffNavModel))

      stubGetUserDetails(userId)

      val fResponse = client("/basic-company-details").
        withHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck").
        get()

      val response = await(fResponse)
      val encryptedHandOffString  = response.header(HeaderNames.LOCATION).get.split("request=").takeRight(1)(0)
      val decryptedHandoffJson  = Jwe.decrypt[JsObject](encryptedHandOffString).get

      response.status shouldBe 303
      response.header(HeaderNames.LOCATION).get should include("/initial-coho-link")
      decryptedHandoffJson shouldBe forwardPayloadJson
    }
  }

  "returnToAboutYou" should {

    "redirect to completion capacity if the payload is correct" in new Setup {
      setupSimpleAuthMocks()
      stubSuccessfulLogin(userId = userId)

      val csrfToken = UUID.randomUUID().toString
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)

      stubKeystore(SessionId, regId)

      val fResponse = client(returnEncryptedRequest(Jwe.encrypt[JsObject](returnPayloadJson).get)).
        withHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck").
        get()

      val response = await(fResponse)

      response.status shouldBe 303
      response.header(HeaderNames.LOCATION).get should include("/about-you")
    }

    "return a bad request if there is an incorrect request" in new Setup {
      setupSimpleAuthMocks()
      stubSuccessfulLogin(userId = userId)

      val csrfToken = UUID.randomUUID().toString
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)

      stubKeystore(SessionId, regId)

      val fResponse = client(returnEncryptedRequest("malformed-encrypted-json")).
        withHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck").
        get()

      val response = await(fResponse)

      response.status shouldBe 400
    }
  }
}
