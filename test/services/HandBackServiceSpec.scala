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

package services

import config.FrontendAppConfig
import fixtures.{CompanyDetailsFixture, PayloadFixture, SubmissionFixture}
import helpers.SCRSSpec
import models.handoff._
import models.{CompanyDetails, RegistrationConfirmationPayload}
import org.mockito.Mockito._
import org.mockito.{ArgumentCaptor, Matchers}
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.binders.ContinueUrl
import utils._

import scala.concurrent.Future
import scala.util.{Failure, Success}

class HandBackServiceSpec extends SCRSSpec with PayloadFixture with CompanyDetailsFixture
  with SubmissionFixture with SCRSExceptions {

  val testJwe = new JweCommon {
    override val key = "Fak3-t0K3n-f0r-pUBLic-r3p0SiT0rY"
  }

  trait Setup {
    val service = new HandBackService {
      override val compRegConnector = mockCompanyRegistrationConnector
      override val keystoreConnector = mockKeystoreConnector
      override val s4LConnector = mockS4LConnector
      override val navModelMongo = mockNavModelRepo
      override val jwe = testJwe
      val continueUrl = mock[ContinueUrl]
      override val scrsFeatureSwitches: SCRSFeatureSwitches = mockSCRSFeatureSwitches
      override val appConfig: FrontendAppConfig = mockAppConfig
    }
  }

  def mockNavRepoGet(regId: String, navModel: HandOffNavModel) =
    when(mockNavModelRepo.getNavModel(Matchers.eq(regId)))
      .thenReturn(Future.successful(Some(navModel)))

  def mockNavRepoInsert(regId: String, navModel: HandOffNavModel) =
    when(mockNavModelRepo.insertNavModel(Matchers.eq(regId), Matchers.any[HandOffNavModel]))
      .thenReturn(Future.successful(Some(navModel)))

  def mockCrRetrieve(regId: String, details: Option[CompanyDetails]) =
    when(mockCompanyRegistrationConnector.retrieveCompanyDetails(Matchers.eq(regId))(Matchers.any[HeaderCarrier])).
      thenReturn(Future.successful(details))

  def mockCrUpdate(regId: String, details: CompanyDetails) =
    when(mockCompanyRegistrationConnector.updateCompanyDetails(Matchers.eq(regId), Matchers.any[CompanyDetails])(Matchers.any[HeaderCarrier]))
      .thenReturn(Future.successful(details))

  def simpleRequest(user: String = "xxx",
                    regId: String = "12345",
                    forward: String = "/testForward",
                    reverse: String = "/testReverse") = Json.obj(
    "user_id" -> user,
    "journey_id" -> regId,
    "hmrc" -> Json.obj(),
    "ch" -> Json.obj("a"->1),
    "links" -> Json.obj("forward"->forward, "reverse"->reverse)
  )

  val registrationID = "12345"
  val cacheMap = CacheMap("", Map("" -> Json.toJson("12345")))

  val testNavModel = HandOffNavModel(
    Sender(Map(
      "1"   -> NavLinks("returnFromCoho", "aboutYOu"),
      "3"   -> NavLinks("groupsHandback", "regularPayments"),
      "3-2" -> NavLinks("summary", "regularPayments"),
      "5"   -> NavLinks("confirmation", "summary"),
      "5-2" -> NavLinks("confirmation",""))),
    Receiver(Map(
      "0"   -> NavLinks("firstHandOff", "/ho1"),
      "1"   -> NavLinks("SIC codes", "/ho3")
    )))

  "updateCompanyDetails" should {
    "update Company Details" in new Setup {

      mockCrRetrieve(registrationID, Some(ho2CompanyDetailsResponse))
      mockCrUpdate(registrationID, ho2UpdatedRequest)

      await(service.updateCompanyDetails(registrationID, validCompanyNameHandOffIncoming)) shouldBe ho2UpdatedRequest

      val captor = ArgumentCaptor.forClass(classOf[CompanyDetails])
      val hcCaptor = ArgumentCaptor.forClass(classOf[HeaderCarrier])

      verify(mockCompanyRegistrationConnector).updateCompanyDetails(Matchers.eq(registrationID), captor.capture())(hcCaptor.capture())

      captor.getValue shouldBe ho2UpdatedRequest
    }
  }

  "processCompanyDetailsHandOff" should {
    def companyDetails(name: String) = Json.obj(
      "company_name" -> name,
      "registered_office_address" -> Json.obj(
        "premises" -> "testPremises",
        "address_line_1" -> "testAddressLine1",
        "address_line_2" -> "testAddressLine2",
        "locality" -> "testLocality",
        "postal_code" -> "testPostcode",
        "country" -> "testCountry"
      ),
      "jurisdiction" -> "testJurisdiction",
      "transaction_id" -> "testTxId"
    )

    def companyDetailsLinks(forward: String = "/testForward",
                            reverse: String = "/testReverse",
                            name: String = "/company-name",
                            address: String = "/address",
                            jurisdiction: String = "/jurisdiction") = Json.obj("links" -> Json.obj(
      "forward" -> forward,
      "reverse" -> reverse,
      "company_name" -> name,
      "company_address" -> address,
      "company_jurisdiction" -> jurisdiction
    ))

    "return a DecryptionError if the encrypted payload is empty" in new Setup {
      await(service.processCompanyDetailsHandBack("")) shouldBe Failure(DecryptionError)
    }

    "return a PayloadError if the decrypted payload is empty" in new Setup {
      val payload = testJwe.encrypt[String]("")
      payload shouldBe defined
      await(service.processCompanyDetailsHandBack(payload.get)) shouldBe Failure(PayloadError)
    }

    "Decrypt and store the CH payload" in new Setup {

      val returnCacheMap = CacheMap("", Map("" -> Json.toJson(testNavModel)))
      val testRegId = "12345"
      mockKeystoreFetchAndGet("registrationID", Some(testRegId))
      mockKeystoreFetchAndGet("HandOffNavigation", Some(testNavModel))

      mockCrRetrieve(testRegId, None)
      mockCrUpdate(testRegId, ho2UpdatedRequest)

      mockNavRepoGet(testRegId, testNavModel)
      mockNavRepoInsert(testRegId, testNavModel)


      val name = "Foo Name"
      val r = simpleRequest() ++ companyDetails(name) ++ companyDetailsLinks()
      val encryptedRequest = testJwe.encrypt[JsValue](r)
      when(mockCompanyRegistrationConnector.saveTXIDAfterHO2(testRegId,"testTxId"))
        .thenReturn(Future.successful(Some(HttpResponse(200))))

      encryptedRequest shouldBe defined

      val result = await(service.processCompanyDetailsHandBack(encryptedRequest.get))
      result shouldBe Success(Json.fromJson[CompanyNameHandOffIncoming](r).get)

      val captor = ArgumentCaptor.forClass(classOf[CompanyDetails])
      val hcCaptor = ArgumentCaptor.forClass(classOf[HeaderCarrier])

      verify(mockCompanyRegistrationConnector).updateCompanyDetails(Matchers.eq(registrationID), captor.capture())(hcCaptor.capture())

      val details = captor.getValue
      details.companyName shouldBe name
    }

    Map(
      "Forward URL" -> companyDetailsLinks(forward = "//foo.com/bar"),
      "Reverse URL" -> companyDetailsLinks(reverse = "//foo.com/bar"),
      "Name URL" -> companyDetailsLinks(name = "//foo.com/bar"),
      "Address URL" -> companyDetailsLinks(address = "//foo.com/bar"),
      "Jurisdiction URL" -> companyDetailsLinks(jurisdiction = "//foo.com/bar")
    ).foreach{
      case (description, links) =>
        s"Fail if the ${description} is invalid" in new Setup {

          val returnCacheMap = CacheMap("", Map("" -> Json.toJson(testNavModel)))

          mockKeystoreFetchAndGet("registrationID", Some("12345"))
          mockNavRepoGet("12345", testNavModel)

          val r = simpleRequest() ++ companyDetails("Url Name") ++ links
          val encryptedRequest = testJwe.encrypt[JsValue](r)

          encryptedRequest shouldBe defined

          intercept[IllegalArgumentException] {
            await(service.processCompanyDetailsHandBack(encryptedRequest.get))
          }
        }
    }
  }

  "summary Page 1 hand back" should {
    "return a DecryptionError if the encrypted payload is empty" in new Setup {
      await(service.processSummaryPage1HandBack("")) shouldBe Failure(DecryptionError)
    }

    "return a PayloadError if the decrypted payload is empty" in new Setup {
      val payload = testJwe.encrypt[String]("")
      payload shouldBe defined
      await(service.processSummaryPage1HandBack(payload.get)) shouldBe Failure(PayloadError)
    }

    "Decrypt and store the CH payload" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))

      mockNavRepoGet("12345", testNavModel)
      mockNavRepoInsert("12345", testNavModel)

      val r = simpleRequest()
      val encryptedRequest = testJwe.encrypt[JsValue](r)

      encryptedRequest shouldBe defined

      await(service.processSummaryPage1HandBack(encryptedRequest.get)) shouldBe Success(Json.fromJson[SummaryPage1HandOffIncoming](r).get)
    }

    Map(
      "Forward URL" -> simpleRequest(forward = "//foo.com/bar"),
      "Reverse URL" -> simpleRequest(reverse = "//foo.com/bar")
    ).foreach {
      case (description, request) =>
        s"fail if the ${description} is invalid" in new Setup {
          val returnCacheMap = CacheMap("", Map("" -> Json.toJson(testNavModel)))

          mockKeystoreFetchAndGet("registrationID", Some("12345"))
          mockNavRepoGet("12345", testNavModel)

          val encryptedRequest = testJwe.encrypt[JsValue](request)

          encryptedRequest shouldBe defined

          intercept[IllegalArgumentException] {
            await(service.processSummaryPage1HandBack(encryptedRequest.get))
          }
        }
    }
  }
  "processGroupsHandBack" should {
  "return an error if the decryption payload is empty" in new Setup {
    await(service.processGroupsHandBack("")) shouldBe Failure(DecryptionError)
  }

   "return a PayloadError if the decrypted payload is empty" in new Setup {
     val payload = testJwe.encrypt[String]("")
     payload shouldBe defined
     await(service.processGroupsHandBack(payload.get)) shouldBe Failure(PayloadError)
   }

    "return groupHandOffModel when corporate shareholders flag is not present" in new Setup {
      val groupModel = GroupHandBackModel(
        "user",
        "journeyid",
        Json.obj("ch"->"value"),
        Json.obj("hmrc"->"value"),
        NavLinks("f", "r"),
        None
      )
      val encryptedGroupModel = testJwe.encrypt[GroupHandBackModel](groupModel)
      await(service.processGroupsHandBack(encryptedGroupModel.get)) shouldBe Success(groupModel)
    }

    "return groupHandOffModel when corporate shareholders flag is present" in new Setup {
      val groupModel = GroupHandBackModel(
        "user",
        "journeyid",
        Json.obj("ch"->"value"),
        Json.obj("hmrc"->"value"),
        NavLinks("/testForward", "/testReverse"),
        Some(true)
      )
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      mockNavRepoGet("12345", testNavModel)
      mockNavRepoInsert("12345", testNavModel)

      val encryptedGroupModel = testJwe.encrypt[GroupHandBackModel](groupModel)
      await(service.processGroupsHandBack(encryptedGroupModel.get)) shouldBe Success(groupModel)
    }
    "throw IllegalArgumentException if one of the navLinks is invalid" in new Setup {
      val groupModel = GroupHandBackModel(
        "user",
        "journeyid",
        Json.obj("ch"->"value"),
        Json.obj("hmrc"->"value"),
        NavLinks("INVALIDXXX", "/testReverse"),
        Some(true)
      )
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      mockNavRepoGet("12345", testNavModel)
      val encryptedGroupModel = testJwe.encrypt[GroupHandBackModel](groupModel)
      intercept[IllegalArgumentException](await(service.processGroupsHandBack(encryptedGroupModel.get)))
    }
    "return Failure when NavModel Cache fails returning None" in new Setup {
      val groupModel = GroupHandBackModel(
        "user",
        "journeyid",
        Json.obj("ch"->"value"),
        Json.obj("hmrc"->"value"),
        NavLinks("/forward", "/testReverse"),
        Some(true)
      )
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      mockNavRepoGet("12345", testNavModel)
      when(mockNavModelRepo.insertNavModel(Matchers.any(), Matchers.any[HandOffNavModel]))
        .thenReturn(Future.successful(None))
      val encryptedGroupModel = testJwe.encrypt[GroupHandBackModel](groupModel)
      await(service.processGroupsHandBack(encryptedGroupModel.get)).isFailure shouldBe true
    }

    "return an exception when fetchNavModel returns an exception" in new Setup {
        val groupModel = GroupHandBackModel(
        "user",
        "journeyid",
        Json.obj("ch"->"value"),
        Json.obj("hmrc"->"value"),
        NavLinks("/forward", "/testReverse"),
        Some(true)
        )
        mockKeystoreFetchAndGet("registrationID", Some("12345"))
      when(mockNavModelRepo.getNavModel(Matchers.any()))
        .thenReturn(Future.failed(new Exception("")))
      val encryptedGroupModel = testJwe.encrypt[GroupHandBackModel](groupModel)
      intercept[Exception](await(service.processGroupsHandBack(encryptedGroupModel.get)))
    }
  }

  "processCompanyNameReverseHandOff" should {
    "return a successful response" in new Setup {
      val payload = simpleRequest(user = "foo", regId = "54321")

      val encryptedPayload = testJwe.encrypt[JsValue](payload).get

      val result = await(service.processCompanyNameReverseHandBack(encryptedPayload))

      result shouldBe Success(payload)
    }
  }

  "decryptConfirmationHandback" should {

    "return a RegistrationConfirmationPayload with no link and a payment reference and amount if it is an old HO6" in new Setup {
      val encryptedPayloadString = testJwe.encrypt[RegistrationConfirmationPayload](registrationConfirmationPayload).get

      val result = await(service.decryptConfirmationHandback(encryptedPayloadString)(hc)).get
      result shouldBe registrationConfirmationPayload
      result.links shouldBe Json.obj()
      result.payment_reference.isDefined shouldBe true
      result.payment_amount.isDefined shouldBe true
    }

    "return a RegistrationConfirmationPayload with a link and no payment reference and amount if it is a HO5.1" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      mockNavRepoGet("12345",testNavModel)
      mockNavRepoInsert("12345",testNavModel)

      val encryptedPayloadString = testJwe.encrypt[RegistrationConfirmationPayload](confirmationHandoffPayload).get

      val result = await(service.decryptConfirmationHandback(encryptedPayloadString)(hc)).get
      result shouldBe confirmationHandoffPayload
      result.links shouldBe Json.obj("forward" -> "/redirect-url")
      result.payment_reference.isDefined shouldBe false
      result.payment_amount.isDefined shouldBe false
    }
  }

  "getNextUrl" should {
    "return an optional string if a next url is present in the payload" in new Setup {
      await(service.getForwardUrl(confirmationHandoffPayload)) shouldBe Some("/redirect-url")
    }
    "return None if a next url is not present in the payload" in new Setup {
      await(service.getForwardUrl(registrationConfirmationPayload)) shouldBe None
    }
  }

  "payloadHasForwardLinkAndNoPaymentRefs" should {
    "return true if there is a forward link and no payment refernce and amount" in new Setup {
      await(service.payloadHasForwardLinkAndNoPaymentRefs(confirmationHandoffPayload)) shouldBe true
    }
    "return false if there is no forward link and a payment refernce and amount" in new Setup {
      await(service.payloadHasForwardLinkAndNoPaymentRefs(registrationConfirmationPayload)) shouldBe false
    }
  }
}