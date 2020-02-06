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

package controllers

import builders.AuthBuilder
import config.FrontendAppConfig
import controllers.groups.GroupUtrController
import helpers.SCRSSpec
import models.{Email, NewAddress, _}
import org.jsoup.Jsoup
import org.mockito.Matchers.any
import org.mockito.Mockito._
import org.mockito.{ArgumentMatcher, Matchers}
import org.scalatestplus.mockito.MockitoSugar
import play.api.i18n.MessagesApi
import play.api.libs.json.Json
import play.api.mvc.{Request, Result, Results}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.GroupPageEnum
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.WithFakeApplication

import scala.concurrent.Future

class GroupUtrControllerSpec extends SCRSSpec with WithFakeApplication with MockitoSugar with AuthBuilder {

  class Setup {
    implicit val r = FakeRequest()
    val controller = new GroupUtrController {
      val groupService = mockGroupService
      val authConnector = mockAuthConnector
      val keystoreConnector = mockKeystoreConnector
      val metaDataService = mockMetaDataService
      override val compRegConnector = mockCompanyRegistrationConnector
      implicit val appConfig: FrontendAppConfig = fakeApplication.injector.instanceOf[FrontendAppConfig]
      override val messagesApi = fakeApplication.injector.instanceOf[MessagesApi]
      val showFunction = showFunc(_: Groups)(_: Request[_])
      val submitFunction = submitFunc("1")(_: Groups)(_: Request[_])
    }

    case class funcMatcher(func: Groups => Future[Result]) extends ArgumentMatcher[Groups => Future[Result]] {
      override def matches(oarg: scala.Any): Boolean = true
    }
  }

  "The GroupUtrController" should {
    "redirect whilst the user is un authorised when sending a GET" in new Setup {
      showWithUnauthorisedUser(controller.show()) {
        result => {
          val response = await(result)
          status(response) shouldBe SEE_OTHER
        }
      }
    }
  }


  "Redirect the user to post sign in if the user is authorised but has no registration id in session" in new Setup {
    mockKeystoreFetchAndGet("registrationID", None)

    showWithAuthorisedUser(controller.show()) {
      result => {
        val response = await(result)
        status(response) shouldBe SEE_OTHER
        response.header.headers("Location") shouldBe "/register-your-company/post-sign-in"
      }
    }
  }

  "Redirect the user to post sign in if the user is authorised but with incorrect status" in new Setup {
    mockKeystoreFetchAndGet("registrationID", Some("reg123"))
    CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("held", ""))

    when(mockGroupService.retrieveGroups(Matchers.any[String])(Matchers.any[HeaderCarrier]))
      .thenReturn(Future.successful(None))

    showWithAuthorisedUser(controller.show()) {

      result => {
        val response = await(result)
        status(response) shouldBe SEE_OTHER
        response.header.headers("Location") shouldBe "/register-your-company/post-sign-in"
      }
    }
  }

  "display the page whilst the user is authorised" in new Setup {
    val testGroups = Groups(true, Some(GroupCompanyName("testGroupCompanyname1", "type")),
      Some(GroupsAddressAndType("type", NewAddress("l1", "l2", None, None, None, None, None))),
      None)
    mockKeystoreFetchAndGet("registrationID", Some("1"))
    val mockOfFunc = (g: Groups) => Future.successful(Results.Ok(""))
    CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
    when(mockCompanyRegistrationConnector.retrieveEmail(any())(any())).thenReturn(Future.successful(Some(Email("verified@email", "GG", true, true, true))))
    when(mockGroupService.retrieveGroups(any())(any())).thenReturn(Future.successful(Some(testGroups)))
    val res: Future[Result] = Future.successful(await(controller.showFunction(testGroups, r)))
    when(mockGroupService.groupsUserSkippedPage(any[Option[Groups]](), any[GroupPageEnum.Value]())(Matchers.argThat(funcMatcher(mockOfFunc)))).thenReturn(res)

    showWithAuthorisedUser(controller.show) {
      result =>
        status(result) shouldBe OK
    }
  }

  "display an empty page whilst the user is authorised" in new Setup {
    val testGroups = Groups(true, Some(GroupCompanyName("testGroupCompanyname1", "type")),
      Some(GroupsAddressAndType("type", NewAddress("l1", "l2", None, None, None, None, None))),
      Some(GroupUTR(None)))
    mockKeystoreFetchAndGet("registrationID", Some("1"))
    val mockOfFunc = (g: Groups) => Future.successful(Results.Ok(""))
    CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
    when(mockCompanyRegistrationConnector.retrieveEmail(any())(any())).thenReturn(Future.successful(Some(Email("verified@email", "GG", true, true, true))))
    when(mockGroupService.retrieveGroups(any())(any())).thenReturn(Future.successful(Some(testGroups)))
    val res: Future[Result] = Future.successful(await(controller.showFunction(testGroups, r)))
    when(mockGroupService.groupsUserSkippedPage(any[Option[Groups]](), any[GroupPageEnum.Value]())(Matchers.argThat(funcMatcher(mockOfFunc)))).thenReturn(res)

    showWithAuthorisedUser(controller.show) {
      result =>
        status(result) shouldBe OK
        Jsoup.parse(contentAsString(result)).getElementById("groupUtr-noutr").attr("checked") shouldBe "checked"
    }
  }

  "return a bad request if the form submitted is incorrect" in new Setup {
    val testGroups = Groups(true, Some(GroupCompanyName("testGroupCompanyname1", "type")),
      Some(GroupsAddressAndType("type", NewAddress("l1", "l2", None, None, None, None, None))),
      None)
    mockKeystoreFetchAndGet("registrationID", Some("1"))
    val mockOfFunc = (g: Groups) => Future.successful(Results.Ok(""))
    CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
    when(mockCompanyRegistrationConnector.retrieveEmail(any())(any())).thenReturn(Future.successful(Some(Email("verified@email", "GG", true, true, true))))
    when(mockGroupService.retrieveGroups(any())(any())).thenReturn(Future.successful(Some(testGroups)))
    when(mockGroupService.updateGroupUtr(any(), any(), any())(any())).thenReturn(Future.successful(testGroups))
    val res: Future[Result] = Future.successful(await(controller.submitFunction(testGroups, FakeRequest().withFormUrlEncodedBody("groupUtr" -> "xxx"))))
    when(mockGroupService.groupsUserSkippedPage(any[Option[Groups]](), any[GroupPageEnum.Value]())(Matchers.argThat(funcMatcher(mockOfFunc)))).thenReturn(res)

    submitWithAuthorisedUser(controller.submit(), FakeRequest().withFormUrlEncodedBody(
      "groupUtr" -> "xxx",
      "utr" -> "1234567890"
    )) {
      result =>
        status(result) shouldBe BAD_REQUEST
    }
  }


  "return a 303 if the user has selected Yes and entered a UTR" in new Setup {
    val testGroups = Groups(true, Some(GroupCompanyName("testGroupCompanyname1", "type")),
      Some(GroupsAddressAndType("type", NewAddress("l1", "l2", None, None, None, None, None))),
      None)
    mockKeystoreFetchAndGet("registrationID", Some("1"))
    val mockOfFunc = (g: Groups) => Future.successful(Results.Ok(""))
    CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
    when(mockCompanyRegistrationConnector.retrieveEmail(any())(any())).thenReturn(Future.successful(Some(Email("verified@email", "GG", true, true, true))))
    when(mockGroupService.retrieveGroups(any())(any())).thenReturn(Future.successful(Some(testGroups)))
    when(mockGroupService.updateGroupUtr(any(), any(), any())(any())).thenReturn(Future.successful(testGroups))
    val res: Future[Result] = Future.successful(await(controller.submitFunction(testGroups, FakeRequest().withFormUrlEncodedBody("groupUtr" -> "utr", "utr" -> "1234567890"))))
    when(mockGroupService.groupsUserSkippedPage(any[Option[Groups]](), any[GroupPageEnum.Value]())(Matchers.argThat(funcMatcher(mockOfFunc)))).thenReturn(res)

    submitWithAuthorisedUser(controller.submit(), FakeRequest().withFormUrlEncodedBody(
      "groupUtr" -> "utr",
      "utr" -> "1234567890"
    )) {
      result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers("Location") shouldBe "/register-your-company/psc-handoff"
    }
  }

  "return a 303 if the user has selected No for the UTR" in new Setup {
    val testGroups = Groups(true, Some(GroupCompanyName("testGroupCompanyname1", "type")),
      Some(GroupsAddressAndType("type", NewAddress("l1", "l2", None, None, None, None, None))),
      None)
    mockKeystoreFetchAndGet("registrationID", Some("1"))
    val mockOfFunc = (g: Groups) => Future.successful(Results.Ok(""))
    CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
    when(mockCompanyRegistrationConnector.retrieveEmail(any())(any())).thenReturn(Future.successful(Some(Email("verified@email", "GG", true, true, true))))
    when(mockGroupService.retrieveGroups(any())(any())).thenReturn(Future.successful(Some(testGroups)))
    when(mockGroupService.updateGroupUtr(any(), any(), any())(any())).thenReturn(Future.successful(testGroups))
    val res: Future[Result] = Future.successful(await(controller.submitFunction(testGroups, FakeRequest().withFormUrlEncodedBody("groupUtr" -> "noutr"))))
    when(mockGroupService.groupsUserSkippedPage(any[Option[Groups]](), any[GroupPageEnum.Value]())(Matchers.argThat(funcMatcher(mockOfFunc)))).thenReturn(res)

    submitWithAuthorisedUser(controller.submit(), FakeRequest().withFormUrlEncodedBody(
      "groupUtr" -> "noutr"
    )) {
      result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers("Location") shouldBe "/register-your-company/psc-handoff"
    }
  }
}
