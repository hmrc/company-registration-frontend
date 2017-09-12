/*
 * Copyright 2017 HM Revenue & Customs
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

package services

import builders.AuthBuilder
import fixtures.{CompanyDetailsFixture, PayloadFixture, SubmissionFixture}
import helpers.SCRSSpec
import mocks.{KeystoreMock, navModelRepoMock}
import models.{CHROAddress, CompanyDetails}
import models.handoff._
import org.mockito.{ArgumentCaptor, Matchers}
import org.mockito.Mockito._
import play.api.libs.json.{JsObject, JsValue, Json}
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.http.HeaderCarrier
import utils.{DecryptionError, Jwe, PayloadError, SCRSExceptions}

import scala.concurrent.Future
import scala.util.{Failure, Success}

class HandBackServiceSpec extends SCRSSpec with PayloadFixture with CompanyDetailsFixture
  with SubmissionFixture with SCRSExceptions with KeystoreMock with navModelRepoMock {
  val mockNavModelRepoObj = mockNavModelRepo
  trait Setup {
    val service = new HandBackService {
      override val compRegConnector = mockCompanyRegistrationConnector
      override val keystoreConnector = mockKeystoreConnector
      override val s4LConnector = mockS4LConnector
      override val navModelMongo = mockNavModelRepoObj

    }
  }

  implicit val user = AuthBuilder.createTestUser

  val registrationID = "12345"
  val cacheMap = CacheMap("", Map("" -> Json.toJson("12345")))

  "CompanyNameHandoffIncoming" should {
    "be able to construct the expected Json" in  {

      val json =
        """
          |{"journey_id":"xxx",
          |"user_id":"xxx",
          |"company_name":"name",
          |"registered_office_address":{
          |"premises":"x",
          |"address_line_1":"x",
          |"address_line_2":"x",
          |"locality":"x",
          |"country":"x",
          |"po_box":"x",
          |"postal_code":"x",
          |"region":"x"},
          |"jurisdiction":"x",
          |"ch":{"a":1},
          |"hmrc":{},
          |"links":{
          | "forward":"testForward",
          | "reverse":"testReverse"
          |}
          |}
        """.stripMargin

      val x =
        CompanyNameHandOffIncoming(
          Some("xxx"),
          "xxx",
          "name",
          CHROAddress(
            "x",
            "x",
            Some("x"),
            "x",
            "x",
            Some("x"),
            Some("x"),
            Some("x")
          ),
          "x",
          Json.parse("""{"a":1}""").as[JsObject],
          Json.parse("{}").as[JsObject],
          Json.parse("""{"forward":"testForward","reverse":"testReverse"}""").as[JsObject])

      CompanyNameHandOffIncoming.format.writes(x) shouldBe Json.parse(json)


    }
  }

  "updateCompanyDetails" should {
    "update Company Details" in new Setup {
      when(mockCompanyRegistrationConnector.retrieveCompanyDetails(Matchers.eq(registrationID))(Matchers.any[HeaderCarrier])).thenReturn(Future.successful(Some(ho2CompanyDetailsResponse)))

      when(mockCompanyRegistrationConnector.updateCompanyDetails(Matchers.eq(registrationID), Matchers.any[CompanyDetails])(Matchers.any[HeaderCarrier]))
        .thenReturn(Future.successful(ho2UpdatedRequest))

      await(service.updateCompanyDetails(registrationID, validCompanyNameHandOffIncoming)) shouldBe ho2UpdatedRequest

      val captor = ArgumentCaptor.forClass(classOf[CompanyDetails])
      val hcCaptor = ArgumentCaptor.forClass(classOf[HeaderCarrier])

      verify(mockCompanyRegistrationConnector).updateCompanyDetails(Matchers.eq(registrationID), captor.capture())(hcCaptor.capture())

      captor.getValue shouldBe ho2UpdatedRequest

    }
  }

  "processCompanyDetailsHandOff" should {
    "return a DecryptionError if the encrypted payload is empty" in new Setup {
      await(service.processCompanyDetailsHandBack("")) shouldBe Failure(DecryptionError)
    }

    "return a PayloadError if the decrypted payload is empty" in new Setup {

      val payload = Jwe.encrypt[String]("")
      payload shouldBe defined
      await(service.processCompanyDetailsHandBack(payload.get)) shouldBe Failure(PayloadError)
    }

    "Decrypt and store the CH payload" in new Setup {

      val testNavModel = HandOffNavModel(
        Sender(Map(
          "1" -> NavLinks("returnFromCoho", "aboutYOu"),
          "3" -> NavLinks("summary", "regularPayments"),
          "5" -> NavLinks("confirmation", "summary"))),
        Receiver(Map(
          "0" -> NavLinks("firstHandOff", ""),
          "1" -> NavLinks("SIC codes", "firstHandoff")
        )))

      val returnCacheMap = CacheMap("", Map("" -> Json.toJson(testNavModel)))

      mockKeystoreFetchAndGet("registrationID", Some("12345"))

      when(mockKeystoreConnector.fetchAndGet[HandOffNavModel](Matchers.eq("HandOffNavigation"))(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(testNavModel)))

      when(mockKeystoreConnector.cache[HandOffNavModel](Matchers.eq(""), Matchers.eq(testNavModel))(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(returnCacheMap))

      when(mockCompanyRegistrationConnector.retrieveCompanyDetails(Matchers.eq("12345"))(Matchers.any()))
        .thenReturn(Future.successful(None))

      when(mockCompanyRegistrationConnector.updateCompanyDetails(Matchers.eq("12345"), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(ho2UpdatedRequest))

      val request = Json.parse(
        """
          | {
          |   "journey_id" : "12345",
          |   "user_id" : "xxx",
          |   "company_name" : "xxx",
          |   "registered_office_address" : {
          |      "premises" : "testPremises",
          |      "address_line_1" : "testAddressLine1",
          |      "address_line_2" : "testAddressLine2",
          |      "locality" : "testLocality",
          |      "postal_code" : "testPostcode",
          |      "country" : "testCountry"
          |   },
          |   "jurisdiction": "testJurisdiction",
          |   "hmrc" : {},
          |   "ch" : { "a" : 1 },
          |   "links":{"forward":"testForward","reverse":"testReverse"}
          | }
        """.stripMargin)

      val encryptedRequest = Jwe.encrypt[JsValue](request)

      encryptedRequest shouldBe defined

      await(service.processCompanyDetailsHandBack(encryptedRequest.get)) shouldBe Success(Json.fromJson[CompanyNameHandOffIncoming](request).get)
    }
  }

  "summary Page 1 hand back" should {

    "return a DecryptionError if the encrypted payload is empty" in new Setup {
      await(service.processSummaryPage1HandBack("")) shouldBe Failure(DecryptionError)
    }

    "return a PayloadError if the decrypted payload is empty" in new Setup {

      val payload = Jwe.encrypt[String]("")
      payload shouldBe defined
      await(service.processSummaryPage1HandBack(payload.get)) shouldBe Failure(PayloadError)
    }

    "Decrypt and store the CH payload" in new Setup {

      val testNavModel = HandOffNavModel(
        Sender(Map(
          "1" -> NavLinks("returnFromCoho", "aboutYOu"),
          "3" -> NavLinks("summary", "regularPayments"),
          "5" -> NavLinks("confirmation", "summary"))),
        Receiver(Map(
          "0" -> NavLinks("firstHandOff", ""),
          "1" -> NavLinks("SIC codes", "firstHandoff")
        )))

      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      mockKeystoreFetchAndGet[HandOffNavModel]("HandOffNavigation", Some(testNavModel))

      val request = Json.parse(
        """
          |{
          |"user_id":"xxx",
          |"journey_id":"12345",
          |"hmrc":{},
          |"ch":{"a":1},
          |"links":{"forward":"test","reverse":"test2"}
          |}
        """.stripMargin)

      val encryptedRequest = Jwe.encrypt[JsValue](request)

      encryptedRequest shouldBe defined

      await(service.processSummaryPage1HandBack(encryptedRequest.get)) shouldBe Success(Json.fromJson[SummaryPage1HandOffIncoming](request).get)
    }
  }

  "processCompanyNameReverseHandOff" should {
    "return a successful response" in new Setup {
      val payload = Json.obj(
        "user_id" -> Json.toJson("testUserID"),
        "journey_id" -> Json.toJson("testJourneyID"),
        "hmrc" -> Json.obj(),
        "ch" -> Json.obj(),
        "links" -> Json.obj()
      )

      val encryptedPayload = Jwe.encrypt[JsValue](payload).get

      val result = await(service.processCompanyNameReverseHandBack(encryptedPayload))

      result shouldBe Success(payload)
    }
  }
}
