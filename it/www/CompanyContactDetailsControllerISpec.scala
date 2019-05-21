
package www

import java.util.UUID

import com.github.tomakehurst.wiremock.client.WireMock.{findAll, postRequestedFor, urlMatching}
import itutil.{FakeAppConfig, IntegrationSpecBase, LoginStub, RequestsFinder}
import org.jsoup.Jsoup
import play.api.http.HeaderNames
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeApplication

class CompanyContactDetailsControllerISpec extends IntegrationSpecBase with LoginStub with FakeAppConfig with RequestsFinder {
  override implicit lazy val app = FakeApplication(additionalConfiguration = fakeConfig())
  val userId = "/bar/foo"
  val csrfToken = UUID.randomUUID().toString
  val regId = "5"
  val sessionCookie = () => getSessionCookie(Map("csrfToken" -> csrfToken), userId)
  def statusResponseFromCR(status:String = "draft", rID:String = "5") =
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

      stubAuthorisation()
      stubSuccessfulLogin(userId = userId)
      stubKeystore(SessionId, regId)
      stubGet(s"/company-registration/corporation-tax-registration/$regId/retrieve-email", 200, emailResponseFromCRLowLevel)
      stubGet(s"/company-registration/corporation-tax-registration/$regId/corporation-tax-registration", 200, statusResponseFromCR())
      stubGet(s"/company-registration/corporation-tax-registration/$regId/contact-details", 404, "")

      val fResponse = await(buildClient(controllers.reg.routes.CompanyContactDetailsController.show().url)
        .withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck")
        .get())
      val doc = Jsoup.parse(fResponse.body)
      fResponse.status shouldBe 200
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

      val fResponse = await(buildClient(controllers.reg.routes.CompanyContactDetailsController.show().url)
        .withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck")
        .get())
      val doc = Jsoup.parse(fResponse.body)
      fResponse.status shouldBe 200
      doc.getElementById("main-heading").html shouldBe "Give us one or more ways to contact fooBAR"
      doc.getElementById("contactDaytimeTelephoneNumber").`val` shouldBe "12345678"
      doc.getElementById("contactMobileNumber").`val` shouldBe "45678"
      doc.getElementById("contactEmail").`val` shouldBe "foo@foo.com"
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

      stubAuthorisation()
      stubSuccessfulLogin(userId = userId, otherParamsForAuth = Some(nameAndCredId))
      stubKeystore(SessionId, regId)
      stubGet(s"/company-registration/corporation-tax-registration/$regId/corporation-tax-registration", 200, statusResponseFromCR())
      stubPut(s"/company-registration/corporation-tax-registration/$regId/contact-details", 200, contactDetailsResp.toString())
      val response = await(buildClient(controllers.reg.routes.CompanyContactDetailsController.submit().url).
        withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck").post(
        Map(
          "csrfToken"->Seq("xxx-ignored-xxx"),
          "contactDaytimeTelephoneNumber"-> Seq("12345678910"),
          "contactMobileNumber" -> Seq("1234567891011"),
          "contactEmail" -> Seq("foo@foo.com")))
      )

      response.status shouldBe 303
      response.header(HeaderNames.LOCATION).get shouldBe controllers.reg.routes.AccountingDatesController.show().url
      val audit = Json.parse(getRequestBody("post","/write/audit")).as[JsObject] \ "detail" \ "businessContactDetails"
      audit.get shouldBe Json.obj("originalEmail" -> "test@test.com","submittedEmail" -> "foo@foo.com")
      val prePop = Json.parse(getRequestBody("post",s"/business-registration/$regId/contact-details")).as[JsObject]
      prePop shouldBe Json.obj("telephoneNumber" -> "12345678910", "mobileNumber" -> "1234567891011", "email" -> "foo@foo.com")
    }
    "return 400 data is invalid" in {
      stubAuthorisation()
      stubSuccessfulLogin(userId = userId, otherParamsForAuth = Some(nameAndCredId))
      stubKeystore(SessionId, regId)
      stubGet(s"/company-registration/corporation-tax-registration/$regId/corporation-tax-registration", 200, statusResponseFromCR())

      val response = await(buildClient(controllers.reg.routes.CompanyContactDetailsController.submit().url).
        withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck").post(
        Map(
          "csrfToken"->Seq("xxx-ignored-xxx"),
          "contactDaytimeTelephoneNumber"-> Seq("1"),
          "contactMobileNumber" -> Seq("12"),
          "contactEmail" -> Seq("foo@foo.com")))
      )

      response.status shouldBe 400
      val doc = Jsoup.parse(response.body)
      doc.getElementById("main-heading").html shouldBe "Give us one or more ways to contact fooBAR"
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
      val response = await(buildClient(controllers.reg.routes.CompanyContactDetailsController.submit().url).
        withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck").post(
        Map(
          "csrfToken"->Seq("xxx-ignored-xxx"),
          "contactDaytimeTelephoneNumber"-> Seq("12345678910"),
          "contactMobileNumber" -> Seq(""),
          "contactEmail" -> Seq("")))
      )

      response.status shouldBe 303
      response.header(HeaderNames.LOCATION).get shouldBe controllers.reg.routes.AccountingDatesController.show().url
      val audit = Json.parse(getRequestBody("post","/write/audit")).as[JsObject] \ "detail" \ "businessContactDetails"
      audit.get shouldBe Json.obj("originalEmail" -> "test@test.com")
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
      val response = await(buildClient(controllers.reg.routes.CompanyContactDetailsController.submit().url).
        withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck").post(
        Map(
          "csrfToken"->Seq("xxx-ignored-xxx"),
          "contactName"->Seq("foo bar"),
          "contactDaytimeTelephoneNumber"-> Seq("12345678910"),
          "contactMobileNumber" -> Seq(""),
          "contactEmail" -> Seq("test@test.com")))
      )

      response.status shouldBe 303
      response.header(HeaderNames.LOCATION).get shouldBe controllers.reg.routes.AccountingDatesController.show().url
      intercept[Exception]((Json.parse(getRequestBody("post","/write/audit")).as[JsObject] \ "detail" \ "businessContactDetails").get)

      findAll(postRequestedFor(urlMatching("/write/audit"))).size() shouldBe 1
    }
  }
}