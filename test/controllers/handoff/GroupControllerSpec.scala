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

package controllers.handoff

import builders.AuthBuilder
import config.FrontendAppConfig
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import fixtures.LoginFixture
import helpers.SCRSSpec
import models.handoff.{GroupHandBackModel, NavLinks}
import org.mockito.Matchers._
import org.mockito.Mockito._
import play.api.i18n.MessagesApi
import play.api.libs.json.Json
import play.api.test.Helpers.{defaultAwaitTimeout, redirectLocation}
import services.{HandBackService, HandOffService, NavModelNotFoundException}
import uk.gov.hmrc.play.test.WithFakeApplication

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
    }
  }
  "groupHandBack" should {
    "return 303 when missing bearer token" in new Setup {
      showWithUnauthorisedUser(TestController.groupHandBack("foo")) {
        result =>
          status(result) shouldBe 303
          redirectLocation(result).get shouldBe authUrl("HO3-1","foo")
      }
    }
    "return a 303 and redirect to post sign due to keystore returning nothing" in new Setup {
      mockKeystoreFetchAndGet("registrationID", None)
      showWithAuthorisedUser(TestController.groupHandBack("")) {
        result =>
          status(result) shouldBe 303
          redirectLocation(result).get shouldBe "/register-your-company/post-sign-in?handOffID=HO3-1&payload="
      }
    }
    "return a bad request when processGroupsHandBack returns a failure" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      when(mockHandBackService.processGroupsHandBack(any[String]())(any())).
        thenReturn(Future.successful(Failure(new Exception("foo"))))
      showWithAuthorisedUser(TestController.groupHandBack("fooBarNotDecryptable")) {
        result =>
          status(result) shouldBe 400
      }
    }
  }
  "return a redirect to handoff 3.2 when payload contains corporate shareholders data" in new Setup {
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
    "return a redirect to handoff 4 when payload does not contain corporate shareholders data" in new Setup {
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
          status(result) shouldBe 303
          redirectLocation(result).get shouldBe controllers.handoff.routes.CorporationTaxSummaryController.corporationTaxSummary("fooBar").url
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
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      when(mockHandOffService.buildPSCPayload(any(),any())(any())) thenReturn Future.successful(None)
      showWithAuthorisedUserRetrieval(TestController.PSCGroupHandOff(), Some("extID")) {
        result => status(result) shouldBe 400
      }
    }

    "return a redirect to the 3.2 hand off if buildPSCPayload returns Some" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      when(mockHandOffService.buildPSCPayload(any(),any())(any())) thenReturn Future.successful(Some("foo", "bar"))
      when(mockHandOffService.buildHandOffUrl(any(),any())).thenReturn("foo/bar/wizz/3-2")
      showWithAuthorisedUserRetrieval(TestController.PSCGroupHandOff(), Some("extID")) {
        result =>
          status(result) shouldBe 303
          redirectLocation(result).get shouldBe "foo/bar/wizz/3-2"
      }
    }

    "redirect to post sign in if nav model is not found NavModelNotFoundException" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      when(mockHandOffService.buildPSCPayload(any(),any())(any())) thenReturn Future.failed(new NavModelNotFoundException)
      when(mockHandOffService.buildHandOffUrl(any(),any())).thenReturn("foo/bar/wizz/3-2")
      showWithAuthorisedUserRetrieval(TestController.PSCGroupHandOff(), Some("extID")) {
        result =>
          status(result) shouldBe 303
          redirectLocation(result).get shouldBe controllers.reg.routes.SignInOutController.postSignIn(None).url
      }
    }

    "throw an exception if buildPSCPayload returns an exception" in new Setup {
        mockKeystoreFetchAndGet("registrationID", Some("1"))
        when(mockHandOffService.buildPSCPayload(any(),any())(any())) thenReturn Future.failed(new Exception())
        intercept[Exception](showWithAuthorisedUser(TestController.PSCGroupHandOff) {
          result => await(result)
        })
      }
    }
}