
package www

import fixtures.HandOffFixtures
import itutil.{FakeAppConfig, IntegrationSpecBase, LoginStub, WiremockHelper}
import play.api.Application
import play.api.http.HeaderNames
import play.api.test.FakeApplication

class HandOffsISpec extends IntegrationSpecBase with LoginStub with FakeAppConfig with HandOffFixtures {

  val mockHost = WiremockHelper.wiremockHost
  val mockPort = WiremockHelper.wiremockPort

  override implicit lazy val app: Application = FakeApplication(additionalConfiguration = fakeConfig())

  def client(path: String) = ws.url(s"http://localhost:$port/register-your-company$path").withFollowRedirects(false)
  def followRequest(path: String) = ws.url(s"http://localhost:$port$path").withFollowRedirects(false)

  val userId = "test-user-id"
  val regId = "12345"

  val userDetails =
    s"""
       |{
       |  "name":"name",
       |  "email":"test@me.com",
       |  "affinityGroup" : "Organisation",
       |  "description" : "description",
       |  "lastName":"test",
       |  "dateOfBirth":"1980-06-30",
       |  "postCode":"ZZ11ZZ",
       |  "authProviderId": "12345-PID",
       |  "authProviderType": "Verify"
       |}
     """.stripMargin

  val footprintResponse =
    s"""
       |{
       |  "registration-id":"$regId",
       |  "created":true,
       |  "confirmation-reference":false,
       |  "payment-reference":false,
       |  "email":{
       |    "address":"some@email.com",
       |    "type":"test",
       |    "link-sent":true,
       |    "verified":true
       |  }
       |}
     """.stripMargin

  Seq(
    ("HO1b", "/return-to-about-you", HO1B_PAYLOAD),
    ("HO2", "/corporation-tax-details", HO2_PAYLOAD),
    ("HO3b", "/business-activities-back", HO3B_PAYLOAD),
    ("HO4", "/corporation-tax-summary", HO4_PAYLOAD),
    ("HO5b", "/return-to-corporation-tax-summary", HO5B_PAYLOAD)
  ).foreach { case (num, url, payload) =>
    s"GET $url when keystore has expired redirect to post sign in, set up keystore and redirect back to $num" in {
      Given("The user is authorised")
      setupSimpleAuthMocks(userId)
      stubSuccessfulLogin(userId = userId)
      stubUserDetails(userId, userDetails)
      stubFootprint(200, footprintResponse)

      And("Keystore has expired")
      stubKeystore(SessionId, regId, 404)

      When(s"A GET request is made to $url with a payload")
      val response = client(s"$url?request=$payload")
        .withHeaders(HeaderNames.COOKIE -> getSessionCookie(userId = userId))
        .get()

      And("The request is redirected to /post-sign-in")
      response.status shouldBe 303
      val redirect = response.header(HeaderNames.LOCATION).get
      redirect shouldBe s"/register-your-company/post-sign-in?handOffID=$num&payload=$payload"

      And("A new keystore entry is created")
      stubKeystoreSave(SessionId, regId, 200)
      stubKeystore(SessionId, regId)

      Then(s"The request is redirected back to $url with the payload as a query string")
      val responseBack = followRequest(redirect)
        .withHeaders(HeaderNames.COOKIE -> getSessionCookie(userId = userId))
        .get()

      responseBack.status shouldBe 303
      responseBack.header(HeaderNames.LOCATION).get shouldBe s"http://localhost:9970/register-your-company$url?request=$payload"
    }
  }
}
