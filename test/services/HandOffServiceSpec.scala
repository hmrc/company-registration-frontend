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

import java.util.UUID

import builders.AuthBuilder
import fixtures._
import helpers.SCRSSpec
import mocks.{KeystoreMock, NavModelRepoMock}
import models.{UserDetailsModel, UserIDs}
import models.handoff._
import org.mockito.{ArgumentCaptor, Matchers}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.test.WithFakeApplication
import utils.JweEncryptor

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

class HandOffServiceSpec extends SCRSSpec with PayloadFixture with CTDataFixture with CorporationTaxFixture
    with BeforeAndAfterEach
    with UserDetailsFixture
    with CompanyDetailsFixture
    with KeystoreMock
    with NavModelRepoMock
    with WithFakeApplication {

  val mockNavModelRepoObj = mockNavModelRepo
  val mockEncryptor = mock[JweEncryptor]

  trait Setup {
    val service = new HandOffService with ServicesConfig {
      override val compRegConnector = mockCompanyRegistrationConnector
      override val returnUrl = "http://test"
      override val keystoreConnector = mockKeystoreConnector
      override val encryptor = mockEncryptor
      override val authConnector = mockAuthConnector
      override val navModelMongo = mockNavModelRepoObj
      override lazy val timeout = 100
      override lazy val timeoutDisplayLength = 30
    }
  }

  override def beforeEach() {
    System.clearProperty("feature.cohoFirstHandOff")
    System.clearProperty("feature.businessActivitiesHandOff")
  }

  val mockCommonService = mock[CommonService]

  implicit val user = AuthBuilder.createTestUser

  val userIDs = UserIDs("testInternalID", "testExternalID")

  "buildBusinessActivitiesPayload" should {

    val registrationID = UUID.randomUUID().toString

    "return an encrypted string" in new Setup {

      val testNavModel = HandOffNavModel(
        Sender(Map(
          "1" -> NavLinks("returnFromCoho", "aboutYOu"),
          "3" -> NavLinks("summary", "regularPayments"),
          "5" -> NavLinks("confirmation", "summary"),
          "5-2" -> NavLinks("confirmation",""))),
        Receiver(Map(
          "0" -> NavLinks("firstHandOff", ""),
          "2" -> NavLinks("SIC codes", "firstHandoff")
        )))

      mockGetNavModel(None)

      when(mockAuthConnector.getIds[UserIDs](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any[ExecutionContext]()))
        .thenReturn(Future.successful(userIDs))

      mockKeystoreFetchAndGet("registrationID",Some(registrationID))

      when(mockNavModelRepoObj.getNavModel(registrationID))
        .thenReturn(Future.successful(Some(testNavModel)))

      when(mockCompanyRegistrationConnector.retrieveCompanyDetails(Matchers.eq(registrationID))(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validCompanyDetailsResponseDifferentAddresses)))

      val chPayload = JsObject(Seq("foo"->Json.toJson("bar")))

      when(mockEncryptor.encrypt[BusinessActivitiesModel](Matchers.any[BusinessActivitiesModel]())(Matchers.any()))
        .thenReturn(Some("xxx"))

      val result = await(service.buildBusinessActivitiesPayload(registrationID))

      result shouldBe defined
      result.get shouldBe "SIC codes" -> "xxx"

      val captor = ArgumentCaptor.forClass(classOf[BusinessActivitiesModel])
      verify(mockEncryptor).encrypt(captor.capture())(Matchers.any())


      val model = BusinessActivitiesModel(
        "testExternalID",
        registrationID,
        Some(handoffPpob1),
        None,
        Json.parse("""{}""").as[JsObject],
        NavLinks("summary","regularPayments"))

      captor.getValue shouldBe model
    }
  }

  "companyNamePayload" should {

    val initNavModel = HandOffNavModel(
      Sender(Map(
        "1" -> NavLinks("http://localhost:9970/register-your-company/principal-place-of-business", "http://localhost:9970/register-your-company/welcome"),
        "3" -> NavLinks("", "http://localhost:9970/register-your-company/trading-details"),
        "5" -> NavLinks("", "http://localhost:9970/register-your-company/summary"))),
      Receiver(Map("0" -> NavLinks("companyNameUrl", "")))
    )

    "return a forward url and encrypted payload when there is no nav model in keystore" in new Setup {
      mockKeystoreFetchAndGet("HandOffNavigation", None)
      mockInsertNavModel("testRegID",Some(initNavModel))
      when(mockAuthConnector.getUserDetails[UserDetailsModel](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any[ExecutionContext]()))
        .thenReturn(Future.successful(userDetailsModel))
      mockGetNavModel(None)
      val result = await(service.companyNamePayload("testRegID"))
      result shouldBe Some(("http://localhost:9986/incorporation-frontend-stubs/basic-company-details","xxx"))
    }
  }

  "externalUserId" should {
    "return an external UserID fetched from auth/authority" in new Setup {
      when(mockAuthConnector.getIds[UserIDs](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any[ExecutionContext]()))
        .thenReturn(Future.successful(userIDs))

      val result = await(service.externalUserId)
      result shouldBe "testExternalID"
    }
  }
  "renewSessionObject" should {
    "return a jsObject" in new Setup {
      service.renewSessionObject shouldBe JsObject(Map(
        "timeout" -> Json.toJson(service.timeout - service.timeoutDisplayLength),
        "keepalive_url" -> Json.toJson(s"http://localhost:9970${controllers.reg.routes.SignInOutController.renewSession().url}"),
        "signedout_url" -> Json.toJson(s"http://localhost:9970${controllers.reg.routes.SignInOutController.destroySession().url}")))
    }
  }

  "BuildLinks" should {
    "parse the links JSObject twice and return both a nav links model and a jump links model" in new Setup {
      val testNav = NavLinks("testForward","testBackward")
      val testJump = JumpLinks("testCName","testCAddr","testCJuri")

      val testJsLinks = Json.obj(
        "forward" -> testNav.forward,
        "reverse" -> testNav.reverse,
        "company_name" -> testJump.company_name,
        "company_address" -> testJump.company_address,
        "company_jurisdiction" -> testJump.company_jurisdiction
      )

      testJsLinks.as[NavLinks] shouldBe testNav

      testJsLinks.as[NavLinks].forward shouldBe "testForward"
      testJsLinks.as[NavLinks].reverse shouldBe "testBackward"


      testJsLinks.as[JumpLinks] shouldBe testJump

      testJsLinks.as[JumpLinks].company_name shouldBe "testCName"
      testJsLinks.as[JumpLinks].company_address shouldBe "testCAddr"
      testJsLinks.as[JumpLinks].company_jurisdiction shouldBe "testCJuri"
    }
  }

  "ObjectBuilder" should {
    "build a JsObject with only nav links" in new Setup {
      val testNavObj = Json.obj("forward" -> "testForward", "reverse" -> "testReverse")
      val testNav = NavLinks("testForward","testReverse")

      val result = service.buildLinksObject(testNav, None)

      result shouldBe testNavObj

      result.as[NavLinks].forward shouldBe "testForward"
      result.as[NavLinks].reverse shouldBe "testReverse"
    }

    "build a JSObject with both nav and jump links" in new Setup {
      val testNavObj =
        Json.obj("forward" -> "testForward",
          "reverse" -> "testReverse",
          "company_name" -> "testCompanyName",
          "company_address" -> "testCompanyAddress",
          "company_jurisdiction" -> "testCompanyJurisdiction")

      val testNav = NavLinks("testForward","testReverse")
      val testJump = JumpLinks("testCompanyName","testCompanyAddress","testCompanyJurisdiction")

      val result = service.buildLinksObject(testNav, Some(testJump))

      result shouldBe testNavObj

      result.as[NavLinks].forward shouldBe "testForward"
      result.as[NavLinks].reverse shouldBe "testReverse"

      result.as[JumpLinks].company_name shouldBe "testCompanyName"
      result.as[JumpLinks].company_address shouldBe "testCompanyAddress"
      result.as[JumpLinks].company_jurisdiction shouldBe "testCompanyJurisdiction"
    }
  }

  "buildHandOffUrl" should {
    "return a link that appends ?request=<PAYLOAD> if url doesn't contain ? OR &" in new Setup {
      val url = service.buildHandOffUrl("testUrl","payload")
      url shouldBe "testUrl?request=payload"
    }

    "return a link that appends &request=<PAYLOAD> if url has ? AND does not end with &" in new Setup {
      val url = service.buildHandOffUrl("testUrl?query=parameter","payload")
      url shouldBe "testUrl?query=parameter&request=payload"
    }

    "return a link that appends request=<PAYLOAD> if url contains ? AND ends with &" in new Setup {
      val url = service.buildHandOffUrl("testUrl?query=parameter&","testPayload")
      url shouldBe "testUrl?query=parameter&request=testPayload"
    }

    "return a link appends request=<PAYLOAD> if the FINAL char is ?" in new Setup {
      val url = service.buildHandOffUrl("testUrl?","payload")
      url shouldBe "testUrl?request=payload"
    }

    "return a link that appends only the payload if url ENDS with ?request=" in new Setup {
      val url = service.buildHandOffUrl("testUrl?request=","payload")
      url shouldBe "testUrl?request=payload"
    }

    "return a link that appends only the payload if url has a param and ENDS with request=" in new Setup {
      val url = service.buildHandOffUrl("testUrl?query=param&request=","payload")
      url shouldBe "testUrl?query=param&request=payload"
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
      when(mockKeystoreConnector.fetchAndGet[String](Matchers.eq("registrationID"))(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some("12345")))

      when(mockHandOffService.externalUserId(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful("testExternalID"))

      when(mockNavModelRepoObj.getNavModel("12345"))
        .thenReturn(Future.successful(Some(handOffNavModel)))

      val result = await(service.buildBackHandOff)

      result.user_id shouldBe "testExternalID"
      result.journey_id shouldBe "12345"
      result.ch shouldBe handOffNavModel.receiver.chData.get
      result.hmrc shouldBe Json.obj()
      result.links shouldBe Json.obj()
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
      when(mockHandOffService.externalUserId(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful("EXT-123456"))

      when(mockCommonService.fetchRegistrationID(Matchers.any()))
        .thenReturn(Future.successful("12345"))

      when(mockNavModelRepoObj.getNavModel("12345"))
        .thenReturn(Future.successful(Some(handOffNavModel)))

      when(mockCompanyRegistrationConnector.updateRegistrationProgress(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(HttpResponse(200)))

      val result = await(service.summaryHandOff).get

      result._1 shouldBe "testForwardLinkFromReceiver4"
      result._2 shouldBe "xxx"
    }
  }
}
