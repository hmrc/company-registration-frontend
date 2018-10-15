
package www

import java.util.UUID

import itutil.{FakeAppConfig, IntegrationSpecBase, LoginStub}
import models._
import org.jsoup.Jsoup
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.test.FakeApplication

class SummaryControllerISpec extends IntegrationSpecBase with LoginStub with FakeAppConfig {
  val userId = "/bar/foo"
  val csrfToken = UUID.randomUUID().toString
  val regId = "5"
  val sessionCookie = () => getSessionCookie(Map("csrfToken" -> csrfToken), userId)
  override implicit lazy val app = FakeApplication(additionalConfiguration = fakeConfig())
  "Display summary correctly with full model, including contact details WITH contact name" in {
    stubAuthorisation()
    stubSuccessfulLogin(userId = userId)
    stubKeystore(SessionId, regId)
    stubBusinessRegRetrieveMetaDataWithRegId(regId,200,Json.toJson(BusinessRegistration(regId,"123","en",Some("director"),Links(Some("foo"),Some("bar")))).toString())
    stubGet(s"/company-registration/corporation-tax-registration/$regId/accounting-details", 200, """{"accountingDateStatus":"FUTURE_DATE", "startDateOfBusiness":"2019-01-02", "links": []}""")

    stubGet(s"/company-registration/corporation-tax-registration/$regId/contact-details", 200, Json.parse(
      """|{
        | "contactFirstName": "wizz",
        | "contactMiddleName": "bar1",
        | "contactSurname": "bar2",
        | "contactDaytimeTelephoneNumber": "12345678",
        | "contactMobileNumber": "45678",
        | "contactEmail": "foo@foo.com",
        | "links": {
        | }
        |}
      """.stripMargin).toString())

    stubGet(s"/company-registration/corporation-tax-registration/$regId/company-details", 200, Json.toJson(CompanyDetails(
      "testCompanyName",
      CHROAddress(
        "Premises", "Line1", Some("Line2"), "Locality", "Country",Some("PO Box"), Some("FX1 1ZZ"), Some("Region")
      ),
      PPOB(
        "RO", None
      ),
      "testJurisdiction"
    )).toString())

    stubGet(s"/company-registration/corporation-tax-registration/$regId/trading-details", 200, Json.toJson(TradingDetails("true")).toString)
    stubGet(s"/company-registration/corporation-tax-registration/$regId/corporation-tax-registration", 200, s"""{
                                                                                                              |    "registrationID" : "${regId}",
                                                                                                              |    "status" : "draft"
                                                                                                              |}""".stripMargin)

    val fResponse = await(buildClient(controllers.reg.routes.SummaryController.show().url)
      .withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck")
      .get())
    val doc = Jsoup.parse(fResponse.body)
    fResponse.status shouldBe 200
    doc.getElementById("contactNameBlock").id shouldBe "contactNameBlock"
    doc.getElementById("companyContactDetails").id shouldBe "companyContactDetails"
    doc.getElementById("companyContactName").html shouldBe "wizz bar1 bar2"
    doc.getElementById("change-contact-details").attr("href").contains(controllers.reg.routes.CompanyContactDetailsController.show().url) shouldBe true
    doc.getElementById("contactEmailBlock").id shouldBe "contactEmailBlock"
    doc.getElementById("contactTelBlock").id shouldBe "contactTelBlock"
    doc.getElementById("contactMobBlock").id shouldBe "contactMobBlock"
  }
  "Display summary correctly with full model, including contact details WITHOUT contact name negating field from html" in {
    stubAuthorisation()
    stubSuccessfulLogin(userId = userId)
    stubKeystore(SessionId, regId)
    stubBusinessRegRetrieveMetaDataWithRegId(regId,200,Json.toJson(BusinessRegistration(regId,"123","en",Some("director"),Links(Some("foo"),Some("bar")))).toString())
    stubGet(s"/company-registration/corporation-tax-registration/$regId/accounting-details", 200, """{"accountingDateStatus":"FUTURE_DATE", "startDateOfBusiness":"2019-01-02", "links": []}""")

    stubGet(s"/company-registration/corporation-tax-registration/$regId/contact-details", 200, Json.parse(
      """|{
         | "contactDaytimeTelephoneNumber": "12345678",
         | "contactMobileNumber": "45678",
         | "contactEmail": "foo@foo.com",
         | "links": {
         | }
         |}
      """.stripMargin).toString())

    stubGet(s"/company-registration/corporation-tax-registration/$regId/company-details", 200, Json.toJson(CompanyDetails(
      "testCompanyName",
      CHROAddress(
        "Premises", "Line1", Some("Line2"), "Locality", "Country",Some("PO Box"), Some("FX1 1ZZ"), Some("Region")
      ),
      PPOB(
        "RO", None
      ),
      "testJurisdiction"
    )).toString())

    stubGet(s"/company-registration/corporation-tax-registration/$regId/trading-details", 200, Json.toJson(TradingDetails("true")).toString)
    stubGet(s"/company-registration/corporation-tax-registration/$regId/corporation-tax-registration", 200, s"""{
                                                                                                               |    "registrationID" : "${regId}",
                                                                                                               |    "status" : "draft"
                                                                                                               |}""".stripMargin)

    val fResponse = await(buildClient(controllers.reg.routes.SummaryController.show().url)
      .withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck")
      .get())
    val doc = Jsoup.parse(fResponse.body)
    fResponse.status shouldBe 200
    intercept[Exception](doc.getElementById("contactNameBlock").id)
    doc.getElementById("companyContactDetails").id shouldBe "companyContactDetails"
    intercept[Exception](doc.getElementById("companyContactName").html)
    doc.getElementById("contactEmailBlock").id shouldBe "contactEmailBlock"
    doc.getElementById("contactTelBlock").id shouldBe "contactTelBlock"
    doc.getElementById("contactMobBlock").id shouldBe "contactMobBlock"

  }
}
