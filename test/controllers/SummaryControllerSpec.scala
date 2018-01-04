/*
 * Copyright 2018 HM Revenue & Customs
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

package controllers

import java.util.UUID

import builders.AuthBuilder
import config.FrontendAuthConnector
import connectors.S4LConnector
import controllers.reg.SummaryController
import fixtures.{AccountingDetailsFixture, CorporationTaxFixture, SCRSFixtures, TradingDetailsFixtures}
import helpers.SCRSSpec
import models._
import models.handoff._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.MetaDataService
import org.mockito.Matchers
import org.mockito.Mockito._
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.play.test.WithFakeApplication

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

class SummaryControllerSpec extends SCRSSpec with SCRSFixtures with WithFakeApplication with AccountingDetailsFixture with TradingDetailsFixtures with CorporationTaxFixture {

  val mockMetaDataService = mock[MetaDataService]

  val aboutYouData = AboutYouChoice("Director")
  val mockNavModelRepoObj = mockNavModelRepo

  val handOffNavModel = HandOffNavModel(
    Sender(
      Map(
        "1" -> NavLinks("testForwardLinkFromSender1", "testReverseLinkFromSender1"),
        "3" -> NavLinks("testForwardLinkFromSender3", "testReverseLinkFromSender3")
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

  class Setup {
    val controller = new SummaryController {
      override val s4LConnector = mockS4LConnector
      override val authConnector = mockAuthConnector
      override val companyRegistrationConnector = mockCompanyRegistrationConnector
      override val keystoreConnector = mockKeystoreConnector
      override val metaDataService = mockMetaDataService
      override val handOffService = mockHandOffService
      override val navModelMongo = mockNavModelRepoObj

    }
  }

  lazy val regID = UUID.randomUUID.toString

  val corporationTaxModel = buildCorporationTaxModel()

  "The SummaryController" should {
    "be using the correct AuthConnector" in new Setup {
      SummaryController.authConnector shouldBe FrontendAuthConnector
    }

    "be using the correct save4later connector" in new Setup {
      SummaryController.s4LConnector shouldBe S4LConnector
    }
  }

  "Sending a GET request to Summary Controller" should {

    "return a 303 and redirect to the sign in page for an unauthorised user" in new Setup {
      AuthBuilder.showWithUnauthorisedUser(controller.show){
        result =>
          status(result) shouldBe 303
          redirectLocation(result) shouldBe Some(authUrl)
      }
    }

    "return a 200 whilst authorised " in new Setup {

      mockS4LFetchAndGet("HandBackData", Some(validCompanyNameHandBack))

      when(mockMetaDataService.getApplicantData(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(aboutYouData))

      mockKeystoreFetchAndGet("registrationID", Some("12345"))

      mockS4LFetchAndGet("CompanyContactDetails", Some(validCompanyContactDetailsModel))
      CTRegistrationConnectorMocks.retrieveCompanyDetails(Some(validCompanyDetailsResponse))
      CTRegistrationConnectorMocks.retrieveTradingDetails(Some(tradingDetailsTrue))
      CTRegistrationConnectorMocks.retrieveContactDetails(CompanyContactDetailsSuccessResponse(validCompanyContactDetailsResponse))
      CTRegistrationConnectorMocks.retrieveAccountingDetails(validAccountingResponse)

      when(mockCompanyRegistrationConnector.retrieveCorporationTaxRegistration(Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(corporationTaxModel))

      AuthBuilder.showWithAuthorisedUser(controller.show, mockAuthConnector){
        result =>
          status(result) shouldBe OK
      }
    }
  }

  "Post to the Summary Controller" should {
    "return a 303 whilst authorised " in new Setup {
      val request = FakeRequest().withFormUrlEncodedBody()
      AuthBuilder.submitWithAuthorisedUser(controller.submit, mockAuthConnector, request){
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/register-your-company/incorporation-summary")
      }
    }
  }

  "back" should {
    "redirect to post sign in if no navModel exists" in new Setup {
      when(mockKeystoreConnector.fetchAndGet[String](Matchers.eq("registrationID"))(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some("12354")))
      when(mockHandOffService.externalUserId(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful("EXT-123456"))
      when(mockKeystoreConnector.fetchAndGet[HandOffNavModel](Matchers.eq("HandOffNavigation"))(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(None))
      mockGetNavModel(None)
      AuthBuilder.showWithAuthorisedUser(controller.back(), mockAuthConnector){
        res =>
          status(res) shouldBe SEE_OTHER
          redirectLocation(res) shouldBe Some("/register-your-company/post-sign-in")
      }

    }
    "redirect to the previous stub page" in new Setup {
      val request = FakeRequest().withFormUrlEncodedBody()

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.eq("registrationID"))(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some("12354")))
      mockGetNavModel(None)
      when(mockHandOffService.externalUserId(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful("EXT-123456"))

      when(mockKeystoreConnector.fetchAndGet[HandOffNavModel](Matchers.eq("HandOffNavigation"))(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(handOffNavModel)))

      when(mockHandOffService.buildBackHandOff(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(BackHandoff("EXT-123456", "12354", Json.obj(), Json.obj(), Json.obj())))

      AuthBuilder.submitWithAuthorisedUser(controller.back, mockAuthConnector, request){
        result =>
          status(result) shouldBe SEE_OTHER
      }
    }
  }

  "summaryBackLink" should {

    "redirect to the specified jump link in the nav model" in new Setup {

      val navModel = HandOffNavModel(
        Sender(
          nav = Map("1" -> NavLinks("testForwardLinkFromSender1", "testReverseLinkFromSender1"))),
        Receiver(
          nav = Map("0" -> NavLinks("testForwardLinkFromReceiver0", "testReverseLinkFromReceiver0")),
          jump = Map("testJumpKey" -> "testJumpLink")
        )
      )
      mockGetNavModel(None)
      when(mockKeystoreConnector.fetchAndGet[String](Matchers.eq("registrationID"))(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some("12354")))
      when(mockHandOffService.externalUserId(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful("EXT-123456"))
      when(mockKeystoreConnector.fetchAndGet[HandOffNavModel](Matchers.eq("HandOffNavigation"))(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(navModel)))

      AuthBuilder.showWithAuthorisedUser(controller.summaryBackLink("testJumpKey"), mockAuthConnector){
        res =>
          status(res) shouldBe SEE_OTHER
      }
    }

    "throw an error when an unkown key is passed" in new Setup {

      val navModel = HandOffNavModel(
        Sender(Map("1" -> NavLinks("testForwardLinkFromSender1", "testReverseLinkFromSender1"))),
        Receiver(Map("0" -> NavLinks("testForwardLinkFromReceiver0", "testReverseLinkFromReceiver0")))
      )
      mockGetNavModel(None)
      when(mockKeystoreConnector.fetchAndGet[String](Matchers.eq("registrationID"))(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some("12354")))
      when(mockHandOffService.externalUserId(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful("EXT-123456"))
      when(mockNavModelRepoObj.getNavModel("12354"))
        .thenReturn(Future.successful(Some(navModel)))
      AuthBuilder.showWithAuthorisedUser(controller.summaryBackLink("testJumpKey"), mockAuthConnector){
        res =>
          val ex = intercept[NoSuchElementException](status(res) shouldBe SEE_OTHER)
          ex.getMessage shouldBe "key not found: testJumpKey"
      }
    }
  }
}
