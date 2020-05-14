/*
 * Copyright 2020 HM Revenue & Customs
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

package controllers.handoff

import builders.AuthBuilder
import config.FrontendAppConfig
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import fixtures.LoginFixture
import helpers.SCRSSpec
import models.{CHROAddress, Groups, Shareholder}
import models.handoff._
import org.mockito.Matchers
import org.mockito.Matchers.{any, eq => eqTo}
import org.mockito.Mockito._
import play.api.i18n.MessagesApi
import play.api.libs.json.{JsObject, Json, Writes}
import play.api.test.FakeRequest
import play.api.test.Helpers.{defaultAwaitTimeout, redirectLocation}
import services.{GroupServiceDeprecated, HandBackService, HandOffService, NavModelNotFoundException}
import uk.gov.hmrc.play.test.WithFakeApplication
import utils.{BooleanFeatureSwitch, JweCommon, SCRSFeatureSwitches}

import scala.concurrent.Future
import scala.util.{Failure, Success}


class GroupControllerSpec extends SCRSSpec with LoginFixture with WithFakeApplication with AuthBuilder {

  class Setup {
    object TestController extends GroupController {
      val authConnector = mockAuthConnector
      val handBackService: HandBackService = mockHandBackService
      val handOffService: HandOffService = mockHandOffService
      implicit val appConfig: FrontendAppConfig = fakeApplication.injector.instanceOf[FrontendAppConfig]
      val keystoreConnector: KeystoreConnector = mockKeystoreConnector
      val compRegConnector: CompanyRegistrationConnector = mockCompanyRegistrationConnector
      override val messagesApi = fakeApplication.injector.instanceOf[MessagesApi]
      override val scrsFeatureSwitches: SCRSFeatureSwitches = mockSCRSFeatureSwitches
      override val groupService: GroupServiceDeprecated = mockGroupServiceDeprecated
      override val jwe: JweCommon = fakeApplication.injector.instanceOf[JweCommon]
    }
  }
  "groupHandBack" should {
    "return 303 when missing bearer token" in new Setup {
      when(mockSCRSFeatureSwitches.pscHandOff).thenReturn(BooleanFeatureSwitch("pscHandOff",false))
      showWithUnauthorisedUser(TestController.groupHandBack("foo")) {
        result =>
          status(result) shouldBe 303
          redirectLocation(result).get shouldBe authUrl("HO3-1","foo")
      }
    }
    "return a 303 and redirect to post sign due to keystore returning nothing" in new Setup {
      when(mockSCRSFeatureSwitches.pscHandOff).thenReturn(BooleanFeatureSwitch("pscHandOff",false))
      mockKeystoreFetchAndGet("registrationID", None)
      showWithAuthorisedUser(TestController.groupHandBack("")) {
        result =>
          status(result) shouldBe 303
          redirectLocation(result).get shouldBe "/register-your-company/post-sign-in?handOffID=HO3-1&payload="
      }
    }
    "return a bad request when processGroupsHandBack returns a failure" in new Setup {
      when(mockSCRSFeatureSwitches.pscHandOff).thenReturn(BooleanFeatureSwitch("pscHandOff",false))
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      when(mockHandBackService.processGroupsHandBack(any[String]())(any())).
        thenReturn(Future.successful(Failure(new Exception("foo"))))
      showWithAuthorisedUser(TestController.groupHandBack("fooBarNotDecryptable")) {
        result =>
          status(result) shouldBe 400
      }
    }
  }
  "return a redirect to handoff 3.2 when payload contains corporate shareholders data of true but feature switch false" in new Setup {
    when(mockSCRSFeatureSwitches.pscHandOff).thenReturn(BooleanFeatureSwitch("pscHandOff",false))
    mockKeystoreFetchAndGet("registrationID", Some("1"))
    when(mockHandBackService.processGroupsHandBack(any[String]())(any())).
      thenReturn(Future.successful(Success(
        GroupHandBackModel(
          "aaa",
          "aaa",
          Json.obj("key" -> "value"),
          Json.obj("key" -> "value"),
          NavLinks("t", "b"),
          Some(true))))
      )
    showWithAuthorisedUser(TestController.groupHandBack("fooBar")) {
      result =>
        status(result) shouldBe 303
        redirectLocation(result).get shouldBe controllers.handoff.routes.GroupController.PSCGroupHandOff().url
    }
  }
  "return a redirect to handoff 3.2 when shareholder list empty sharholder flag true AND feature switch true" in new Setup {
    when(mockSCRSFeatureSwitches.pscHandOff).thenReturn(BooleanFeatureSwitch("pscHandOff",true))
    when(mockGroupServiceDeprecated.potentiallyDropGroupsBasedOnReturnFromTXApiAndReturnList(any())(any()))
        .thenReturn(Future.successful(List.empty))
    mockKeystoreFetchAndGet("registrationID", Some("1"))
    when(mockHandBackService.processGroupsHandBack(any[String]())(any())).
      thenReturn(Future.successful(Success(
        GroupHandBackModel(
          "aaa",
          "aaa",
          Json.obj("key" -> "value"),
          Json.obj("key" -> "value"),
          NavLinks("t", "b"),
          Some(true))))
      )
    showWithAuthorisedUser(TestController.groupHandBack("fooBar")) {
      result =>
        status(result) shouldBe 303
        redirectLocation(result).get shouldBe controllers.handoff.routes.GroupController.PSCGroupHandOff().url
    }
  }
  "return first groups page when shareholder list not empty sharholder flag true AND feature switch true" in new Setup {
    when(mockSCRSFeatureSwitches.pscHandOff).thenReturn(BooleanFeatureSwitch("pscHandOff",true))
    when(mockGroupServiceDeprecated.potentiallyDropGroupsBasedOnReturnFromTXApiAndReturnList(any())(any()))
      .thenReturn(Future.successful(List(Shareholder("foo",None,None,None,CHROAddress("","",None,"","",None,None,None)))))
    when(mockGroupServiceDeprecated.hasDataChangedIfSoDropGroups(any(),any())(any()))
      .thenReturn(Future.successful(List(Shareholder("foo",None,None,None,CHROAddress("","",None,"","",None,None,None)))))
    mockKeystoreFetchAndGet("registrationID", Some("1"))
    when(mockHandBackService.processGroupsHandBack(any[String]())(any())).
      thenReturn(Future.successful(Success(
        GroupHandBackModel(
          "aaa",
          "aaa",
          Json.obj("key" -> "value"),
          Json.obj("key" -> "value"),
          NavLinks("t", "b"),
          Some(true))))
      )
    showWithAuthorisedUser(TestController.groupHandBack("fooBar")) {
      result =>
        status(result) shouldBe 303
        redirectLocation(result).get shouldBe controllers.groups.routes.GroupReliefController.show().url
    }
  }
    "return a bad request when payload does not contain corporate shareholders data" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      when(mockHandBackService.processGroupsHandBack(any[String]())(any())).
        thenReturn(Future.successful(Success(
          GroupHandBackModel(
            "aaa",
            "aaa",
            Json.obj("key"-> "value"),
            Json.obj("key"->"value"),
            NavLinks("t", "b"),
            None)))
        )
      showWithAuthorisedUser(TestController.groupHandBack("fooBar")) {
        result =>
          status(result) shouldBe 400
      }
  }
  "throw an exception if processGroupsHandBack throws an exception" in new Setup {
    mockKeystoreFetchAndGet("registrationID", Some("1"))
    when(mockHandBackService.processGroupsHandBack(any[String]())(any())).
      thenReturn(Future.failed(new Exception("foo"))
      )
    intercept[Exception](showWithAuthorisedUser(TestController.groupHandBack("fooBar")) {
      result => await(result)
    })
  }

  "PSCGroupHandOff" should {
    "return 303 when missing bearer token" in new Setup {

      showWithUnauthorisedUser(TestController.PSCGroupHandOff()) {
        result =>
          status(result) shouldBe 303
          redirectLocation(result).get shouldBe authUrl
      }
    }

    "return a 303 and redirect to post sign due to keystore returning nothing" in new Setup {
      mockKeystoreFetchAndGet("registrationID", None)
      showWithAuthorisedUserRetrieval(TestController.PSCGroupHandOff(), Some("extID")) {
        result =>
          status(result) shouldBe 303
          redirectLocation(result).get shouldBe controllers.reg.routes.SignInOutController.postSignIn(None,None,None).url
      }
    }

    "return a 400 when buildPSCPayload returns a None" in new Setup {
      when(mockSCRSFeatureSwitches.pscHandOff).thenReturn(BooleanFeatureSwitch("pscHandOff",true))
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      when(mockGroupServiceDeprecated.retrieveGroups(any())(any())).thenReturn(Future.successful(None))
      when(mockHandOffService.buildPSCPayload(any(),any(),any())(any())) thenReturn Future.successful(None)
      showWithAuthorisedUserRetrieval(TestController.PSCGroupHandOff(), Some("extID")) {
        result => status(result) shouldBe 400
      }
    }

    "return a redirect to the 3.2 hand off if buildPSCPayload returns Some" in new Setup {
      when(mockSCRSFeatureSwitches.pscHandOff).thenReturn(BooleanFeatureSwitch("pscHandOff",true))
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      when(mockGroupServiceDeprecated.retrieveGroups(any())(any())).thenReturn(Future.successful(None))
      when(mockHandOffService.buildPSCPayload(any(),any(),any())(any())) thenReturn Future.successful(Some("foo", "bar"))
      when(mockHandOffService.buildHandOffUrl(any(),any())).thenReturn("foo/bar/wizz/3-2")
      showWithAuthorisedUserRetrieval(TestController.PSCGroupHandOff(), Some("extID")) {
        result =>
          status(result) shouldBe 303
          redirectLocation(result).get shouldBe "foo/bar/wizz/3-2"
      }
    }
    "return a redirect to the 3.2 hand off if buildPSCPayload returns Some and groups is Some and feature switch on" in new Setup {
      val groups = Groups(false,None,None,None)
      when(mockSCRSFeatureSwitches.pscHandOff).thenReturn(BooleanFeatureSwitch("pscHandOff",true))
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      when(mockGroupServiceDeprecated.retrieveGroups(any())(any())).thenReturn(Future.successful(Some(groups)))
      when(mockHandOffService.buildPSCPayload(any(),any(),eqTo(Some(groups)))(any())) thenReturn Future.successful(Some("foo", "bar"))
      when(mockHandOffService.buildHandOffUrl(any(),any())).thenReturn("foo/bar/wizz/3-2")
      showWithAuthorisedUserRetrieval(TestController.PSCGroupHandOff(), Some("extID")) {
        result =>
          status(result) shouldBe 303
          redirectLocation(result).get shouldBe "foo/bar/wizz/3-2"
      }
    }
    "return a redirect to the 3.2 hand off if buildPSCPayload returns Some and groups is Some BUT feature switch is false" in new Setup {
      when(mockSCRSFeatureSwitches.pscHandOff).thenReturn(BooleanFeatureSwitch("pscHandOff",false))
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      when(mockGroupServiceDeprecated.retrieveGroups(any())(any())).thenReturn(Future.successful(Some(Groups(false,None,None,None))))
      when(mockHandOffService.buildPSCPayload(any(),any(),eqTo(Option.empty[Groups]))(any())) thenReturn Future.successful(Some("foo", "bar"))
      when(mockHandOffService.buildHandOffUrl(any(),any())).thenReturn("foo/bar/wizz/3-2")
      showWithAuthorisedUserRetrieval(TestController.PSCGroupHandOff(), Some("extID")) {
        result =>
          status(result) shouldBe 303
          redirectLocation(result).get shouldBe "foo/bar/wizz/3-2"
      }
    }


    "redirect to post sign in if nav model is not found NavModelNotFoundException" in new Setup {
      when(mockSCRSFeatureSwitches.pscHandOff).thenReturn(BooleanFeatureSwitch("pscHandOff",true))
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      when(mockGroupServiceDeprecated.retrieveGroups(any())(any())).thenReturn(Future.successful(None))
      when(mockHandOffService.buildPSCPayload(any(),any(),any())(any())) thenReturn Future.failed(new NavModelNotFoundException)
      when(mockHandOffService.buildHandOffUrl(any(),any())).thenReturn("foo/bar/wizz/3-2")
      showWithAuthorisedUserRetrieval(TestController.PSCGroupHandOff(), Some("extID")) {
        result =>
          status(result) shouldBe 303
          redirectLocation(result).get shouldBe controllers.reg.routes.SignInOutController.postSignIn(None).url
      }
    }

    "throw an exception if buildPSCPayload returns an exception" in new Setup {
      when(mockSCRSFeatureSwitches.pscHandOff).thenReturn(BooleanFeatureSwitch("pscHandOff",true))
        mockKeystoreFetchAndGet("registrationID", Some("1"))
        when(mockHandOffService.buildPSCPayload(any(),any(),any())(any())) thenReturn Future.failed(new Exception())
        intercept[Exception](showWithAuthorisedUser(TestController.PSCGroupHandOff) {
          result => await(result)
        })
      }
    }
  "back" should {
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
          "3-1" -> NavLinks("testForwardLinkFromSender3-1", "https://www.foobar.com"),
          "4" -> NavLinks("testForwardLinkFromReceiver4", "testReverseLinkFromReceiver4")
        ),
        Map("testJumpKey" -> "testJumpLink"),
        Some(Json.parse("""{"testCHBagKey": "testValue"}""").as[JsObject])
      )
    )
    "redirect to post sign in if no navModel exists" in new Setup {
      when(mockKeystoreConnector.fetchAndGet[String](Matchers.eq("registrationID"))(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some("12354")))
      when(mockHandOffService.fetchNavModel(Matchers.any())(Matchers.any())).thenReturn(Future.failed(new NavModelNotFoundException))
      showWithAuthorisedUserRetrieval(TestController.back, Some("extID")) {
        res =>
          status(res) shouldBe 303
          redirectLocation(res) shouldBe Some("/register-your-company/post-sign-in")
      }
    }
    "redirect to the previous stub page" in new Setup {
      val request = FakeRequest().withFormUrlEncodedBody()

      when(mockHandOffService.fetchNavModel(Matchers.any())(Matchers.any())).thenReturn(Future.successful(handOffNavModel))

      when(mockHandOffService.buildBackHandOff(Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(BackHandoff("EXT-123456", "12354", Json.obj(), Json.obj(), Json.obj())))
      when(mockHandOffService.buildHandOffUrl(any(),any())).thenReturn("foo")
      submitWithAuthorisedUserRetrieval(TestController.back, request, Some("extID")){
        result =>
          status(result) shouldBe 303
          redirectLocation(result) shouldBe Some("foo")
      }
    }
  }
}