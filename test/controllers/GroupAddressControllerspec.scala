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
import connectors.KeystoreConnector
import controllers.groups.GroupAddressController
import helpers.SCRSSpec
import models._
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.mockito.{ArgumentMatcher, Matchers}
import org.scalatestplus.mockito.MockitoSugar
import play.api.i18n.MessagesApi
import play.api.mvc.{Request, Result, Results}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.{AddressLookupFrontendService, GroupPageEnum, GroupService}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.play.test.WithFakeApplication

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class GroupAddressControllerspec extends SCRSSpec with WithFakeApplication with MockitoSugar with AuthBuilder {

  class Setup {
    implicit val r = FakeRequest()
    val controller = new GroupAddressController {
      override val addressLookupFrontendService: AddressLookupFrontendService = mockAddressLookupService
      override val groupService: GroupService = mockGroupService
      override val keystoreConnector: KeystoreConnector = mockKeystoreConnector
      override val compRegConnector = mockCompanyRegistrationConnector
      implicit val appConfig: FrontendAppConfig = fakeApplication.injector.instanceOf[FrontendAppConfig]
      override val messagesApi = fakeApplication.injector.instanceOf[MessagesApi]

      override def authConnector: AuthConnector = mockAuthConnector

      val showFunction = showFunc("1")(_: Groups)(_: Request[_])
      val submitFunction = submitFunc("1")(_: Groups)(_: Request[_])
      val alfFunction = handBackFromALFFunc("1")(_: Groups)(_: Request[_])
    }
  }

    case class funcMatcher(func: Groups => Future[Result]) extends ArgumentMatcher[Groups => Future[Result]] {
      override def matches(oarg: scala.Any): Boolean = true
    }



    "show" should {
      "return 200 if groups exist and address exists, name is other" in new Setup {
        val testGroups = Groups(true, Some(GroupCompanyName("testGroupCompanyname1", "Other")),
          Some(GroupsAddressAndType("ALF", NewAddress("l1", "l2", None, None, None, None, None))),
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
            status(result) shouldBe 200
            val doc = Jsoup.parse(contentAsString(result))
            doc.getElementById("groupAddress-alf").attr("checked") shouldBe "checked"
        }
      }
      "return redirect to ALF if name type is Other and address is empty" in new Setup {
        val testGroups = Groups(true, Some(GroupCompanyName("testGroupCompanyname1", "Other")),
          None,
          None)
        mockKeystoreFetchAndGet("registrationID", Some("1"))
        val mockOfFunc = (g: Groups) => Future.successful(Results.Ok(""))
        CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
        when(mockCompanyRegistrationConnector.retrieveEmail(any())(any())).thenReturn(Future.successful(Some(Email("verified@email", "GG", true, true, true))))
        when(mockGroupService.retrieveGroups(any())(any())).thenReturn(Future.successful(Some(testGroups)))
        when(mockAddressLookupService.buildAddressLookupUrlGroups(any(),any(),any())(any())).thenReturn(Future.successful("foo"))
        val res: Future[Result] = Future.successful(await(controller.showFunction(testGroups, r)))
        when(mockGroupService.groupsUserSkippedPage(any[Option[Groups]](), any[GroupPageEnum.Value]())(Matchers.argThat(funcMatcher(mockOfFunc)))).thenReturn(res)
        showWithAuthorisedUser(controller.show) {
          result =>
            status(result) shouldBe 303
           redirectLocation(result).get shouldBe "foo"
        }
      }

      "return redirect to ALF if returnMapOfAddressesMatchDropAndReturnUpdatedGroups returns empty list" in new Setup {
        val testGroups = Groups(true, Some(GroupCompanyName("testGroupCompanyname1", "CohoEntered")),
          None,
          None)
        mockKeystoreFetchAndGet("registrationID", Some("1"))
        val mockOfFunc = (g: Groups) => Future.successful(Results.Ok(""))
        CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
        when(mockCompanyRegistrationConnector.retrieveEmail(any())(any())).thenReturn(Future.successful(Some(Email("verified@email", "GG", true, true, true))))
        when(mockGroupService.retrieveGroups(any())(any())).thenReturn(Future.successful(Some(testGroups)))
        when(mockGroupService.returnMapOfAddressesMatchDropAndReturnUpdatedGroups(any(),any())(any())).thenReturn(Future.successful(Map.empty[String,String],testGroups))
        when(mockAddressLookupService.buildAddressLookupUrlGroups(any(),any(),any())(any())).thenReturn(Future.successful("foo"))
        val res: Future[Result] = Future.successful(await(controller.showFunction(testGroups, r)))
        when(mockGroupService.groupsUserSkippedPage(any[Option[Groups]](), any[GroupPageEnum.Value]())(Matchers.argThat(funcMatcher(mockOfFunc)))).thenReturn(res)
        showWithAuthorisedUser(controller.show) {
          result =>
            status(result) shouldBe 303
            redirectLocation(result).get shouldBe "foo"
        }
      }
      "return 200 if groups exist, and returnMapOfAddressesMatchDropAndReturnUpdatedGroups is not empty, but address in cr is empty" in new Setup {
        val testGroups = Groups(true, Some(GroupCompanyName("testGroupCompanyname1", "CohoEntered")),
          None,
          None)
        mockKeystoreFetchAndGet("registrationID", Some("1"))
        val mockOfFunc = (g: Groups) => Future.successful(Results.Ok(""))
        CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
        when(mockCompanyRegistrationConnector.retrieveEmail(any())(any())).thenReturn(Future.successful(Some(Email("verified@email", "GG", true, true, true))))
        when(mockGroupService.retrieveGroups(any())(any())).thenReturn(Future.successful(Some(testGroups)))
        when(mockGroupService.returnMapOfAddressesMatchDropAndReturnUpdatedGroups(any(),any())(any())).thenReturn(Future.successful(Map("foo" -> "bar"),testGroups))

        val res: Future[Result] = Future.successful(await(controller.showFunction(testGroups, r)))
        when(mockGroupService.groupsUserSkippedPage(any[Option[Groups]](), any[GroupPageEnum.Value]())(Matchers.argThat(funcMatcher(mockOfFunc)))).thenReturn(res)
        showWithAuthorisedUser(controller.show) {
          result =>
            status(result) shouldBe 200
            val doc = Jsoup.parse(contentAsString(result))
            doc.getElementById("groupAddress-foo").attr("checked") shouldBe ""
            val label = await(doc.getElementsByTag("label")
              .filter{e: Elements => !e.attr("for","groupAddress-foo").isEmpty}).first()
            label.text shouldBe "bar"
            doc.getElementById("groupAddress-other").attr("checked") shouldBe ""
        }
      }
      "return 200 if groups exist, and returnMapOfAddressesMatchDropAndReturnUpdatedGroups is not empty, AND address is not empty in cr and matches one in list" in new Setup {
        val testGroups = Groups(true, Some(GroupCompanyName("testGroupCompanyname1", "CohoEntered")),
          Some(GroupsAddressAndType("foo", NewAddress("l1", "l2", None, None, None, None, None))),
          None)
        mockKeystoreFetchAndGet("registrationID", Some("1"))
        val mockOfFunc = (g: Groups) => Future.successful(Results.Ok(""))
        CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
        when(mockCompanyRegistrationConnector.retrieveEmail(any())(any())).thenReturn(Future.successful(Some(Email("verified@email", "GG", true, true, true))))
        when(mockGroupService.retrieveGroups(any())(any())).thenReturn(Future.successful(Some(testGroups)))
        when(mockGroupService.returnMapOfAddressesMatchDropAndReturnUpdatedGroups(any(),any())(any())).thenReturn(Future.successful(Map("foo" -> "bar"),testGroups))

        val res: Future[Result] = Future.successful(await(controller.showFunction(testGroups, r)))
        when(mockGroupService.groupsUserSkippedPage(any[Option[Groups]](), any[GroupPageEnum.Value]())(Matchers.argThat(funcMatcher(mockOfFunc)))).thenReturn(res)
        showWithAuthorisedUser(controller.show) {
          result =>
            status(result) shouldBe 200
            val doc = Jsoup.parse(contentAsString(result))
            doc.getElementById("groupAddress-foo").attr("checked") shouldBe "checked"
            val label = await(doc.getElementsByTag("label")
              .filter{e: Elements => !e.attr("for","groupAddress-foo").isEmpty}).first()
            label.text shouldBe "bar"
            doc.getElementById("groupAddress-other").attr("checked") shouldBe ""
        }
      }
    }

  "submit" should {
    val testGroups = Groups(true, Some(GroupCompanyName("testGroupCompanyname1", "CohoEntered")),
      Some(GroupsAddressAndType("foo", NewAddress("l1", "l2", None, None, None, None, None))),
      None)
    "return 400 if invalid form is submitted by the user" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      val mockOfFunc = (g: Groups) => Future.successful(Results.Ok(""))
      CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
      when(mockCompanyRegistrationConnector.retrieveEmail(any())(any())).thenReturn(Future.successful(Some(Email("verified@email", "GG", true, true, true))))
      when(mockGroupService.retrieveGroups(any())(any())).thenReturn(Future.successful(Some(testGroups)))
      when(mockGroupService.returnMapOfAddressesMatchDropAndReturnUpdatedGroups(any(),any())(any())).thenReturn(Future.successful(Map("foo" -> "bar"),testGroups))

      val res: Future[Result] = Future.successful(await(controller.submitFunction(testGroups, FakeRequest().withFormUrlEncodedBody("groupAddress" -> "WALLS"))))
      when(mockGroupService.groupsUserSkippedPage(any[Option[Groups]](), any[GroupPageEnum.Value]())(Matchers.argThat(funcMatcher(mockOfFunc)))).thenReturn(res)
      submitWithAuthorisedUser(controller.submit(), FakeRequest().withFormUrlEncodedBody("groupAddress" -> "WALLS")) {
        result =>
          status(result) shouldBe BAD_REQUEST
      }
    }
    s"return 303 to ${controllers.groups.routes.GroupUtrController.show().url} if submission is TxAPI and saveShareholderAddress returns address" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      val mockOfFunc = (g: Groups) => Future.successful(Results.Ok(""))
      CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
      when(mockCompanyRegistrationConnector.retrieveEmail(any())(any())).thenReturn(Future.successful(Some(Email("verified@email", "GG", true, true, true))))
      when(mockGroupService.retrieveGroups(any())(any())).thenReturn(Future.successful(Some(testGroups)))
      when(mockGroupService.returnMapOfAddressesMatchDropAndReturnUpdatedGroups(any(),any())(any())).thenReturn(Future.successful(Map("foo" -> "bar"),testGroups))
      when(mockGroupService.saveTxShareHolderAddress(any(),any())(any())).thenReturn(Future.successful(Right(testGroups)))
      val res: Future[Result] = Future.successful(await(controller.submitFunction(testGroups, FakeRequest().withFormUrlEncodedBody("groupAddress" -> "TxAPI"))))
      when(mockGroupService.groupsUserSkippedPage(any[Option[Groups]](), any[GroupPageEnum.Value]())(Matchers.argThat(funcMatcher(mockOfFunc)))).thenReturn(res)
      submitWithAuthorisedUser(controller.submit(), FakeRequest().withFormUrlEncodedBody("groupAddress" -> "TxAPI")) {
        result =>
          status(result) shouldBe 303
          redirectLocation(result).get shouldBe controllers.groups.routes.GroupUtrController.show().url
      }
    }
    "return 303 to ALF if submission is TxAPI and saveShareholderAddress returns nothing" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      val mockOfFunc = (g: Groups) => Future.successful(Results.Ok(""))
      CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
      when(mockCompanyRegistrationConnector.retrieveEmail(any())(any())).thenReturn(Future.successful(Some(Email("verified@email", "GG", true, true, true))))
      when(mockGroupService.retrieveGroups(any())(any())).thenReturn(Future.successful(Some(testGroups)))
      when(mockAddressLookupService.buildAddressLookupUrlGroups(any(),any(),any())(any())).thenReturn(Future.successful("foo"))
      when(mockGroupService.returnMapOfAddressesMatchDropAndReturnUpdatedGroups(any(),any())(any())).thenReturn(Future.successful(Map("foo" -> "bar"),testGroups))
      when(mockGroupService.saveTxShareHolderAddress(any(),any())(any())).thenReturn(Future.successful(Left(new Exception(""))))
      val res: Future[Result] = Future.successful(await(controller.submitFunction(testGroups, FakeRequest().withFormUrlEncodedBody("groupAddress" -> "TxAPI"))))
      when(mockGroupService.groupsUserSkippedPage(any[Option[Groups]](), any[GroupPageEnum.Value]())(Matchers.argThat(funcMatcher(mockOfFunc)))).thenReturn(res)
      submitWithAuthorisedUser(controller.submit(), FakeRequest().withFormUrlEncodedBody("groupAddress" -> "TxAPI")) {
        result =>
          status(result) shouldBe 303
          redirectLocation(result).get shouldBe "foo"
      }
    }
    s"return 303 to ${controllers.groups.routes.GroupUtrController.show().url} if submission is ALF" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      val mockOfFunc = (g: Groups) => Future.successful(Results.Ok(""))
      CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
      when(mockCompanyRegistrationConnector.retrieveEmail(any())(any())).thenReturn(Future.successful(Some(Email("verified@email", "GG", true, true, true))))
      when(mockGroupService.retrieveGroups(any())(any())).thenReturn(Future.successful(Some(testGroups)))
      when(mockGroupService.returnMapOfAddressesMatchDropAndReturnUpdatedGroups(any(),any())(any())).thenReturn(Future.successful(Map("foo" -> "bar"),testGroups))

      val res: Future[Result] = Future.successful(await(controller.submitFunction(testGroups, FakeRequest().withFormUrlEncodedBody("groupAddress" -> "ALF"))))
      when(mockGroupService.groupsUserSkippedPage(any[Option[Groups]](), any[GroupPageEnum.Value]())(Matchers.argThat(funcMatcher(mockOfFunc)))).thenReturn(res)
      submitWithAuthorisedUser(controller.submit(), FakeRequest().withFormUrlEncodedBody("groupAddress" -> "ALF")) {
        result =>
          status(result) shouldBe 303
          redirectLocation(result).get shouldBe controllers.groups.routes.GroupUtrController.show().url
      }
    }
    s"return 303 to ALF if submission is Other" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      val mockOfFunc = (g: Groups) => Future.successful(Results.Ok(""))
      CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
      when(mockCompanyRegistrationConnector.retrieveEmail(any())(any())).thenReturn(Future.successful(Some(Email("verified@email", "GG", true, true, true))))
      when(mockGroupService.retrieveGroups(any())(any())).thenReturn(Future.successful(Some(testGroups)))
      when(mockAddressLookupService.buildAddressLookupUrlGroups(any(),any(),any())(any())).thenReturn(Future.successful("foo"))
      when(mockGroupService.returnMapOfAddressesMatchDropAndReturnUpdatedGroups(any(),any())(any())).thenReturn(Future.successful(Map("foo" -> "bar"),testGroups))

      val res: Future[Result] = Future.successful(await(controller.submitFunction(testGroups, FakeRequest().withFormUrlEncodedBody("groupAddress" -> "Other"))))
      when(mockGroupService.groupsUserSkippedPage(any[Option[Groups]](), any[GroupPageEnum.Value]())(Matchers.argThat(funcMatcher(mockOfFunc)))).thenReturn(res)
      submitWithAuthorisedUser(controller.submit(), FakeRequest().withFormUrlEncodedBody("groupAddress" -> "Other")) {
        result =>
          status(result) shouldBe 303
          redirectLocation(result).get shouldBe "foo"
      }
    }
    s"return 303 to ${controllers.groups.routes.GroupUtrController.show().url} if submission is CohoEntered" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      val mockOfFunc = (g: Groups) => Future.successful(Results.Ok(""))
      CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
      when(mockCompanyRegistrationConnector.retrieveEmail(any())(any())).thenReturn(Future.successful(Some(Email("verified@email", "GG", true, true, true))))
      when(mockGroupService.retrieveGroups(any())(any())).thenReturn(Future.successful(Some(testGroups)))
      when(mockGroupService.returnMapOfAddressesMatchDropAndReturnUpdatedGroups(any(),any())(any())).thenReturn(Future.successful(Map("foo" -> "bar"),testGroups))

      val res: Future[Result] = Future.successful(await(controller.submitFunction(testGroups, FakeRequest().withFormUrlEncodedBody("groupAddress" -> "CohoEntered"))))
      when(mockGroupService.groupsUserSkippedPage(any[Option[Groups]](), any[GroupPageEnum.Value]())(Matchers.argThat(funcMatcher(mockOfFunc)))).thenReturn(res)
      submitWithAuthorisedUser(controller.submit(), FakeRequest().withFormUrlEncodedBody("groupAddress" -> "CohoEntered")) {
        result =>
          status(result) shouldBe 303
          redirectLocation(result).get shouldBe controllers.groups.routes.GroupUtrController.show().url
      }
    }
  }
  "handbackFromALF" should {
    val testGroups = Groups(true, Some(GroupCompanyName("testGroupCompanyname1", "CohoEntered")),
      Some(GroupsAddressAndType("foo", NewAddress("l1", "l2", None, None, None, None, None))),
      None)
    s"return 303 to ${controllers.groups.routes.GroupUtrController.show().url}" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      val mockOfFunc = (g: Groups) => Future.successful(Results.Ok(""))
      CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
      when(mockCompanyRegistrationConnector.retrieveEmail(any())(any())).thenReturn(Future.successful(Some(Email("verified@email", "GG", true, true, true))))
      when(mockGroupService.retrieveGroups(any())(any())).thenReturn(Future.successful(Some(testGroups)))
      when(mockAddressLookupService.getAddress(any(),any())).thenReturn(Future.successful(NewAddress("l1", "l2", None, None, None, None, None)))
      when(mockGroupService.updateGroupAddress(any(),any(),any())(any())).thenReturn(Future.successful(testGroups))
      val res: Future[Result] = Future.successful(await(controller.alfFunction(testGroups, r)))
      when(mockGroupService.groupsUserSkippedPage(any[Option[Groups]](), any[GroupPageEnum.Value]())(Matchers.argThat(funcMatcher(mockOfFunc)))).thenReturn(res)
      showWithAuthorisedUser(controller.handbackFromALF) {
        result =>
          status(result) shouldBe 303
          redirectLocation(result).get shouldBe controllers.groups.routes.GroupUtrController.show().url
      }
    }
  }
}