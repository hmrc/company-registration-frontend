
package www

import java.util.UUID

import config.FrontendAppConfig
import fixtures.HandOffFixtures
import itutil.{FakeAppConfig, IntegrationSpecBase, LoginStub, PayloadExtractor}
import models.handoff.{NavLinks, PSCHandOff}
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.test.FakeApplication
import play.modules.reactivemongo.ReactiveMongoComponent
import repositories.NavModelRepo
import uk.gov.hmrc.mongo.MongoSpecSupport
import utils.JweCommon

import scala.concurrent.ExecutionContext.Implicits.global

class GroupControllerISpec extends IntegrationSpecBase with MongoSpecSupport with LoginStub with FakeAppConfig with HandOffFixtures {

  override implicit lazy val app = FakeApplication(additionalConfiguration = fakeConfig())
  val userId = "test-user-id"
  val regId = "12345"

  class Setup {
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
         |    }
         |}
     """.stripMargin

    val rc = app.injector.instanceOf[ReactiveMongoComponent]
    val repo = new NavModelRepo {
      override val mongo: ReactiveMongoComponent = rc
      override val appConfig: FrontendAppConfig = app.injector.instanceOf[FrontendAppConfig]
    }
    await(repo.repository.drop) shouldBe true
    await(repo.repository.ensureIndexes)
    val jweDecryptor = app.injector.instanceOf[JweCommon]
  }

  s"${controllers.handoff.routes.GroupController.groupHandBack("").url}" should {
    "return 303 when a valid payload is passed in with the shareholders flag and updates NavModel successfully" in new Setup {
      stubSuccessfulLogin(userId = userId)
      stubFootprint(200, footprintResponse)
      stubKeystore(SessionId, regId)
      await(repo.repository.insertNavModel(regId, handOffNavModelDataUpTo3))
      await(repo.repository.count) shouldBe 1
      val csrfToken = UUID.randomUUID().toString
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)

      val fResponse = buildClient(controllers.handoff.routes.GroupController.groupHandBack(H03_1_PAYLOAD_FLAG).url).
        withHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck").
        get()

      val response = await(fResponse)
      await(repo.repository.getNavModel(regId)).get shouldBe handOffNavModelDataUpTo3_1
      response.status shouldBe 303
      response.header(HeaderNames.LOCATION).get shouldBe controllers.handoff.routes.GroupController.PSCGroupHandOff().url
    }
    "return 303 when a valid payload is passed in without flag, does not update nav model, redirects to H04" in new Setup {
      stubSuccessfulLogin(userId = userId)
      stubFootprint(200, footprintResponse)
      stubKeystore(SessionId, regId)
      await(repo.repository.insertNavModel(regId, handOffNavModelDataUpTo3))
      await(repo.repository.count) shouldBe 1
      val csrfToken = UUID.randomUUID().toString
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)

      val fResponse = buildClient(controllers.handoff.routes.GroupController.groupHandBack(H03_1_PAYLOAD_NO_FLAG).url).
        withHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck").
        get()

      val response = await(fResponse)
      await(repo.repository.getNavModel(regId)).get shouldBe handOffNavModelDataUpTo3
      response.status shouldBe 303
      response.header(HeaderNames.LOCATION).get shouldBe controllers.handoff.routes.CorporationTaxSummaryController.corporationTaxSummary(H03_1_PAYLOAD_NO_FLAG).url
    }
    "return 400 when payload contains invalid data" in new Setup {
      stubSuccessfulLogin(userId = userId)
      stubFootprint(200, footprintResponse)
      stubKeystore(SessionId, regId)
      await(repo.repository.insertNavModel(regId, handOffNavModelDataUpTo3))
      await(repo.repository.count) shouldBe 1
      val csrfToken = UUID.randomUUID().toString
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)

      val fResponse = buildClient(controllers.handoff.routes.GroupController.groupHandBack("").url).
        withHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck").
        get()

      val response = await(fResponse)
      await(repo.repository.getNavModel(regId)).get shouldBe handOffNavModelDataUpTo3
      response.status shouldBe 400
    }
  }

  s"${controllers.handoff.routes.GroupController.PSCGroupHandOff().url}" should {
    "Redirect to PSC hand off url from 3-1 entry in nav Model" in new Setup {
      stubSuccessfulLogin(userId = userId,otherParamsForAuth = Some(Json.obj("externalId" -> "foo")))
      stubFootprint(200, footprintResponse)
      stubKeystore(SessionId, regId)
      await(repo.repository.insertNavModel(regId, handOffNavModelDataWithJust3_2Requirements))
      await(repo.repository.count) shouldBe 1
      val csrfToken = UUID.randomUUID().toString
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)
      val fResponse = buildClient(controllers.handoff.routes.GroupController.PSCGroupHandOff().url).
        withHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck")
        .get()

      val response = await(fResponse)
      response.status shouldBe 303
      await(repo.repository.getNavModel(regId)).get shouldBe handOffNavModelDataWithJust3_2Requirements
      val url = response.header(HeaderNames.LOCATION).get

      val payload = PayloadExtractor.extractPayload(url)
      val decryptedPayload = jweDecryptor.decrypt[PSCHandOff](payload)
      val expected = PSCHandOff(
        "foo",
        "12345",
        Json.obj(),
        Some(Json.obj("testCHBagKey" -> "testValue")),
        NavLinks("/forwardToNextHmrcPage","/reverseToPreviousHmrcPage"),
        false)
      decryptedPayload.get shouldBe expected
    }
    "Redirect to post sign in if no nav model exists" in new Setup {
      stubSuccessfulLogin(userId = userId,otherParamsForAuth = Some(Json.obj("externalId" -> "foo")))
      stubFootprint(200, footprintResponse)
      stubKeystore(SessionId, regId)
      await(repo.repository.count) shouldBe 0
      val csrfToken = UUID.randomUUID().toString
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)
      val fResponse = buildClient(controllers.handoff.routes.GroupController.PSCGroupHandOff().url).
        withHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck")
        .get()

      val response = await(fResponse)
      response.status shouldBe 303
      response.header(HeaderNames.LOCATION).get shouldBe controllers.reg.routes.SignInOutController.postSignIn(None, None, None).url
    }
  }
}