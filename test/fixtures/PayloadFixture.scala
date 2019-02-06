/*
 * Copyright 2019 HM Revenue & Customs
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

package fixtures

import models.handoff._
import models.{AccountingDatesHandOffModel, HandBackPayloadModel, _}
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.http.cache.client.CacheMap
import utils.JweCommon

trait PayloadFixture extends AddressFixture with JweFixture {

  lazy val simpleRoAddress = CHROAddress("","",Some(""),"","",Some(""),Some(""),Some(""))
  lazy val testHandBackData =
    CompanyNameHandOffIncoming(
      Some("RegID"),
      "FAKE_OPEN_CONNECT",
      "TestCompanyName",
      simpleRoAddress,
      "testJuri",
      Json.parse("""{"ch" : 1}""").as[JsObject],
      Json.parse("""{"ch" : 1}""").as[JsObject],
      Json.parse("""{"forward":"testForward","reverse":"testReverse"}""").as[JsObject])

  lazy val testHandBackData3 =
    RegistrationConfirmationPayload(
      "HMRC616",
      "testReturnUrl",
      "transactionID",
      Some("BRCT-SC123456"),
      Some("123"),
      Json.obj(),
      Json.obj(),
      Json.obj()
    )

  lazy val emptyHandBackData =
    RegistrationConfirmationPayload("HMRC616","testReturnUrl","", Some(""), Some(""),Json.obj(),Json.obj(),Json.obj())

  lazy val returnCacheMap = CacheMap("", Map("" -> Json.toJson(testHandBackData)))
  lazy val returnCacheMap3 = CacheMap("", Map("" -> Json.toJson(testHandBackData3)))
  lazy val returnEmptyCacheMap = CacheMap("", Map("" -> Json.toJson(emptyHandBackData)))

  lazy val payloadEncrypted = JweWithTestKey.encrypt(Json.toJson[CompanyNameHandOffIncoming](testHandBackData))
  lazy val payloadEncrypted3 = JweWithTestKey.encrypt(Json.toJson(testHandBackData3))
  lazy val emptyPayloadEncrypted = JweWithTestKey.encrypt(Json.toJson(emptyHandBackData))

  lazy val validFirstHandBack = HandBackPayloadModel(
    "testOID",
    "testReturnUrl",
    "testFullName",
    "testEmail",
    "testCompanyName",
    chROAddress
  )

  val basicRoAddress = CHROAddress("1","1",None,"1","1",None,None,None)
  lazy val validCompanyNameHandBack
    = CompanyNameHandOffIncoming(
    Some("testID"),
    "FAKE_OPEN_CONNECT",
    "TestCompanyName",
    basicRoAddress,
    "testJurisdiction",
    Json.parse("""{"registrationId":"12345"}""").as[JsObject],
    Json.parse("""{"registrationId":"12345"}""").as[JsObject],
    Json.parse("""{"forward":"testForward","reverse":"testReverse"}""").as[JsObject]
  )

  lazy val handBackFormData = Seq(
    "journey_id" ->"testID",
    "user_id" ->"testID",
    "company_name" ->"testCompanyName",
    "registered_office_address.premises" -> "premises",
    "registered_office_address.address_line_1" -> "line1",
    "registered_office_address.address_line_2" -> "line2",
    "registered_office_address.country" -> "country",
    "registered_office_address.locality" -> "locality",
    "registered_office_address.po_box" -> "POBox",
    "registered_office_address.postal_code" -> "POCode",
    "registered_office_address.region" -> "region",
    "jurisdiction" -> "testJurisdiction",
    "ch" -> "ch",
    "hmrc" -> "hmrc")

  lazy val validBusinessActivitiesPayload =
    BusinessActivitiesModel(
      "openConnect",
      "testRegID",
      Some(HandoffPPOB(
        "number L1",
        "L2",
        Some("L3"),
        Some("L4"),
        Some("POCode"),
        Some("Country"))
      ),
      Some(JsObject(Seq())),
      JsObject(Seq()),
      NavLinks("testLink", "testReverseLink")
  )

  lazy val validAccountingDatesHandOffModelWithAddress = AccountingDatesHandOffModel(
    "testOID",
    "testReturnUrl",
    Some(validAddressWithHouseName)
  )

  lazy val validAccountingDatesHandOffModelWithNoAddress = AccountingDatesHandOffModel(
    "testOID",
    "testReturnUrl",
    None
  )

  val confirmationHandoffPayload = RegistrationConfirmationPayload(
    user_id = "1",
    journey_id = "123",
    transaction_id = "fake-t",
    payment_reference = None,
    payment_amount = None,
    ch = Json.obj(),
    hmrc = Json.obj(),
    links = Json.obj("forward" -> "/redirect-url")
  )

  val registrationConfirmationPayload = confirmationHandoffPayload.copy(payment_amount = Some("50.00"),payment_reference = Some("fake-reference"), links = Json.obj())

  lazy val firstHandBackEncrypted = (jwe: JweCommon) =>  jwe.encrypt[HandBackPayloadModel](validFirstHandBack)

  lazy val secondHandOffWithAddressJson = """{"OID":"testOID","return_url":"testReturnUrl","address":{"houseNameNumber":"testHouseNumber","street":"testStreet","area":"testArea","postTown":"testPostTown","region":"testRegion","country":"testCountry","postCode":"FX1 1ZZ"}}"""
  lazy val secondHandOffWithNoAddressJson = """{"OID":"testOID","return_url":"testReturnUrl"}"""

  lazy val summaryHandOffModelPayload = SummaryHandOff("testUserID","testJourneyID",Json.obj("hmrc" -> "some hmrc data"),Some(Json.obj("ch" -> "some ch data")),Json.obj("links" -> "some links"))

  lazy val summaryEncryptedPayload = (jwe: JweCommon) =>  jwe.encrypt[SummaryHandOff](summaryHandOffModelPayload).get

  lazy val summaryHandOffJson = """{"user_id":"testUserID","journey_id":"testJourneyID","hmrc":{"hmrc":"some hmrc data"},"ch":{"ch":"some ch data"},"links":{"links":"some links"}}"""

  lazy val validEncryptedPayload = """eyJhbGciOiJkaXIiLCJlbmMiOiJBMTI4Q0JDLUhTMjU2In0..PPxL3WE_9tU4L5dXO9w0YQ.SOZ66zJRwLLaqElHCo6fBczwKMDfhj__XKaDu3qlcJIrXQiGSySW28IPNdJ1fkrvoaMwVZCihe4wkDgnqetP3zcCHIYx0iaAwiEtTvAPozZkMYI8wonlH3-JEC1MN08mkR3rbw546sXBACGtwHh5OfvQCnUHIsVbpsASU6OGDVXeYsMqmczElmCAOuYnxNwr.wDdM5GD0dc7plwpq6Jfnkw"""

  lazy val validEncryptedCompanyNameHandOff = (jwe: JweCommon) =>  jwe.encrypt[CompanyNameHandOffIncoming](validCompanyNameHandBack)

  lazy val validEncryptedBusinessActivities = (jwe: JweCommon) =>  jwe.encrypt[BusinessActivitiesModel](validBusinessActivitiesPayload).get

  lazy val confirmationPayload = (jwe: JweCommon) =>  jwe.encrypt[RegistrationConfirmationPayload](RegistrationConfirmationPayload("user","journey","transaction",Some("ref"),Some("amount"), Json.obj(), Json.obj(), Json.obj())).get
}