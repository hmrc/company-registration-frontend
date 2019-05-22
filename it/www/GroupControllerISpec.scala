
package www

import java.util.UUID

import com.github.tomakehurst.wiremock.client.WireMock._
import config.FrontendAppConfig
import fixtures.{Fixtures, HandOffFixtures}
import itutil._
import models.{Groups, NewAddress}
import models.handoff.{NavLinks, PSCHandOff}
import org.jsoup.Jsoup
import play.api.http.HeaderNames
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeApplication
import play.modules.reactivemongo.ReactiveMongoComponent
import repositories.NavModelRepo
import utils.{BooleanFeatureSwitch, JweCommon, SCRSFeatureSwitches}

import scala.concurrent.ExecutionContext.Implicits.global

class GroupControllerISpec extends IntegrationSpecBase with LoginStub with FakeAppConfig with HandOffFixtures with Fixtures with RequestsFinder {

  override implicit lazy val app = FakeApplication(additionalConfiguration = fakeConfig())

  val userId = "test-user-id"
  val regId = "12345"
  val txid = "txid-1"

  class Setup {
    val csrfToken = () => UUID.randomUUID().toString
    val sessionCookie = () => getSessionCookie(Map("csrfToken" -> csrfToken()), userId)
    val statusResponseFromCR = s"""
         |{
         |    "registrationID" : "$regId",
         |    "status" : "draft",
         |        "verifiedEmail" : {
         |        "address" : "user@test.com",
         |        "type" : "GG",
         |        "link-sent" : true,
         |        "verified" : true,
         |        "return-link-email-sent" : false
         |    },
         |    "companyDetails" : {
         |        "companyName" : "Company Name Ltd"
         |        }
         |}
     """.stripMargin

    val listOfShareHoldersFromII = s"""[{
                                             |  "percentage_dividend_rights": 75,
                                             |  "percentage_voting_rights": 75,
                                             |  "percentage_capital_rights": 75,
                                             |  "corporate_name": "big company",
                                             |    "address": {
                                             |    "premises": "11",
                                             |    "address_line_1": "Add L1",
                                             |    "address_line_2": "Add L2",
                                             |    "locality": "London",
                                             |    "country": "United Kingdom",
                                             |    "postal_code": "ZZ1 1ZZ"
                                             |      }
                                             |    },{
                                             |    "surname": "foo will never show",
                                             |    "forename" : "bar will never show",
                                             |    "percentage_dividend_rights": 75,
                                             |    "percentage_voting_rights": 75,
                                             |    "percentage_capital_rights": 75,
                                             |    "address": {
                                             |    "premises": "11",
                                             |    "address_line_1": "Add L1",
                                             |    "address_line_2": "Add L2",
                                             |    "locality": "London",
                                             |    "country": "United Kingdom",
                                             |    "postal_code": "ZZ1 1ZZ"
                                             |      }
                                             |    }
                                             |]""".stripMargin
    val featureSwitch =  app.injector.instanceOf[SCRSFeatureSwitches]
    featureSwitch.featureSwitchManager.enable(BooleanFeatureSwitch("pscHandOff", true))
    val repo = new NavModelRepo {
      override val mongo: ReactiveMongoComponent = app.injector.instanceOf[ReactiveMongoComponent]
      override val appConfig: FrontendAppConfig = app.injector.instanceOf[FrontendAppConfig]
    }
    await(repo.repository.drop)
    await(repo.repository.count) shouldBe 0
    await(repo.repository.ensureIndexes)
    val jweDecryptor = app.injector.instanceOf[JweCommon]
  }

  s"${controllers.handoff.routes.GroupController.groupHandBack("").url}" should {
    "return 303 when a valid payload is passed in with the shareholders flag and updates NavModel successfully" in new Setup {
        stubSuccessfulLogin(userId = userId)
        stubFootprint(200, footprintResponse(regId))
        stubKeystore(SessionId, regId)
      stubGet(s"/company-registration/corporation-tax-registration/$regId/confirmation-references", 200, s"""  {
                                                                                                            |        "acknowledgement-reference" : "ABCD00000000001",
                                                                                                            |        "transaction-id" : "$txid",
                                                                                                            |        "payment-reference" : "PAY_REF-123456789",
                                                                                                            |        "payment-amount" : "12"
                                                                                                            |    }""".stripMargin)
      await(repo.repository.insertNavModel(regId, handOffNavModelDataUpTo3))
      await(repo.repository.count) shouldBe 1

      val fResponse = buildClient(controllers.handoff.routes.GroupController.groupHandBack(H03_1_PAYLOAD_FLAG).url).
        withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck").
        get()

      val response = await(fResponse)
      await(repo.repository.getNavModel(regId)).get shouldBe handOffNavModelDataUpTo3_1
      response.status shouldBe 303
      response.header(HeaderNames.LOCATION).get shouldBe controllers.handoff.routes.GroupController.PSCGroupHandOff().url
    }
    "return 400 when a valid payload is passed in without flag (this does not happen any more)" in new Setup {
      stubSuccessfulLogin(userId = userId)
      stubFootprint(200, footprintResponse(regId))
      stubKeystore(SessionId, regId)
      await(repo.repository.insertNavModel(regId, handOffNavModelDataUpTo3))
      await(repo.repository.count) shouldBe 1

      val fResponse = buildClient(controllers.handoff.routes.GroupController.groupHandBack(H03_1_PAYLOAD_NO_FLAG).url).
        withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck").
        get()

      val response = await(fResponse)
      await(repo.repository.getNavModel(regId)).get shouldBe handOffNavModelDataUpTo3
      response.status shouldBe 400
    }
    "return 400 when payload contains invalid data" in new Setup {
      stubSuccessfulLogin(userId = userId)
      stubFootprint(200, footprintResponse(regId))
      stubKeystore(SessionId, regId)
      await(repo.repository.insertNavModel(regId, handOffNavModelDataUpTo3))
      await(repo.repository.count) shouldBe 1

      val fResponse = buildClient(controllers.handoff.routes.GroupController.groupHandBack("").url).
        withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck").
        get()

      val response = await(fResponse)
      await(repo.repository.getNavModel(regId)).get shouldBe handOffNavModelDataUpTo3
      response.status shouldBe 400
    }
  }
"pSCGroupHandBack" should {
  "redirect to group utr with full group block" in new Setup {
    stubSuccessfulLogin(userId = userId)
    stubFootprint(200, footprintResponse(regId))
    stubKeystore(SessionId, regId)
    stubGet(s"/company-registration/corporation-tax-registration/$regId/confirmation-references", 200, s"""  {
                                                                                                          |        "acknowledgement-reference" : "ABCD00000000001",
                                                                                                          |        "transaction-id" : "$txid",
                                                                                                          |        "payment-reference" : "PAY_REF-123456789",
                                                                                                          |        "payment-amount" : "12"
                                                                                                          |    }""".stripMargin)
    val groups = Json.parse("""{
                              |   "groupRelief": true,
                              |   "nameOfCompany": {
                              |     "name": "foo",
                              |     "nameType" : "Other"
                              |   },
                              |   "addressAndType" : {
                              |     "addressType" : "ALF",
                              |       "address" : {
                              |         "line1": "1 abc",
                              |         "line2" : "2 abc",
                              |         "line3" : "3 abc",
                              |         "line4" : "4 abc",
                              |         "country" : "country A",
                              |         "postcode" : "ZZ1 1ZZ"
                              |     }
                              |   },
                              |   "groupUTR" : {
                              |     "UTR" : "1234567890"
                              |   }
                              |}""".stripMargin).toString()
    stubGet(s"/company-registration/corporation-tax-registration/$regId/groups",200, groups)
    await(repo.repository.insertNavModel(regId, handOffNavModelDataUpTo3))
    await(repo.repository.count) shouldBe 1

    val fResponse = buildClient(controllers.handoff.routes.GroupController.pSCGroupHandBack(HO3B_PAYLOAD).url).
      withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck").
      get()

    val response = await(fResponse)
    response.status shouldBe 303
    response.header(HeaderNames.LOCATION).get shouldBe "/register-your-company/owning-companys-utr"
  }
  "redirect to group relief with relief == false" in new Setup {
    stubSuccessfulLogin(userId = userId)
    stubFootprint(200, footprintResponse(regId))
    stubKeystore(SessionId, regId)
    stubGet(s"/company-registration/corporation-tax-registration/$regId/confirmation-references", 200, s"""  {
                                                                                                          |        "acknowledgement-reference" : "ABCD00000000001",
                                                                                                          |        "transaction-id" : "$txid",
                                                                                                          |        "payment-reference" : "PAY_REF-123456789",
                                                                                                          |        "payment-amount" : "12"
                                                                                                          |    }""".stripMargin)
    val groups = Json.parse("""{
                              |   "groupRelief": false
                              |
                              |}""".stripMargin).toString()
    stubGet(s"/company-registration/corporation-tax-registration/$regId/groups",200, groups)
    await(repo.repository.insertNavModel(regId, handOffNavModelDataUpTo3))
    await(repo.repository.count) shouldBe 1

    val fResponse = buildClient(controllers.handoff.routes.GroupController.pSCGroupHandBack(HO3B_PAYLOAD).url).
      withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck").
      get()

    val response = await(fResponse)
    response.status shouldBe 303
    response.header(HeaderNames.LOCATION).get shouldBe "/register-your-company/group-relief"
  }
}

  s"${controllers.handoff.routes.GroupController.PSCGroupHandOff().url}" should {
    "Redirect to PSC hand off url from 3-1 entry in nav Model with NO GROUPS in cr" in new Setup {

      stubGet(s"/company-registration/corporation-tax-registration/$regId/groups",204, "")
      stubSuccessfulLogin(userId = userId,otherParamsForAuth = Some(Json.obj("externalId" -> "foo")))
      stubFootprint(200, footprintResponse(regId))
      stubKeystore(SessionId, regId)
      await(repo.repository.insertNavModel(regId, handOffNavModelDataWithJust3_2Requirements))
      await(repo.repository.count) shouldBe 1

      val fResponse = buildClient(controllers.handoff.routes.GroupController.PSCGroupHandOff().url).
        withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck")
        .get()

      val response = await(fResponse)
      response.status shouldBe 303
      await(repo.repository.getNavModel(regId)).get shouldBe handOffNavModelDataWithJust3_2Requirements
      val url = response.header(HeaderNames.LOCATION).get

      val payload = PayloadExtractor.extractPayload(url)
      val decryptedPayload = jweDecryptor.decrypt[JsObject](payload)
      val expected = PSCHandOff(
        "foo",
        "12345",
        Json.obj(),
        Some(Json.obj("testCHBagKey" -> "testValue")),
        NavLinks("/forwardToNextHmrcPage","/reverseToPreviousHmrcPage"),
        false)
      decryptedPayload.get shouldBe Json.parse("""{"user_id":"foo","journey_id":"12345","hmrc":{},"another_company_own_shares":false,"ch":{"testCHBagKey":"testValue"},"links":{"forward":"/forwardToNextHmrcPage","reverse":"http://localhost:9970/register-your-company/business-activities-back"}}""".stripMargin)
    }
    "Redirect to PSC hand off url from 3-1 entry in nav Model with FULL groups in cr" in new Setup {
      val groups = Json.parse("""{
                                  |   "groupRelief": true,
                                  |   "nameOfCompany": {
                                  |     "name": "foo",
                                  |     "nameType" : "Other"
                                  |   },
                                  |   "addressAndType" : {
                                  |     "addressType" : "ALF",
                                  |       "address" : {
                                  |         "line1": "1 abc",
                                  |         "line2" : "2 abc",
                                  |         "line3" : "3 abc",
                                  |         "line4" : "4 abc",
                                  |         "country" : "country A",
                                  |         "postcode" : "ZZ1 1ZZ"
                                  |     }
                                  |   },
                                  |   "groupUTR" : {
                                  |     "UTR" : "1234567890"
                                  |   }
                                  |}""".stripMargin).toString()
      stubGet(s"/company-registration/corporation-tax-registration/$regId/groups",200, groups)
      stubSuccessfulLogin(userId = userId,otherParamsForAuth = Some(Json.obj("externalId" -> "foo")))
      stubFootprint(200, footprintResponse(regId))
      stubKeystore(SessionId, regId)
      await(repo.repository.insertNavModel(regId, handOffNavModelDataWithJust3_2Requirements))
      await(repo.repository.count) shouldBe 1

      val fResponse = buildClient(controllers.handoff.routes.GroupController.PSCGroupHandOff().url).
        withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck")
        .get()

      val response = await(fResponse)
      response.status shouldBe 303
      await(repo.repository.getNavModel(regId)).get shouldBe handOffNavModelDataWithJust3_2Requirements
      val url = response.header(HeaderNames.LOCATION).get

      val payload = PayloadExtractor.extractPayload(url)
      val decryptedPayload = jweDecryptor.decrypt[JsObject](payload)

      decryptedPayload.get shouldBe Json.parse("""{"user_id":"foo","journey_id":"12345","hmrc":{},"another_company_own_shares":true,"ch":{"testCHBagKey":"testValue"},"parent_company":{"name":"foo","address":{"address_line_1":"1 abc","address_line_2":"2 abc","address_line_3":"3 abc","address_line_4":"4 abc","country":"country A","postal_code":"ZZ1 1ZZ"},"tax_reference":"*******890"},"links":{"forward":"/forwardToNextHmrcPage","reverse":"http://localhost:9970/register-your-company/groups-back-handback","loss_relief_group":"http://localhost:9970/register-your-company/group-relief","parent_address":"http://localhost:9970/register-your-company/owning-companys-address","parent_company_name":"http://localhost:9970/register-your-company/owning-companys-name","parent_tax_reference":"http://localhost:9970/register-your-company/owning-companys-utr"},"loss_relief_group":true}""".stripMargin)
    }
    "Redirect to PSC hand off url from 3-1 entry in nav Model with groups loss relief of false" in new Setup {
      val groups = Json.parse("""{
                                |   "groupRelief": false
                                |}""".stripMargin).toString()
      stubGet(s"/company-registration/corporation-tax-registration/$regId/groups",200, groups)
      stubSuccessfulLogin(userId = userId,otherParamsForAuth = Some(Json.obj("externalId" -> "foo")))
      stubFootprint(200, footprintResponse(regId))
      stubKeystore(SessionId, regId)
      await(repo.repository.insertNavModel(regId, handOffNavModelDataWithJust3_2Requirements))
      await(repo.repository.count) shouldBe 1
      val fResponse = buildClient(controllers.handoff.routes.GroupController.PSCGroupHandOff().url).
        withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck")
        .get()

      val response = await(fResponse)
      response.status shouldBe 303
      await(repo.repository.getNavModel(regId)).get shouldBe handOffNavModelDataWithJust3_2Requirements
      val url = response.header(HeaderNames.LOCATION).get

      val payload = PayloadExtractor.extractPayload(url)
      val decryptedPayload = jweDecryptor.decrypt[JsObject](payload)
      decryptedPayload.get shouldBe Json.parse("""{"user_id":"foo","journey_id":"12345","hmrc":{},"another_company_own_shares":true,"ch":{"testCHBagKey":"testValue"},"links":{"forward":"/forwardToNextHmrcPage","reverse":"http://localhost:9970/register-your-company/groups-back-handback","loss_relief_group":"http://localhost:9970/register-your-company/group-relief"},"loss_relief_group":false}""".stripMargin)
    }
    "Redirect to post sign in if no nav model exists" in new Setup {
      stubGet(s"/company-registration/corporation-tax-registration/$regId/groups",204, "")
      stubSuccessfulLogin(userId = userId,otherParamsForAuth = Some(Json.obj("externalId" -> "foo")))
      stubFootprint(200, footprintResponse(regId))
      stubKeystore(SessionId, regId)
      await(repo.repository.count) shouldBe 0

      val fResponse = buildClient(controllers.handoff.routes.GroupController.PSCGroupHandOff().url).
        withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck")
        .get()

      val response = await(fResponse)
      response.status shouldBe 303
      response.header(HeaderNames.LOCATION).get shouldBe controllers.reg.routes.SignInOutController.postSignIn(None, None, None).url
    }
  }

  "GroupReliefGET" should {
    "return 200 and pre populate page" in new Setup {
      stubSuccessfulLogin(userId = userId,otherParamsForAuth = Some(Json.obj("externalId" -> "foo")))
      stubFootprint(200, footprintResponse(regId))
      stubKeystore(SessionId, regId)
      val expected = Json.parse("""{
                                   |   "groupRelief": true,
                                   |   "nameOfCompany": {
                                   |     "name": "foo",
                                   |     "nameType" : "Other"
                                   |   },
                                   |   "addressAndType" : {
                                   |     "addressType" : "ALF",
                                   |       "address" : {
                                   |         "line1": "1 abc",
                                   |         "line2" : "2 abc",
                                   |         "line3" : "3 abc",
                                   |         "line4" : "4 abc",
                                   |         "country" : "country A",
                                   |         "postcode" : "ZZ1 1ZZ"
                                   |     }
                                   |   },
                                   |   "groupUTR" : {
                                   |     "UTR" : "1234567890"
                                   |   }
                                   |}""".stripMargin).toString()
      stubGet(s"/company-registration/corporation-tax-registration/$regId/groups",200, expected)
      stubGet(s"/company-registration/corporation-tax-registration/$regId/corporation-tax-registration", 200, statusResponseFromCR)

      val fResponse = buildClient(controllers.groups.routes.GroupReliefController.show().url).
        withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck")
        .get()
      val doc = Jsoup.parse(await(fResponse.body))
      doc.getElementById("groupRelief-true").attr("checked") shouldBe "checked"
    }
  }
  "GroupNameGET" should {
    "return 200 and pre populate page when Other is saved" in new Setup {
      val expected = Json.parse("""{
                                   |   "groupRelief": true,
                                   |   "nameOfCompany": {
                                   |     "name": "foo",
                                   |     "nameType" : "Other"
                                   |   },
                                   |   "addressAndType" : {
                                   |     "addressType" : "ALF",
                                   |       "address" : {
                                   |         "line1": "1 abc",
                                   |         "line2" : "2 abc",
                                   |         "line3" : "3 abc",
                                   |         "line4" : "4 abc",
                                   |         "country" : "country A",
                                   |         "postcode" : "ZZ1 1ZZ"
                                   |     }
                                   |   },
                                   |   "groupUTR" : {
                                   |     "UTR" : "1234567890"
                                   |   }
                                   |}""".stripMargin).toString
      stubSuccessfulLogin(userId = userId,otherParamsForAuth = Some(Json.obj("externalId" -> "foo")))
      stubFootprint(200, footprintResponse(regId))
      stubKeystore(SessionId, regId)
      stubGet(s"/incorporation-information/shareholders/$txid", 200, listOfShareHoldersFromII)
      stubGet(s"/company-registration/corporation-tax-registration/$regId/groups",200, expected)
      stubGet(s"/company-registration/corporation-tax-registration/$regId/corporation-tax-registration", 200, statusResponseFromCR)
      stubGet(s"/company-registration/corporation-tax-registration/$regId/confirmation-references", 200, s"""  {
                                                                                                           |        "acknowledgement-reference" : "ABCD00000000001",
                                                                                                           |        "transaction-id" : "$txid",
                                                                                                           |        "payment-reference" : "PAY_REF-123456789",
                                                                                                           |        "payment-amount" : "12"
                                                                                                           |    }""".stripMargin)
      stubPost(s"/company-registration/corporation-tax-registration/check-list-of-group-names", 200, """["wizz", "bar ", "bar"]""")

      val fResponse = buildClient(controllers.groups.routes.GroupNameController.show().url).
        withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck")
        .get()
      val doc = Jsoup.parse(await(fResponse.body))

      doc.getElementById("groupName-othername").attr("checked") shouldBe "checked"
      doc.getElementById("otherName").`val` shouldBe "foo"
      doc.getElementById("groupName-wizz").attr("value") shouldBe "wizz"
      doc.getElementById("groupName-wizz").attr("checked") shouldBe ""
      doc.getElementById("groupName-bar_").attr("value") shouldBe "bar "
      doc.getElementById("groupName-bar_").attr("checked") shouldBe ""
      doc.getElementById("groupName-bar").attr("value") shouldBe "bar"
      doc.getElementById("groupName-bar").attr("checked") shouldBe ""
    }
    "return 200 and pre populate page when CohoEntered is saved && shareholder name still in list of shareholders" in new Setup {
      val expected = Json.parse("""{
                          |   "groupRelief": true,
                          |   "nameOfCompany": {
                          |     "name": "Foo Bar",
                          |     "nameType" : "CohoEntered"
                          |   }
                          |}""".stripMargin).toString
      stubSuccessfulLogin(userId = userId,otherParamsForAuth = Some(Json.obj("externalId" -> "foo")))
      stubFootprint(200, footprintResponse(regId))
      stubKeystore(SessionId, regId)
      stubGet(s"/incorporation-information/shareholders/$txid", 200, listOfShareHoldersFromII)
      stubGet(s"/company-registration/corporation-tax-registration/$regId/groups",200, expected)
      stubGet(s"/company-registration/corporation-tax-registration/$regId/corporation-tax-registration", 200, statusResponseFromCR)
      stubGet(s"/company-registration/corporation-tax-registration/$regId/confirmation-references", 200, s"""  {
                                                                                                             |        "acknowledgement-reference" : "ABCD00000000001",
                                                                                                             |        "transaction-id" : "$txid",
                                                                                                             |        "payment-reference" : "PAY_REF-123456789",
                                                                                                             |        "payment-amount" : "12"
                                                                                                             |    }""".stripMargin)
      stubPost(s"/company-registration/corporation-tax-registration/check-list-of-group-names", 200, """["Foo Bar", "bar"]""")

      val fResponse = buildClient(controllers.groups.routes.GroupNameController.show().url).
        withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck")
        .get()
      val doc = Jsoup.parse(await(fResponse.body))
      doc.getElementById("groupName-othername").attr("checked") shouldBe ""
      doc.getElementById("otherName").`val` shouldBe ""
      doc.getElementById("otherName-hidden").attr("style") shouldBe ""
      doc.getElementById("groupName-foo_bar").attr("value") shouldBe "Foo Bar"
      doc.getElementById("groupName-foo_bar").attr("checked") shouldBe "checked"
      doc.getElementById("groupName-bar").attr("value")  shouldBe "bar"

    }
    "return 200, dont pre populate page and drop group elelments from CR when CohoEntered is saved && shareholder name is NOT in the list of shareholders any more (changed shareholder)" in new Setup {
      val expected = Json.parse("""{
                                  |   "groupRelief": true,
                                  |   "nameOfCompany": {
                                  |     "name": "Foo Bar INCORRECT",
                                  |     "nameType" : "CohoEntered"
                                  |   }
                                  |}""".stripMargin).toString
      stubSuccessfulLogin(userId = userId,otherParamsForAuth = Some(Json.obj("externalId" -> "foo")))
      stubFootprint(200, footprintResponse(regId))
      stubKeystore(SessionId, regId)
      stubGet(s"/incorporation-information/shareholders/$txid", 200, listOfShareHoldersFromII)
      stubGet(s"/company-registration/corporation-tax-registration/$regId/groups",200, expected)
      stubPut(s"/company-registration/corporation-tax-registration/$regId/groups",200, """{"groupRelief": true}""")
      stubGet(s"/company-registration/corporation-tax-registration/$regId/corporation-tax-registration", 200, statusResponseFromCR)
      stubGet(s"/company-registration/corporation-tax-registration/$regId/confirmation-references", 200,s"""  {
                                                                                                             |        "acknowledgement-reference" : "ABCD00000000001",
                                                                                                             |        "transaction-id" : "$txid",
                                                                                                             |        "payment-reference" : "PAY_REF-123456789",
                                                                                                             |        "payment-amount" : "12"
                                                                                                             |    }""".stripMargin)
      stubPost(s"/company-registration/corporation-tax-registration/check-list-of-group-names", 200, """["Foo Bar", "bar"]""")

      val fResponse = buildClient(controllers.groups.routes.GroupNameController.show().url).
        withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck")
        .get()
      val doc = Jsoup.parse(await(fResponse.body))

      doc.getElementById("groupName-othername").attr("checked") shouldBe ""
      doc.getElementById("otherName").`val` shouldBe ""
      doc.getElementById("groupName-foo_bar").attr("value") shouldBe "Foo Bar"
      doc.getElementById("groupName-foo_bar").attr("checked") shouldBe ""
      doc.getElementById("groupName-bar").attr("value")  shouldBe "bar"
      doc.getElementById("groupName-bar").attr("checked") shouldBe ""
      intercept[Exception](doc.getElementById("groupName-foo_bar_incorrect").`val`)

      getPUTRequestJsonBody(s"/company-registration/corporation-tax-registration/$regId/groups") shouldBe Json.parse("""{"groupRelief": true}""")
    }
  }
  "GroupAddressGET" should {

    "return 200 and pre populate page with address stored in CR that is ALF" in new Setup {
      stubSuccessfulLogin(userId = userId,otherParamsForAuth = Some(Json.obj("externalId" -> "foo")))
      stubFootprint(200, footprintResponse(regId))
      stubKeystore(SessionId, regId)
      stubGet(s"/company-registration/corporation-tax-registration/$regId/corporation-tax-registration", 200, statusResponseFromCR)

      val jsonToBeParsed = Json.parse("""{
                                        |   "groupRelief": true,
                                        |   "nameOfCompany": {
                                        |     "name": "foo",
                                        |     "nameType" : "Other"
                                        |   },
                                        |   "addressAndType" : {
                                        |     "addressType" : "ALF",
                                        |       "address" : {
                                        |         "line1": "1 abc",
                                        |         "line2" : "2 abc",
                                        |         "line3" : "3 abc",
                                        |         "line4" : "4 abc",
                                        |         "country" : "country A",
                                        |         "postcode" : "ZZ1 1ZZ"
                                        |     }
                                        |   },
                                        |   "groupUTR" : {
                                        |     "UTR" : "1234567890"
                                        |   }
                                        |}""".stripMargin)
      stubGet(s"/company-registration/corporation-tax-registration/$regId/groups",200, jsonToBeParsed.toString)
      val fResponse = buildClient(controllers.groups.routes.GroupAddressController.show().url).
        withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck")
        .get()
      val doc = Jsoup.parse(await(fResponse.body))
      doc.getElementById("groupAddress-alf").`val` shouldBe "ALF"
      val label = await(doc.getElementsByTag("label")
        .filter(e => !e.attr("for","groupAddress-alf").isEmpty)).first()
      label.text shouldBe "1 abc, 2 abc, 3 abc, 4 abc, ZZ1 1ZZ, country A"
    }
  }
  "GroupAddressSubmit" should {

    "call coho to get the latest address for TxAPI and address already exists in DB (ALF Address)" in new Setup {
      stubSuccessfulLogin(userId = userId,otherParamsForAuth = Some(Json.obj("externalId" -> "foo")))
      stubFootprint(200, footprintResponse(regId))
      stubKeystore(SessionId, regId)
      stubGet(s"/company-registration/corporation-tax-registration/$regId/corporation-tax-registration", 200, statusResponseFromCR)
      stubGet(s"/company-registration/corporation-tax-registration/$regId/confirmation-references", 200, s"""  {
                                                                                                            |        "acknowledgement-reference" : "ABCD00000000001",
                                                                                                            |        "transaction-id" : "$txid",
                                                                                                            |        "payment-reference" : "PAY_REF-123456789",
                                                                                                            |        "payment-amount" : "12"
                                                                                                            |    }""".stripMargin)
      val jsonToBeParsed = Json.parse("""{
                                        |   "groupRelief": true,
                                        |   "nameOfCompany": {
                                        |     "name": "big company",
                                        |     "nameType" : "CohoEntered"
                                        |   },
                                        |   "addressAndType" : {
                                        |     "addressType" : "ALF",
                                        |       "address" : {
                                        |         "line1": "1 abc",
                                        |         "line2" : "2 abc",
                                        |         "line3" : "3 abc",
                                        |         "line4" : "4 abc",
                                        |         "country" : "country A",
                                        |         "postcode" : "ZZ1 1ZZ"
                                        |     }
                                        |   },
                                        |   "groupUTR" : {
                                        |     "UTR" : "1234567890"
                                        |   }
                                        |}""".stripMargin)
      val validAddressBack = Json.toJson(NewAddress("foo","bar",None,None,Some("ZZ1 1ZZ"),None,None))(Groups.formatsNewAddressGroups)
      stubGet(s"/company-registration/corporation-tax-registration/$regId/groups",200, jsonToBeParsed.toString)
      stubGet(s"/incorporation-information/shareholders/$txid", 200, listOfShareHoldersFromII)
      stubPost(s"/company-registration/corporation-tax-registration/check-return-business-address", 200,validAddressBack.toString)
      stubPut(s"/company-registration/corporation-tax-registration/$regId/groups",200, """{"groupRelief": true}""")
      val fResponse = buildClient(controllers.groups.routes.GroupAddressController.submit.url)
        .withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck")
        .post(Map("Csrf-Token" -> Seq("nocheck"), "groupAddress" -> Seq("TxAPI")))
      val res = await(fResponse)
      res.status shouldBe 303
      res.header(HeaderNames.LOCATION).get shouldBe controllers.groups.routes.GroupUtrController.show().url

    }
    "redirect to alf if II returns non 2xx on submit" in new Setup {
      stubSuccessfulLogin(userId = userId,otherParamsForAuth = Some(Json.obj("externalId" -> "foo")))
      stubFootprint(200, footprintResponse(regId))
      stubKeystore(SessionId, regId)
      stubGet(s"/company-registration/corporation-tax-registration/$regId/confirmation-references", 200, s"""  {
                                                                                                            |        "acknowledgement-reference" : "ABCD00000000001",
                                                                                                            |        "transaction-id" : "$txid",
                                                                                                            |        "payment-reference" : "PAY_REF-123456789",
                                                                                                            |        "payment-amount" : "12"
                                                                                                            |    }""".stripMargin)
      stubGet(s"/company-registration/corporation-tax-registration/$regId/corporation-tax-registration", 200, statusResponseFromCR)
      val jsonToBeParsed = Json.parse("""{
                                        |   "groupRelief": true,
                                        |   "nameOfCompany": {
                                        |     "name": "big company",
                                        |     "nameType" : "CohoEntered"
                                        |   },
                                        |   "addressAndType" : {
                                        |     "addressType" : "ALF",
                                        |       "address" : {
                                        |         "line1": "1 abc",
                                        |         "line2" : "2 abc",
                                        |         "line3" : "3 abc",
                                        |         "line4" : "4 abc",
                                        |         "country" : "country A",
                                        |         "postcode" : "ZZ1 1ZZ"
                                        |     }
                                        |   },
                                        |   "groupUTR" : {
                                        |     "UTR" : "1234567890"
                                        |   }
                                        |}""".stripMargin)

      stubGet(s"/company-registration/corporation-tax-registration/$regId/groups",200, jsonToBeParsed.toString)


      stubGet(s"/incorporation-information/shareholders/$txid", 400, listOfShareHoldersFromII)
      stubPost("/api/init", 200, "{}", responseHeader = ("Location","foo"))
      val fResponse = buildClient(controllers.groups.routes.GroupAddressController.submit.url)
        .withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck")
        .post(Map("Csrf-Token" -> Seq("nocheck"), "groupAddress" -> Seq("TxAPI")))
      val res = await(fResponse)
      res.status shouldBe 303
      res.header(HeaderNames.LOCATION).get shouldBe "foo"
    }
  }
  "GroupAddressSaveAddressFromALF" should {

    "save address to backend updating existing groups block" in new Setup {
      stubSuccessfulLogin(userId = userId,otherParamsForAuth = Some(Json.obj("externalId" -> "foo")))
      stubFootprint(200, footprintResponse(regId))
      stubKeystore(SessionId, regId)
      stubGet(s"/company-registration/corporation-tax-registration/$regId/corporation-tax-registration", 200, statusResponseFromCR)
      val jsonToBeParsed = Json.parse("""{
                                        |   "groupRelief": true,
                                        |   "nameOfCompany": {
                                        |     "name": "big company",
                                        |     "nameType" : "CohoEntered"
                                        |   },
                                        |   "addressAndType" : {
                                        |     "addressType" : "ALF",
                                        |       "address" : {
                                        |         "line1": "1 abc",
                                        |         "line2" : "2 abc",
                                        |         "line3" : "3 abc",
                                        |         "line4" : "4 abc",
                                        |         "country" : "country A",
                                        |         "postcode" : "ZZ1 1ZZ"
                                        |     }
                                        |   },
                                        |   "groupUTR" : {
                                        |     "UTR" : "1234567890"
                                        |   }
                                        |}""".stripMargin)
      val addressLookupJson = Json.parse(
        """{
          |  "auditRef":"tstAuditRef",
          |  "address":{
          |    "lines":[
          |      "Address Line 1",
          |      "Testford",
          |      "Testley",
          |      "Testshire"
          |    ],
          |    "postcode":"FX1 1ZZ",
          |    "country":{
          |      "code":"UK",
          |      "name":"United Kingdom"
          |    }
          |  }
          |}""".stripMargin)
      stubPut(s"/company-registration/corporation-tax-registration/$regId/groups",200, """{"groupRelief": true}""")
      stubGet(s"/company-registration/corporation-tax-registration/$regId/groups",200, jsonToBeParsed.toString)
      stubFor(get(urlEqualTo("/api/confirmed?id=1"))
        .willReturn(
          aResponse().
            withStatus(200).
            withBody(addressLookupJson.toString)
        )
      )
      val fResponse = buildClient(controllers.groups.routes.GroupAddressController.handbackFromALF().url + """?id=1""").
        withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck")
        .get()

      val res = await(fResponse)
      res.status shouldBe 303
      res.header(HeaderNames.LOCATION).get shouldBe controllers.groups.routes.GroupUtrController.show().url

    }
  }
  "GroupUTRGET" should {
    "return 200 and pre populate page" in new Setup {
      stubSuccessfulLogin(userId = userId,otherParamsForAuth = Some(Json.obj("externalId" -> "foo")))
      stubFootprint(200, footprintResponse(regId))
      stubKeystore(SessionId, regId)
      stubGet(s"/company-registration/corporation-tax-registration/$regId/corporation-tax-registration", 200, statusResponseFromCR)
      val jsonToBeParsed = Json.parse("""{
                                        |   "groupRelief": true,
                                        |   "nameOfCompany": {
                                        |     "name": "big company",
                                        |     "nameType" : "CohoEntered"
                                        |   },
                                        |   "addressAndType" : {
                                        |     "addressType" : "ALF",
                                        |       "address" : {
                                        |         "line1": "1 abc",
                                        |         "line2" : "2 abc",
                                        |         "line3" : "3 abc",
                                        |         "line4" : "4 abc",
                                        |         "country" : "country A",
                                        |         "postcode" : "ZZ1 1ZZ"
                                        |     }
                                        |   },
                                        |   "groupUTR" : {
                                        |     "UTR" : "1234567890"
                                        |   }
                                        |}""".stripMargin)

      stubGet(s"/company-registration/corporation-tax-registration/$regId/groups",200, jsonToBeParsed.toString)
      val fResponse = buildClient(controllers.groups.routes.GroupUtrController.show().url).
        withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck")
        .get()

      val doc = Jsoup.parse(await(fResponse.body))
      doc.getElementById("groupUtr-utr").`val` shouldBe "utr"
      doc.getElementById("utr").`val` shouldBe "1234567890"
      doc.getElementById("groupUtr-utr").attr("checked") shouldBe "checked"
    }
  }
  "GroupUTRSubmit" should {
    "return 303" in new Setup{
      stubSuccessfulLogin(userId = userId,otherParamsForAuth = Some(Json.obj("externalId" -> "foo")))
      stubFootprint(200, footprintResponse(regId))
      stubKeystore(SessionId, regId)
      stubGet(s"/company-registration/corporation-tax-registration/$regId/corporation-tax-registration", 200, statusResponseFromCR)
      val jsonToBeParsed = Json.parse("""{
                                        |   "groupRelief": true,
                                        |   "nameOfCompany": {
                                        |     "name": "big company",
                                        |     "nameType" : "CohoEntered"
                                        |   },
                                        |   "addressAndType" : {
                                        |     "addressType" : "ALF",
                                        |       "address" : {
                                        |         "line1": "1 abc",
                                        |         "line2" : "2 abc",
                                        |         "line3" : "3 abc",
                                        |         "line4" : "4 abc",
                                        |         "country" : "country A",
                                        |         "postcode" : "ZZ1 1ZZ"
                                        |     }
                                        |   },
                                        |   "groupUTR" : {
                                        |     "UTR" : "1234567890"
                                        |   }
                                        |}""".stripMargin)

      stubGet(s"/company-registration/corporation-tax-registration/$regId/groups",200, jsonToBeParsed.toString)
      stubPut(s"/company-registration/corporation-tax-registration/$regId/groups",200, """{"groupRelief": true}""")

      val fResponse = buildClient(controllers.groups.routes.GroupUtrController.submit().url).
        withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck")
        .post(Map("Csrf-Token" -> Seq("nocheck"), "groupUtr" -> Seq("utr"),"utr" -> Seq("123")))

      val res = await(fResponse)
      res.status shouldBe 303
      res.header(HeaderNames.LOCATION).get shouldBe controllers.handoff.routes.GroupController.PSCGroupHandOff().url
    }
  }
}