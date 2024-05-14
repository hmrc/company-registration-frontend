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

package test.www

import test.fixtures.HandOffFixtures
import test.itutil.{IntegrationSpecBase, LoginStub, MongoHelper}
import models.{CHROAddress, CompanyDetails, PPOB}
import play.api.http.HeaderNames
import play.api.libs.crypto.DefaultCookieSigner
import play.api.libs.json.Json
import repositories.NavModelRepoImpl
import utils.JweCommon

import java.util.UUID

class CorporationTaxDetailsControllerISpec extends IntegrationSpecBase with LoginStub with HandOffFixtures with MongoHelper {

  val userId = "test-user-id"
  val regId = "12345"

  lazy val defaultCookieSigner: DefaultCookieSigner = app.injector.instanceOf[DefaultCookieSigner]

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
  val statusResponseFromCR =
    s"""
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

  val companyDetails = Json.toJson(CompanyDetails("CompanyName", CHROAddress("premises", "line1", Some("line2"), "locality", "UK", None, None, None), PPOB("", None), "ENGLAND_AND_WALES")).toString

  val repo = app.injector.instanceOf[NavModelRepoImpl].repository

  val jweDecryptor = app.injector.instanceOf[JweCommon]

  s"${controllers.handoff.routes.CorporationTaxDetailsController.corporationTaxDetails("").url}" should {
    "return 303 when a valid payload is passed in updates NavModel successfully" in {
      stubSuccessfulLogin(userId = userId)
      stubFootprint(200, footprintResponse)
      stubKeystore(SessionId, regId)
      await(repo.drop)
      await(repo.insertNavModel(regId, handOffNavModelDataUpTo1))
      await(repo.count) mustBe 1
      val csrfToken = UUID.randomUUID().toString
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)
      stubRetrieveCRCompanyDetails(regId, 200, companyDetails)
      stubUpdateCRCompanyDetails(regId, 200, companyDetails)
      stubHandOffReference(regId, 200)

      await(repo.getNavModel(regId)).get mustBe handOffNavModelDataUpTo1
      val fResponse = buildClient(controllers.handoff.routes.CorporationTaxDetailsController.corporationTaxDetails(HO2_PAYLOAD).url)
        .withHttpHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck")
        .get()

      val response = await(fResponse)
      response.status mustBe 303
      response.header(HeaderNames.LOCATION).get mustBe controllers.reg.routes.PPOBController.show.url
    }

    "return 400 when payload contains invalid data" in {
      stubSuccessfulLogin(userId = userId)
      stubFootprint(200, footprintResponse)
      stubKeystore(SessionId, regId)
      await(repo.drop)
      await(repo.insertNavModel(regId, handOffNavModelDataUpTo1))
      await(repo.count) mustBe 1
      val csrfToken = UUID.randomUUID().toString
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)
      stubRetrieveCRCompanyDetails(regId, 200, companyDetails)
      stubUpdateCRCompanyDetails(regId, 200, companyDetails)
      stubHandOffReference(regId, 200)

      await(repo.getNavModel(regId)).get mustBe handOffNavModelDataUpTo1
      val fResponse = buildClient(controllers.handoff.routes.CorporationTaxDetailsController.corporationTaxDetails("").url)
        .withHttpHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck")
        .get()

      val response = await(fResponse)
      response.status mustBe 400
    }
  }
}