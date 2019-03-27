
package www

import java.util.UUID

import config.FrontendAppConfig
import fixtures.HandOffFixtures
import itutil.{FakeAppConfig, IntegrationSpecBase, LoginStub, PayloadExtractor}
import models.{CHROAddress, CompanyDetails, PPOB}
import models.handoff.{CompanyNameHandOffIncoming, NavLinks, PSCHandOff}
import org.mockito.Matchers
import org.mockito.Mockito._
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.test.FakeApplication
import play.modules.reactivemongo.ReactiveMongoComponent
import repositories.NavModelRepo
import uk.gov.hmrc.http.HeaderCarrier
import utils.JweCommon

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CorporationTaxDetailsControllerISpec extends IntegrationSpecBase with LoginStub with FakeAppConfig with HandOffFixtures {

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

    val companyDetails = Json.toJson(CompanyDetails("CompanyName",CHROAddress("premises","line1",Some("line2"),"locality","UK",None,None,None),PPOB("",None),"ENGLAND_AND_WALES")).toString


    val repo = new NavModelRepo {
      override val mongo: ReactiveMongoComponent = app.injector.instanceOf[ReactiveMongoComponent]
      override val appConfig: FrontendAppConfig = app.injector.instanceOf[FrontendAppConfig]
    }
    await(repo.repository.drop)
    await(repo.repository.count) shouldBe 0
    await(repo.repository.ensureIndexes)
    val jweDecryptor = app.injector.instanceOf[JweCommon]
  }

  s"${controllers.handoff.routes.CorporationTaxDetailsController.corporationTaxDetails("").url}" should {
    "return 303 when a valid payload is passed in updates NavModel successfully" in new Setup {
      stubSuccessfulLogin(userId = userId)
      stubFootprint(200, footprintResponse)
      stubKeystore(SessionId, regId)
      await(repo.repository.insertNavModel(regId, handOffNavModelDataUpTo1))
      await(repo.repository.count) shouldBe 1
      val csrfToken = UUID.randomUUID().toString
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)
      stubRetrieveCRCompanyDetails(regId,200,companyDetails)
      stubUpdateCRCompanyDetails(regId,200,companyDetails)


      await(repo.repository.getNavModel(regId)).get shouldBe handOffNavModelDataUpTo1
      val fResponse = buildClient(controllers.handoff.routes.CorporationTaxDetailsController.corporationTaxDetails(HO2_PAYLOAD).url).
      withHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck").
      get()

      val response = await(fResponse)
      response.status shouldBe 303
      response.header(HeaderNames.LOCATION).get shouldBe controllers.reg.routes.PPOBController.show().url
    }

      "return 400 when payload contains invalid data" in new Setup {
        stubSuccessfulLogin(userId = userId)
        stubFootprint(200, footprintResponse)
        stubKeystore(SessionId, regId)
        await(repo.repository.insertNavModel(regId, handOffNavModelDataUpTo1))
        await(repo.repository.count) shouldBe 1
        val csrfToken = UUID.randomUUID().toString
        val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)
        stubRetrieveCRCompanyDetails(regId,200,companyDetails)
        stubUpdateCRCompanyDetails(regId,200,companyDetails)


        await(repo.repository.getNavModel(regId)).get shouldBe handOffNavModelDataUpTo1
        val fResponse = buildClient(controllers.handoff.routes.CorporationTaxDetailsController.corporationTaxDetails("").url).
          withHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck").
          get()

        val response = await(fResponse)
        response.status shouldBe 400
      }
  }
}