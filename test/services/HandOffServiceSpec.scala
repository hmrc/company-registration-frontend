/*
 * Copyright 2023 HM Revenue & Customs
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
import config.{AppConfig, LangConstants}
import fixtures._
import helpers.SCRSSpec
import mocks.{KeystoreMock, NavModelRepoMock}
import models._
import models.handoff._
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import utils.{BooleanFeatureSwitch, JweCommon, JweEncryptor, SCRSFeatureSwitches}

import scala.concurrent.Future

class HandOffServiceSpec extends SCRSSpec with PayloadFixture with CTDataFixture with CorporationTaxFixture with AuthBuilder
  with UserDetailsFixture
  with CompanyDetailsFixture
  with KeystoreMock
  with NavModelRepoMock
  with GuiceOneAppPerSuite {

  val mockNavModelRepoObj = mockNavModelRepo
  val mockEncryptor = mock[JweEncryptor]

  val testJwe = new JweCommon {
    val key = "Fak3-t0K3n-f0r-pUBLic-r3p0SiT0rY"
  }

  trait Setup {
    val service = new HandOffService {
      val compRegConnector = mockCompanyRegistrationConnector
      val returnUrl = "http://test"
      val externalUrl = "http://external"
      val keystoreConnector = mockKeystoreConnector
      val encryptor = testJwe
      val authConnector = mockAuthConnector
      val navModelMongo = mockNavModelRepoObj
      lazy val timeout = 100
      lazy val timeoutDisplayLength = 30
      override val scrsFeatureSwitches: SCRSFeatureSwitches = mockSCRSFeatureSwitches
      override val appConfig: AppConfig = app.injector.instanceOf[AppConfig]
    }
  }

  override def beforeEach() {
    System.clearProperty("feature.cohoFirstHandOff")
  }

  val externalID = "testExternalID"

  "buildBusinessActivitiesPayload" should {

    val registrationID = "12345-678910"

    "return an encrypted string" in new Setup {

      val testNavModel = HandOffNavModel(
        Sender(Map(
          "1" -> NavLinks("returnFromCoho", "aboutYOu"),
          "3" -> NavLinks("summary", "regularPayments"),
          "5" -> NavLinks("confirmation", "summary"),
          "5-2" -> NavLinks("confirmation", ""))),
        Receiver(Map(
          "0" -> NavLinks("firstHandOff", ""),
          "2" -> NavLinks("SIC codes", "firstHandoff")
        )))

      mockGetNavModel(None)
      mockKeystoreFetchAndGet("registrationID", Some(registrationID))

      when(mockNavModelRepoObj.getNavModel(registrationID))
        .thenReturn(Future.successful(Some(testNavModel)))

      when(mockCompanyRegistrationConnector.retrieveCompanyDetails(ArgumentMatchers.eq(registrationID))(ArgumentMatchers.any[HeaderCarrier](), any()))
        .thenReturn(Future.successful(Some(validCompanyDetailsResponseDifferentAddresses)))

      val result = await(service.buildBusinessActivitiesPayload(registrationID, externalID, LangConstants.english))

      result.get._1 mustBe "SIC codes"

      val address = HandoffPPOB.fromCorePPOB(validCompanyDetailsResponseDifferentAddresses.pPOBAddress)
      val model = BusinessActivitiesModel(
        externalID,
        registrationID,
        Some(address),
        None,
        Json.parse("""{}""").as[JsObject],
        LangConstants.english,
        NavLinks("summary", "regularPayments"))

      testJwe.decrypt[BusinessActivitiesModel](result.get._2).get mustBe model
    }
  }

  "companyNamePayload" should {

    val initNavModel = HandOffNavModel(
      Sender(Map(
        "1" -> NavLinks("http://localhost:9970/register-your-company/principal-place-of-business", "http://localhost:9970/register-your-company/register"),
        "3" -> NavLinks("", "http://localhost:9970/register-your-company/loan-payments-dividends"),
        "5" -> NavLinks("", "http://localhost:9970/register-your-company/check-confirm-answers"))),
      Receiver(Map("0" -> NavLinks("companyNameUrl", ""))))

    "return a forward url and encrypted payload when there is no nav model in the repository" in new Setup {
      mockInsertNavModel("testRegID", Some(initNavModel))
      mockGetNavModel(None)
      when(mockSCRSFeatureSwitches.legacyEnv).thenReturn(BooleanFeatureSwitch("foo", false))
      when(mockSCRSFeatureSwitches.cohoFirstHandOff).thenReturn(BooleanFeatureSwitch("foo", false))
      val result = await(service.companyNamePayload("testRegID", "testemail", "testname", externalID, LangConstants.english))
      result.get._1 mustBe "http://localhost:9986/incorporation-frontend-stubs/basic-company-details"
      val decrypted = testJwe.decrypt[BusinessActivitiesModel](result.get._2)
      decrypted.get mustBe BusinessActivitiesModel(
        "testExternalID",
        "testRegID",
        None, None,
        Json.obj(),
        LangConstants.english,
        NavLinks(
          "http://localhost:9970/register-your-company/corporation-tax-details",
          "http://localhost:9970/register-your-company/return-to-about-you")
      )
    }
  }

  "buildPSCPayload" should {
    val groups = Groups(true,
      nameOfCompany = Some(GroupCompanyName("foo", "Other")),
      addressAndType = Some(GroupsAddressAndType("ALF", NewAddress("1 abc", "2 abc", Some("3 abc"), Some("4 abc"), Some("ZZ1 1ZZ"), Some("country A")))),
      groupUTR = Some(GroupUTR(Some("1234567890"))))
    val validNavModelForThisFunction = HandOffNavModel(
      Sender(Map(
        "3-2" -> NavLinks("SenderUrlToSummary", "ReverseUrlToTradingDetails"))
      ),
      Receiver(Map(
        "3-1" -> NavLinks("PSCStubPage", "BusinessActivitiesStubPage")
      ),
        chData = Some(Json.obj("foo" -> "bar")))
    )
    "Return forward URL and encrypted payload with groups of NONE" in new Setup {
      when(mockKeystoreConnector.fetchAndGet[String](ArgumentMatchers.eq("registrationID"))(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some("12345")))
      mockGetNavModel(Some(validNavModelForThisFunction))

      val result = await(service.buildPSCPayload("12345", "12", None, LangConstants.english))
      result.get._1 mustBe "PSCStubPage"
      val decrypted = testJwe.decrypt[JsObject](result.get._2)
      decrypted.get mustBe Json.parse(
        """{
          |"user_id":"12","journey_id":"12345",
          |"hmrc":{},
          |"another_company_own_shares":false,
          |"ch":{"foo":"bar"},
          |"language": "en",
          |"links":{"forward":"SenderUrlToSummary","reverse":"http://localhost:9970/register-your-company/business-activities-back"}
          |} """.stripMargin)
    }
    "Return forward URL and encrypted payload with full groups" in new Setup {
      when(mockKeystoreConnector.fetchAndGet[String](ArgumentMatchers.eq("registrationID"))(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some("12345")))
      mockGetNavModel(Some(validNavModelForThisFunction))

      val result = await(service.buildPSCPayload("12345", "12", Some(groups), LangConstants.english))
      result.get._1 mustBe "PSCStubPage"
      val decrypted = testJwe.decrypt[JsObject](result.get._2)
      decrypted.get mustBe Json.parse(
        """
          |{"user_id":"12","journey_id":"12345","hmrc":{},"another_company_own_shares":true,"ch":{"foo":"bar"},"parent_company":{"name":"foo","address":{"address_line_1":"1 abc","address_line_2":"2 abc","address_line_3":"3 abc","address_line_4":"4 abc","country":"country A","postal_code":"ZZ1 1ZZ"},"tax_reference":"*******890"},"links":{"forward":"SenderUrlToSummary","reverse":"http://localhost:9970/register-your-company/groups-back-handback","loss_relief_group":"http://localhost:9970/register-your-company/group-relief","parent_address":"http://localhost:9970/register-your-company/owning-companys-address","parent_company_name":"http://localhost:9970/register-your-company/owning-companys-name","parent_tax_reference":"http://localhost:9970/register-your-company/owning-companys-utr"},"loss_relief_group":true, "language": "en"} """.stripMargin)
    }
    "Return forward URL and encrypted payload with groups but relief is false" in new Setup {
      when(mockKeystoreConnector.fetchAndGet[String](ArgumentMatchers.eq("registrationID"))(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some("12345")))
      mockGetNavModel(Some(validNavModelForThisFunction))

      val result = await(service.buildPSCPayload("12345", "12", Some(Groups(false, None, None, None)), LangConstants.english))
      result.get._1 mustBe "PSCStubPage"
      val decrypted = testJwe.decrypt[JsObject](result.get._2)
      decrypted.get mustBe Json.parse(
        """{"user_id":"12","journey_id":"12345","hmrc":{},"another_company_own_shares":true,"ch":{"foo":"bar"},"language": "en","links":{"forward":"SenderUrlToSummary","reverse":"http://localhost:9970/register-your-company/groups-back-handback","loss_relief_group":"http://localhost:9970/register-your-company/group-relief"},"loss_relief_group":false}""".stripMargin)
    }
    "throw exception if groups block incomplete" in new Setup {
      when(mockKeystoreConnector.fetchAndGet[String](ArgumentMatchers.eq("registrationID"))(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some("12345")))
      mockGetNavModel(Some(validNavModelForThisFunction))
      intercept[Exception](await(service.buildPSCPayload("12345", "12", Some(Groups(true, None, None, Some(GroupUTR(None)))), LangConstants.english)))
    }

    "return exception when sender link does not exist in nav model" in new Setup {
      val invalidNavModel = HandOffNavModel(
        Sender(Map(
          "3-123" -> NavLinks("SenderUrlToSummary", "ReverseUrlToTradingDetails"))
        ),
        Receiver(Map(
          "3-1" -> NavLinks("PSCStubPage", "BusinessActivitiesStubPage")
        )))
      when(mockKeystoreConnector.fetchAndGet[String](ArgumentMatchers.eq("registrationID"))(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some("12345")))
      mockGetNavModel(Some(invalidNavModel))

      intercept[Exception](await(service.buildPSCPayload("12345", "12", None, LangConstants.english)))
    }
    "return exception when receiver link does not exist in nav model" in new Setup {
      val invalidNavModel = HandOffNavModel(
        Sender(Map(
          "3-2" -> NavLinks("SenderUrlToSummary", "ReverseUrlToTradingDetails"))
        ),
        Receiver(Map(
          "3-123" -> NavLinks("PSCStubPage", "BusinessActivitiesStubPage")
        )))
      when(mockKeystoreConnector.fetchAndGet[String](ArgumentMatchers.eq("registrationID"))(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some("12345")))
      mockGetNavModel(Some(invalidNavModel))

      intercept[Exception](await(service.buildPSCPayload("12345", "12", None, LangConstants.english)))
    }
    "return an exception when keystorefetchAndGet returns an exception" in new Setup {
      when(mockKeystoreConnector.fetchAndGet[String](ArgumentMatchers.eq("registrationID"))(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new Exception("foo")))

      intercept[Exception](await(service.buildPSCPayload("12345", "12", None, LangConstants.english)))
    }
  }

  "renewSessionObject" should {
    "return a jsObject" in new Setup {
      service.renewSessionObject mustBe JsObject(Map(
        "timeout" -> Json.toJson(service.timeout - service.timeoutDisplayLength),
        "keepalive_url" -> Json.toJson(s"http://localhost:9970${controllers.reg.routes.SignInOutController.renewSession.url}"),
        "signedout_url" -> Json.toJson(s"http://localhost:9970${controllers.reg.routes.SignInOutController.destroySession.url}")))
    }
  }

  "BuildLinks" should {
    "parse the links JSObject twice and return both a nav links model and a jump links model" in new Setup {
      val testNav = NavLinks("testForward", "testBackward")
      val testJump = JumpLinks("testCName", "testCAddr", "testCJuri")

      val testJsLinks: JsObject = Json.obj(
        "forward" -> testNav.forward,
        "reverse" -> testNav.reverse,
        "company_name" -> testJump.company_name,
        "company_address" -> testJump.company_address,
        "company_jurisdiction" -> testJump.company_jurisdiction
      )

      testJsLinks.as[NavLinks] mustBe testNav

      testJsLinks.as[NavLinks].forward mustBe "testForward"
      testJsLinks.as[NavLinks].reverse mustBe "testBackward"

      testJsLinks.as[JumpLinks] mustBe testJump

      testJsLinks.as[JumpLinks].company_name mustBe "testCName"
      testJsLinks.as[JumpLinks].company_address mustBe "testCAddr"
      testJsLinks.as[JumpLinks].company_jurisdiction mustBe "testCJuri"
    }
  }

  "ObjectBuilder" should {
    "build a JsObject with only nav links" in new Setup {
      val testNavObj = Json.obj("forward" -> "testForward", "reverse" -> "testReverse")
      val testNav = NavLinks("testForward", "testReverse")

      val result = service.buildLinksObject(testNav, None)

      result mustBe testNavObj

      result.as[NavLinks].forward mustBe "testForward"
      result.as[NavLinks].reverse mustBe "testReverse"
    }

    "build a JSObject with both nav and jump links" in new Setup {
      val testNavObj =
        Json.obj("forward" -> "testForward",
          "reverse" -> "testReverse",
          "company_name" -> "testCompanyName",
          "company_address" -> "testCompanyAddress",
          "company_jurisdiction" -> "testCompanyJurisdiction")

      val testNav = NavLinks("testForward", "testReverse")
      val testJump = JumpLinks("testCompanyName", "testCompanyAddress", "testCompanyJurisdiction")

      val result = service.buildLinksObject(testNav, Some(testJump))

      result mustBe testNavObj

      result.as[NavLinks].forward mustBe "testForward"
      result.as[NavLinks].reverse mustBe "testReverse"

      result.as[JumpLinks].company_name mustBe "testCompanyName"
      result.as[JumpLinks].company_address mustBe "testCompanyAddress"
      result.as[JumpLinks].company_jurisdiction mustBe "testCompanyJurisdiction"
    }
  }

  "buildHandOffUrl" should {
    "return a link that appends ?request=<PAYLOAD> if url doesn't contain ? OR &" in new Setup {
      val url = service.buildHandOffUrl("testUrl", "payload")
      url mustBe "testUrl?request=payload"
    }

    "return a link that appends &request=<PAYLOAD> if url has ? AND does not end with &" in new Setup {
      val url = service.buildHandOffUrl("testUrl?query=parameter", "payload")
      url mustBe "testUrl?query=parameter&request=payload"
    }

    "return a link that appends request=<PAYLOAD> if url contains ? AND ends with &" in new Setup {
      val url = service.buildHandOffUrl("testUrl?query=parameter&", "testPayload")
      url mustBe "testUrl?query=parameter&request=testPayload"
    }

    "return a link appends request=<PAYLOAD> if the FINAL char is ?" in new Setup {
      val url = service.buildHandOffUrl("testUrl?", "payload")
      url mustBe "testUrl?request=payload"
    }

    "return a link that appends only the payload if url ENDS with ?request=" in new Setup {
      val url = service.buildHandOffUrl("testUrl?request=", "payload")
      url mustBe "testUrl?request=payload"
    }

    "return a link that appends only the payload if url has a param and ENDS with request=" in new Setup {
      val url = service.buildHandOffUrl("testUrl?query=param&request=", "payload")
      url mustBe "testUrl?query=param&request=payload"
    }
  }

  "buildBackHandOff" should {
    "return a BackHandOffModel" in new Setup {
      val handOffNavModel = HandOffNavModel(
        Sender(
          Map(
            "1" -> NavLinks("testForwardLinkFromSender1", "testReverseLinkFromSender1"),
            "3" -> NavLinks("testForwardLinkFromSender3", "testReverseLinkFromSender3"),
            "5" -> NavLinks("testForwardLinkFromSender5", "testReverseLinkFromSender5")
          )
        ),
        Receiver(
          Map(
            "0" -> NavLinks("testForwardLinkFromReceiver0", "testReverseLinkFromReceiver0"),
            "2" -> NavLinks("testForwardLinkFromReceiver2", "testReverseLinkFromReceiver2"),
            "4" -> NavLinks("testForwardLinkFromReceiver4", "testReverseLinkFromReceiver4")
          ),
          Map("testJumpKey" -> "testJumpLink"),
          Some(Json.parse("""{"testCHBagKey": "testValue"}""").as[JsObject])
        )
      )
      mockGetNavModel(None)
      when(mockKeystoreConnector.fetchAndGet[String](ArgumentMatchers.eq("registrationID"))(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some("12345")))

      when(mockNavModelRepoObj.getNavModel("12345"))
        .thenReturn(Future.successful(Some(handOffNavModel)))

      val result = await(service.buildBackHandOff(externalID, LangConstants.english))

      result.user_id mustBe "testExternalID"
      result.journey_id mustBe "12345"
      result.ch mustBe handOffNavModel.receiver.chData.get
      result.hmrc mustBe Json.obj()
      result.links mustBe Json.obj()
    }
  }

  "summaryHandOff" should {
    "return an optional tuple of strings" in new Setup {

      val handOffNavModel = HandOffNavModel(
        Sender(
          Map(
            "1" -> NavLinks("testForwardLinkFromSender1", "testReverseLinkFromSender1"),
            "3" -> NavLinks("testForwardLinkFromSender3", "testReverseLinkFromSender3"),
            "5" -> NavLinks("testForwardLinkFromSender5", "testReverseLinkFromSender5")
          )
        ),
        Receiver(
          Map(
            "0" -> NavLinks("testForwardLinkFromReceiver0", "testReverseLinkFromReceiver0"),
            "2" -> NavLinks("testForwardLinkFromReceiver2", "testReverseLinkFromReceiver2"),
            "4" -> NavLinks("testForwardLinkFromReceiver4", "testReverseLinkFromReceiver4")
          ),
          Map("testJumpKey" -> "testJumpLink"),
          Some(Json.parse("""{"testCHBagKey": "testValue"}""").as[JsObject])
        )
      )
      mockGetNavModel(None)

      when(mockCommonService.fetchRegistrationID(ArgumentMatchers.any()))
        .thenReturn(Future.successful("12345"))

      when(mockNavModelRepoObj.getNavModel("12345"))
        .thenReturn(Future.successful(Some(handOffNavModel)))

      when(mockCompanyRegistrationConnector.updateRegistrationProgress(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), any()))
        .thenReturn(Future.successful(HttpResponse(200, "")))

      val result = await(service.summaryHandOff(externalID, LangConstants.english)).get

      result._1 mustBe "testForwardLinkFromReceiver4"
      val decrypted = testJwe.decrypt[BusinessActivitiesModel](result._2)
      decrypted.get mustBe BusinessActivitiesModel(
        "testExternalID",
        "12345",
        None,
        Some(Json.obj("testCHBagKey" -> "testValue")),
        Json.obj(),
        LangConstants.english,
        NavLinks("testForwardLinkFromSender5", "testReverseLinkFromSender5"))
    }
  }
  "groupsCheckerForPSCHandOff" should {
    "return None if None passed in" in new Setup {
      val res = service.groupsCheckerForPSCHandOff(None)
      res mustBe None
    }
    "return Some of groups if group relief is false and a random block exists" in new Setup {
      val res = service.groupsCheckerForPSCHandOff(Some(Groups(false, nameOfCompany = Some(GroupCompanyName("foo", "Other")),
        addressAndType = Some(GroupsAddressAndType("ALF", NewAddress("1 abc", "2 abc", Some("3 abc"), Some("4 abc"), Some("country A"), Some("ZZ1 1ZZ")))),
        groupUTR = Some(GroupUTR(Some("1234567890"))))))
      res mustBe Some(Groups(false, None, None, None))
    }
    "return exception when of groups if group relief is true and a random blocks DONT exist" in new Setup {
      intercept[Exception](service.groupsCheckerForPSCHandOff(Some(Groups(true, None, None, Some(GroupUTR(Some("foooo")))))))
    }
    "return no exception when of groups if group relief is true and all blocks exist" in new Setup {
      val goodGroups = Groups(true,
        nameOfCompany = Some(GroupCompanyName("foo", "Other")),
        addressAndType = Some(GroupsAddressAndType("ALF", NewAddress("1 abc", "2 abc", Some("3 abc"), Some("4 abc"), Some("country A"), Some("ZZ1 1ZZ")))),
        groupUTR = Some(GroupUTR(Some("1234567890"))))
      val res = service.groupsCheckerForPSCHandOff(Some(goodGroups))
      res.get mustBe goodGroups
    }
  }
  "buildTheStaticJumpLinksForGroupsBasedOnGroupsData" should {
    "return a list of jump links for Group block with true as loss relief" in new Setup {
      val res = service.buildTheStaticJumpLinksForGroupsBasedOnGroupsData(Some(Groups(true, None, None, None)))
      res.get mustBe JumpLinksForGroups(
        "http://localhost:9970/register-your-company/group-relief",
        Some("http://localhost:9970/register-your-company/owning-companys-address"),
        Some("http://localhost:9970/register-your-company/owning-companys-name"),
        Some("http://localhost:9970/register-your-company/owning-companys-utr"))
    }
    "return a 1 jump links for Group block with false as loss relief" in new Setup {
      val res = service.buildTheStaticJumpLinksForGroupsBasedOnGroupsData(Some(Groups(false, None, None, None)))
      res.get mustBe JumpLinksForGroups(
        "http://localhost:9970/register-your-company/group-relief",
        None,
        None,
        None)
    }
    "return None if None is passed in" in new Setup {
      val res = service.buildTheStaticJumpLinksForGroupsBasedOnGroupsData(None)
      res mustBe None
    }
  }
  "buildParentCompanyNameOutOfGroups" should {
    "return None if group relief is false" in new Setup {
      service.buildParentCompanyNameOutOfGroups(Some(Groups(false, None, None, None))) mustBe None
    }
    "return ParentCompany name populated with groups data" in new Setup {
      val goodGroups = Groups(true,
        nameOfCompany = Some(GroupCompanyName("foo", "Other")),
        addressAndType = Some(GroupsAddressAndType("ALF", NewAddress("1 abc", "2 abc", Some("3 abc"), Some("4 abc"), Some("country A"), Some("ZZ1 1ZZ")))),
        groupUTR = Some(GroupUTR(Some("1234567890"))))
      service.buildParentCompanyNameOutOfGroups(Some(goodGroups)) mustBe Some(
        ParentCompany(
          "foo",
          NewAddress("1 abc", "2 abc", Some("3 abc"), Some("4 abc"), Some("country A"), Some("ZZ1 1ZZ")),
          Some("1234567890"))
      )
    }
  }
}