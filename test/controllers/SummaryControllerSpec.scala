/*
 * Copyright 2022 HM Revenue & Customs
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

import builders.AuthBuilder
import config.{AppConfig, LangConstants}
import controllers.handoff.HandOffUtils
import controllers.reg.{ControllerErrorHandler, SummaryController}
import fixtures.{AccountingDetailsFixture, CorporationTaxFixture, SCRSFixtures, TradingDetailsFixtures}
import helpers.SCRSSpec
import mocks.TakeoverServiceMock
import models.SummaryListRowUtils.{optSummaryListRowSeq, optSummaryListRowString}
import models._
import models.handoff._
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.{Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json, Writes}
import play.api.mvc.{AnyContentAsEmpty, MessagesControllerComponents}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.NavModelRepo
import services.{NavModelNotFoundException, SummaryService}
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.SummaryList
import uk.gov.hmrc.http.HeaderCarrier
import utils.SCRSFeatureSwitchesImpl
import views.html.reg.{Summary => SummaryView}

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SummaryControllerSpec extends SCRSSpec with SCRSFixtures with GuiceOneAppPerSuite with AccountingDetailsFixture with TradingDetailsFixtures
  with CorporationTaxFixture with AuthBuilder with TakeoverServiceMock {

  val aboutYouData = AboutYouChoice("Director")

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
  lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]
  lazy val mockControllerComponents = app.injector.instanceOf[MessagesControllerComponents]
  lazy val mockControllerErrorHandler = app.injector.instanceOf[ControllerErrorHandler]
  lazy val mockSummaryView = app.injector.instanceOf[SummaryView]
  lazy val mockNavModelRepoObj = app.injector.instanceOf[NavModelRepo]
  override lazy val mockSCRSFeatureSwitches = mock[SCRSFeatureSwitchesImpl]
  lazy val mockSummaryService = mock[SummaryService]



  class Setup {
    val controller = new SummaryController (
      mockAuthConnector,
      mockS4LConnector,
      mockCompanyRegistrationConnector,
      mockKeystoreConnector,
      mockMetaDataService,
      mockTakeoverService,
      mockHandOffService,
      mockNavModelRepoObj,
      mockSCRSFeatureSwitches,
      mockJweCommon,
      mockControllerComponents,
      mockControllerErrorHandler,
      mockSummaryService,
      app.injector.instanceOf[HandOffUtils],
      mockSummaryView
    )(
      appConfig,
      global
      )

  }

  implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
  implicit val messages: Messages = app.injector.instanceOf[MessagesApi].preferred(request)

  lazy val regID = UUID.randomUUID.toString

  val corporationTaxModel = buildCorporationTaxModel()
  val testRegId = "12345"

  val testCompletionBlock = Seq(
    optSummaryListRowSeq(
      messages("pages.summary.completionCapacity.question"),
      Some(Seq("test")),
      Some(controllers.reg.routes.CompletionCapacityController.show.url)
    )
  ).flatten

  val testAccountingBlock = Seq(
  optSummaryListRowString(
    messages("page.reg.summary.tradingDetails"),
    Some(messages("page.reg.ct61.radioYesLabel")),
    Some(controllers.reg.routes.TradingDetailsController.show.url)
  )).flatten

  val testContactDetailsBlock = Seq(
  optSummaryListRowSeq(
    messages("page.reg.summary.companyContact", "TestName"),
    Some(Seq()),
    Some(controllers.reg.routes.CompanyContactDetailsController.show.url)
  )).flatten

  val testCompanyDetailsBlock = Seq(
    optSummaryListRowString(
    messages("page.reg.summary.companyNameText"),
    Some("TestName"),
    Some(controllers.reg.routes.SummaryController.summaryBackLink("company_name").url)
  )).flatten

  val testTakeoverBlock = Seq(
    optSummaryListRowString(
    messages("page.reg.summary.takeovers.otherBusinessName"),
    Some("testName"),
    Some(controllers.takeovers.routes.OtherBusinessNameController.show.url)
  )).flatten


  "Sending a GET request to Summary Controller" should {

    "return a 303 and redirect to the sign in page for an unauthorised user" in new Setup {
      showWithUnauthorisedUser(controller.show) {
        result =>
          status(result) mustBe 303
          redirectLocation(result) mustBe Some(authUrl)
      }
    }

    "return a 200 whilst authorised " in new Setup {
      mockS4LFetchAndGet("HandBackData", Some(validCompanyNameHandBack))

      mockKeystoreFetchAndGet("registrationID", Some(testRegId))
      mockGetTakeoverDetails(testRegId)(Future.successful(None))
      mockS4LFetchAndGet("CompanyContactDetails", Some(validCompanyContactDetailsModel))

      when(mockSummaryService.getCompanyDetailsBlock(ArgumentMatchers.eq(testRegId))(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any())).thenReturn(
        Future.successful(SummaryList(testCompanyDetailsBlock))
      )
      when(mockSummaryService.getTakeoverBlock(ArgumentMatchers.eq(testRegId))(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any())).thenReturn(
        Future.successful(SummaryList(testTakeoverBlock))
      )
      when(mockSummaryService.getContactDetailsBlock(ArgumentMatchers.eq(testRegId))(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any())).thenReturn(
        Future.successful(SummaryList(testContactDetailsBlock))
      )
      when(mockSummaryService.getAccountingDates(ArgumentMatchers.eq(testRegId))(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any())).thenReturn(
        Future.successful(SummaryList(testAccountingBlock))
      )
      when(mockSummaryService.getCompletionCapacity(ArgumentMatchers.eq(testRegId))(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any())).thenReturn(
        Future.successful(SummaryList(testCompletionBlock))
      )

      when(mockCompanyRegistrationConnector.retrieveCorporationTaxRegistration(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(corporationTaxModel))

      showWithAuthorisedUser(controller.show) {
        result =>
          status(result) mustBe OK
      }
    }
  }

  "Post to the Summary Controller" should {
    "return a 303 whilst authorised " in new Setup {
      val request = FakeRequest().withFormUrlEncodedBody()
      submitWithAuthorisedUser(controller.submit, request) {
        result =>
          status(result) mustBe SEE_OTHER
          redirectLocation(result) mustBe Some("/register-your-company/incorporation-summary")
      }
    }
  }

  "back" should {
    "redirect to post sign in if no navModel exists" in new Setup {
      when(mockKeystoreConnector.fetchAndGet[String](ArgumentMatchers.eq("registrationID"))(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(testRegId)))
      when(mockHandOffService.fetchNavModel(ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(Future.failed(new NavModelNotFoundException))
      showWithAuthorisedUserRetrieval(controller.back, Some("extID")) {
        res =>
          status(res) mustBe SEE_OTHER
          redirectLocation(res) mustBe Some("/register-your-company/post-sign-in")
      }
    }
    "redirect to the previous stub page" in new Setup {
      val request = FakeRequest().withFormUrlEncodedBody()

      when(mockKeystoreConnector.fetchAndGet[String](ArgumentMatchers.eq("registrationID"))(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(testRegId)))
      when(mockJweCommon.encrypt[BackHandoff](ArgumentMatchers.any[BackHandoff]())(ArgumentMatchers.any[Writes[BackHandoff]])).thenReturn(Some("foo"))

      when(mockHandOffService.fetchNavModel(ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(Future.successful(handOffNavModel))

      when(mockHandOffService.buildBackHandOff(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(BackHandoff("EXT-123456", testRegId, Json.obj(), Json.obj(), LangConstants.english, Json.obj())))

      when(mockHandOffService.buildHandOffUrl(ArgumentMatchers.eq("testReverseLinkFromReceiver4"), ArgumentMatchers.eq("foo")))
        .thenReturn(s"testReverseLinkFromReceiver4?request=foo")

      submitWithAuthorisedUserRetrieval(controller.back, request, Some("extID")) {
        result =>
          status(result) mustBe SEE_OTHER
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
      when(mockKeystoreConnector.fetchAndGet[String](ArgumentMatchers.eq("registrationID"))(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(testRegId)))
      when(mockHandOffService.fetchNavModel(ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(Future.successful(navModel))
      when(mockJweCommon.encrypt[JsObject](ArgumentMatchers.any[JsObject]())(ArgumentMatchers.any[Writes[JsObject]])).thenReturn(Some("foo"))

      when(mockHandOffService.buildHandOffUrl(ArgumentMatchers.eq("testJumpLink"), ArgumentMatchers.eq("foo")))
        .thenReturn(s"testJumpLink?request=foo")

      showWithAuthorisedUserRetrieval(controller.summaryBackLink("testJumpKey"), Some("extID")) {
        res =>
          status(res) mustBe SEE_OTHER
      }
    }

    "throw an error when an unkown key is passed" in new Setup {

      val navModel = HandOffNavModel(
        Sender(Map("1" -> NavLinks("testForwardLinkFromSender1", "testReverseLinkFromSender1"))),
        Receiver(Map("0" -> NavLinks("testForwardLinkFromReceiver0", "testReverseLinkFromReceiver0")))
      )
      when(mockKeystoreConnector.fetchAndGet[String](ArgumentMatchers.eq("registrationID"))(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(testRegId)))
      when(mockJweCommon.encrypt[JsObject](ArgumentMatchers.any[JsObject]())(ArgumentMatchers.any[Writes[JsObject]])).thenReturn(Some("foo"))
      when(mockHandOffService.fetchNavModel(ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(Future.successful(handOffNavModel))
      showWithAuthorisedUserRetrieval(controller.summaryBackLink("foo"), Some("extID")) {
        res =>
          val ex = intercept[NoSuchElementException](status(res) mustBe SEE_OTHER)
          ex.getMessage mustBe "key not found: foo"
      }
    }
  }
}
