
package www

import java.util.UUID

import itutil.{FakeAppConfig, IntegrationSpecBase, LoginStub}
import models.{BusinessRegistration, Links}
import org.jsoup.Jsoup
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.test.FakeApplication

class CompletionCapacityControllerISpec extends IntegrationSpecBase with LoginStub with FakeAppConfig  {

  override implicit lazy val app = FakeApplication(additionalConfiguration = fakeConfig())
  val regId = "5"
  val userId = "/bar/foo"
  val csrfToken = UUID.randomUUID().toString

  val businessRegResponse =  Json.toJson(BusinessRegistration(
    regId,
    "2016-08-03T10:49:11Z",
    "en",
    Some("director"),
    Links(Some("foo bar"))
  )).toString

  def statusResponseFromCR(status:String = "draft", rID:String = "5") =
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

  s"${controllers.reg.routes.CompletionCapacityController.show().url}" should {
    "return 200" in {
      stubAuthorisation()
      stubSuccessfulLogin()
      stubKeystore(SessionId, regId)
      stubBusinessRegRetrieveMetaDataNoRegId(200, businessRegResponse)
      stubGet(s"/company-registration/corporation-tax-registration/$regId/corporation-tax-registration", 200, statusResponseFromCR())
     val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)
     val response =  await(buildClient(controllers.reg.routes.CompletionCapacityController.show().url)
       .withHeaders(HeaderNames.COOKIE -> sessionCookie)
       .get())
     response.status shouldBe 200

     val doc = Jsoup.parse(response.body)
     doc.getElementById("completionCapacity-director").attr("checked") shouldBe "checked"
     doc.getElementById("completionCapacityOther").`val` shouldBe ""
    }
  }

  s"${controllers.reg.routes.CompletionCapacityController.submit().url}" should {
    "redirect with a status of 303 with valid data" in {
      stubAuthorisation()
      stubSuccessfulLogin()
      stubKeystore(SessionId, regId)
      stubBusinessRegRetrieveMetaDataWithRegId(regId, 200, businessRegResponse)
      stubUpdateBusinessRegistrationCompletionCapacity(regId, 200, businessRegResponse)
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)

      val response = await(buildClient(controllers.reg.routes.CompletionCapacityController.submit().url).
        withHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck").post(
          Map("csrfToken"->Seq("xxx-ignored-xxx"),"completionCapacity"->Seq("director"),"completionCapacityOther"->Seq("")))
      )

      response.status shouldBe 303
      response.header(HeaderNames.LOCATION) shouldBe Some(controllers.handoff.routes.BasicCompanyDetailsController.basicCompanyDetails().url)
    }
    "return 400 to the user and display the appropriate error messages" in {
      stubAuthorisation()
      stubSuccessfulLogin()
      stubKeystore(SessionId, regId)
      stubBusinessRegRetrieveMetaDataWithRegId(regId, 200, businessRegResponse)
      stubUpdateBusinessRegistrationCompletionCapacity(regId, 200, businessRegResponse)
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)

      val response = await(buildClient(controllers.reg.routes.CompletionCapacityController.submit().url).
        withHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck").post(
        Map("csrfToken"->Seq("xxx-ignored-xxx"),"completionCapacity"->Seq(""),"completionCapacityOther"->Seq("bar")))
      )

      response.status shouldBe 400
      val doc = Jsoup.parse(response.body)
      Option(doc.getElementById("completionCapacity-error-summary")).isDefined shouldBe true
    }
  }
}