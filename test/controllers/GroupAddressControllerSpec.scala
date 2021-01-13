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

package controllers

import builders.AuthBuilder
import config.FrontendAppConfig
import controllers.groups.GroupAddressController
import helpers.SCRSSpec
import models._
import org.jsoup.Jsoup
import org.jsoup.select.Elements
import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.{Lang, Langs, Messages, MessagesProvider}
import play.api.mvc.{AnyContentAsEmpty, MessagesControllerComponents}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class GroupAddressControllerSpec()(implicit lang: Lang) extends SCRSSpec with GuiceOneAppPerSuite with MockitoSugar with AuthBuilder {

  implicit val appConfig: FrontendAppConfig = app.injector.instanceOf[FrontendAppConfig]
  implicit val messages = app.injector.instanceOf[Messages]
  implicit val langs = app.injector.instanceOf[Langs]
  val mockMcc = app.injector.instanceOf[MessagesControllerComponents]
  implicit val ec: ExecutionContext = global

  class Setup {
    implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
    val alfId = "2"

    val controller = new GroupAddressController(
      mockAuthConnector,
      mockGroupService,
      mockCompanyRegistrationConnector,
      mockKeystoreConnector,
      mockAddressLookupService,
      mockMcc
    )
  }

  val testAddress = NewAddress("l1", "l2", None, None, None, None, None)

  "show" should {
    "return 200 if groups exist and address exists, name is other" in new Setup {
      val testGroups = Groups(groupRelief = true, Some(GroupCompanyName("testGroupCompanyname1", "Other")),
        Some(GroupsAddressAndType("ALF", testAddress)),
        None)
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
      when(mockCompanyRegistrationConnector.retrieveEmail(any())(any()))
        .thenReturn(Future.successful(Some(Email("verified@email", "GG", linkSent = true, verified = true, returnLinkEmailSent = true))))
      when(mockGroupService.retrieveGroups(any())(any()))
        .thenReturn(Future.successful(Some(testGroups)))

      showWithAuthorisedUser(controller.show) {
        result =>
          status(result) shouldBe 200
          val doc = Jsoup.parse(contentAsString(result))
          doc.getElementById("groupAddress-alf").attr("checked") shouldBe "checked"
      }
    }

    "redirect to the groupCompanyName page if it is missing" in new Setup {
      val testGroups = Groups(groupRelief = true, None,
        None,
        None)
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
      when(mockCompanyRegistrationConnector.retrieveEmail(any())(any()))
        .thenReturn(Future.successful(Some(Email("verified@email", "GG", linkSent = true, verified = true, returnLinkEmailSent = true))))
      when(mockGroupService.retrieveGroups(any())(any()))
        .thenReturn(Future.successful(Some(testGroups)))
      showWithAuthorisedUser(controller.show) {
        result =>
          status(result) shouldBe 303
          redirectLocation(result).get shouldBe controllers.groups.routes.GroupNameController.show().url
      }
    }

    "return redirect to ALF if name type is Other and address is empty" in new Setup {
      val testGroups = Groups(groupRelief = true, Some(GroupCompanyName("testGroupCompanyname1", "Other")),
        None,
        None)
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
      when(mockCompanyRegistrationConnector.retrieveEmail(any())(any()))
        .thenReturn(Future.successful(Some(Email("verified@email", "GG", linkSent = true, verified = true, returnLinkEmailSent = true))))
      when(mockGroupService.retrieveGroups(any())(any()))
        .thenReturn(Future.successful(Some(testGroups)))
      when(mockAddressLookupService.initialiseAlfJourney(any(), any(), any(), any())(any[HeaderCarrier], any[MessagesProvider]))
        .thenReturn(Future.successful("foo"))
      showWithAuthorisedUser(controller.show) {
        result =>
          status(result) shouldBe 303
          redirectLocation(result).get shouldBe "foo"
      }
    }

    "return redirect to ALF if retrieveTxApiAddress returns None" in new Setup {
      val testGroups = Groups(groupRelief = true, Some(GroupCompanyName("testGroupCompanyname1", "CohoEntered")),
        None,
        None)
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
      when(mockCompanyRegistrationConnector.retrieveEmail(any())(any()))
        .thenReturn(Future.successful(Some(Email("verified@email", "GG", linkSent = true, verified = true, returnLinkEmailSent = true))))
      when(mockGroupService.retrieveGroups(any())(any()))
        .thenReturn(Future.successful(Some(testGroups)))
      when(mockGroupService.retreiveValidatedTxApiAddress(any(), any())(any()))
        .thenReturn(Future.successful(None))
      when(mockAddressLookupService.initialiseAlfJourney(any(), any(), any(), any())(any[HeaderCarrier], any[MessagesProvider]))
        .thenReturn(Future.successful("foo"))
      showWithAuthorisedUser(controller.show) {
        result =>
          status(result) shouldBe 303
          redirectLocation(result).get shouldBe "foo"
      }
    }

    "return 200 if groups exist, and retrieveTxApiAddress returns an address, but address in cr is empty" in new Setup {
      val testGroups = Groups(groupRelief = true, Some(GroupCompanyName("testGroupCompanyname1", "CohoEntered")),
        None,
        None)
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
      when(mockCompanyRegistrationConnector.retrieveEmail(any())(any()))
        .thenReturn(Future.successful(Some(Email("verified@email", "GG", linkSent = true, verified = true, returnLinkEmailSent = true))))
      when(mockGroupService.retrieveGroups(any())(any()))
        .thenReturn(Future.successful(Some(testGroups)))
      when(mockGroupService.retreiveValidatedTxApiAddress(any(), any())(any()))
        .thenReturn(Future.successful(Some(testAddress)))
      when(mockGroupService.dropOldFields(any(), any(), any())(any()))
        .thenReturn(Future.successful(testGroups))
      when(mockGroupService.createAddressMap(any(), any()))
        .thenReturn(Map("foo" -> "bar"))
      showWithAuthorisedUser(controller.show) {
        result =>
          status(result) shouldBe 200
          val doc = Jsoup.parse(contentAsString(result))
          doc.getElementById("groupAddress-foo").attr("checked") shouldBe ""
          val label = await(doc.getElementsByTag("label")
            .filter { e: Elements => !e.attr("for", "groupAddress-foo").isEmpty }).first()
          label.text shouldBe "bar"
          doc.getElementById("groupAddress-other").attr("checked") shouldBe ""
      }
    }

    "return 200 if groups exist, and retrieveTxApiAddress returns an address, AND address is not empty in cr and matches txapi" in new Setup {
      val testGroups = Groups(groupRelief = true, Some(GroupCompanyName("testGroupCompanyname1", "CohoEntered")),
        Some(GroupsAddressAndType("foo", testAddress)),
        None)
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
      when(mockCompanyRegistrationConnector.retrieveEmail(any())(any()))
        .thenReturn(Future.successful(Some(Email("verified@email", "GG", linkSent = true, verified = true, returnLinkEmailSent = true))))
      when(mockGroupService.retrieveGroups(any())(any()))
        .thenReturn(Future.successful(Some(testGroups)))
      when(mockGroupService.retreiveValidatedTxApiAddress(any(), any())(any()))
        .thenReturn(Future.successful(Some(testAddress)))
      when(mockGroupService.dropOldFields(any(), any(), any())(any()))
        .thenReturn(Future.successful(testGroups))
      when(mockGroupService.createAddressMap(any(), any()))
        .thenReturn(Map("foo" -> "bar"))
      showWithAuthorisedUser(controller.show) {
        result =>
          status(result) shouldBe 200
          val doc = Jsoup.parse(contentAsString(result))
          doc.getElementById("groupAddress-foo").attr("checked") shouldBe "checked"
          val label = await(doc.getElementsByTag("label")
            .filter { e: Elements => !e.attr("for", "groupAddress-foo").isEmpty }).first()
          label.text shouldBe "bar"
          doc.getElementById("groupAddress-other").attr("checked") shouldBe ""
      }
    }
  }

  "submit" should {
    val testGroups = Groups(groupRelief = true, Some(GroupCompanyName("testGroupCompanyname1", "CohoEntered")),
      Some(GroupsAddressAndType("foo", testAddress)),
      None)
    "return 400 if invalid form is submitted by the user" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
      when(mockCompanyRegistrationConnector.retrieveEmail(any())(any()))
        .thenReturn(Future.successful(Some(Email("verified@email", "GG", linkSent = true, verified = true, returnLinkEmailSent = true))))
      when(mockGroupService.retrieveGroups(any())(any()))
        .thenReturn(Future.successful(Some(testGroups)))
      when(mockGroupService.retreiveValidatedTxApiAddress(any(), any())(any()))
        .thenReturn(Future.successful(Some(testAddress)))
      when(mockGroupService.createAddressMap(any(), any()))
        .thenReturn(Map("foo" -> "bar"))
      submitWithAuthorisedUser(controller.submit(), FakeRequest().withFormUrlEncodedBody("groupAddress" -> "WALLS")) {
        result =>
          status(result) shouldBe BAD_REQUEST
      }
    }

    s"return 303 to ${controllers.groups.routes.GroupUtrController.show().url} if submission is TxAPI and saveShareholderAddress returns address" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
      when(mockCompanyRegistrationConnector.retrieveEmail(any())(any()))
        .thenReturn(Future.successful(Some(Email("verified@email", "GG", linkSent = true, verified = true, returnLinkEmailSent = true))))
      when(mockGroupService.retrieveGroups(any())(any()))
        .thenReturn(Future.successful(Some(testGroups)))
      when(mockGroupService.saveTxShareHolderAddress(any(), any())(any())).thenReturn(Future.successful(Right(testGroups)))
      submitWithAuthorisedUser(controller.submit(), FakeRequest().withFormUrlEncodedBody("groupAddress" -> "TxAPI")) {
        result =>
          status(result) shouldBe 303
          redirectLocation(result).get shouldBe controllers.groups.routes.GroupUtrController.show().url
      }
    }

    "return 303 to ALF if submission is TxAPI and saveShareholderAddress returns nothing" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
      when(mockCompanyRegistrationConnector.retrieveEmail(any())(any()))
        .thenReturn(Future.successful(Some(Email("verified@email", "GG", linkSent = true, verified = true, returnLinkEmailSent = true))))
      when(mockGroupService.retrieveGroups(any())(any()))
        .thenReturn(Future.successful(Some(testGroups)))
      when(mockAddressLookupService.initialiseAlfJourney(any(), any(), any(), any())(any[HeaderCarrier], any[MessagesProvider])).thenReturn(Future.successful("foo"))
      when(mockGroupService.saveTxShareHolderAddress(any(), any())(any())).thenReturn(Future.successful(Left(new Exception(""))))
      submitWithAuthorisedUser(controller.submit(), FakeRequest().withFormUrlEncodedBody("groupAddress" -> "TxAPI")) {
        result =>
          status(result) shouldBe 303
          redirectLocation(result).get shouldBe "foo"
      }
    }

    s"return 303 to ${controllers.groups.routes.GroupUtrController.show().url} if submission is ALF" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
      when(mockCompanyRegistrationConnector.retrieveEmail(any())(any()))
        .thenReturn(Future.successful(Some(Email("verified@email", "GG", linkSent = true, verified = true, returnLinkEmailSent = true))))
      when(mockGroupService.retrieveGroups(any())(any()))
        .thenReturn(Future.successful(Some(testGroups)))
      submitWithAuthorisedUser(controller.submit(), FakeRequest().withFormUrlEncodedBody("groupAddress" -> "ALF")) {
        result =>
          status(result) shouldBe 303
          redirectLocation(result).get shouldBe controllers.groups.routes.GroupUtrController.show().url
      }
    }

    s"return 303 to ALF if submission is Other" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
      when(mockCompanyRegistrationConnector.retrieveEmail(any())(any()))
        .thenReturn(Future.successful(Some(Email("verified@email", "GG", linkSent = true, verified = true, returnLinkEmailSent = true))))
      when(mockGroupService.retrieveGroups(any())(any()))
        .thenReturn(Future.successful(Some(testGroups)))
      when(mockAddressLookupService.initialiseAlfJourney(any(), any(), any(), any())(any[HeaderCarrier], any[MessagesProvider])).thenReturn(Future.successful("foo"))
      submitWithAuthorisedUser(controller.submit(), FakeRequest().withFormUrlEncodedBody("groupAddress" -> "Other")) {
        result =>
          status(result) shouldBe 303
          redirectLocation(result).get shouldBe "foo"
      }
    }

    s"return 303 to ${controllers.groups.routes.GroupUtrController.show().url} if submission is CohoEntered" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
      when(mockCompanyRegistrationConnector.retrieveEmail(any())(any()))
        .thenReturn(Future.successful(Some(Email("verified@email", "GG", linkSent = true, verified = true, returnLinkEmailSent = true))))
      when(mockGroupService.retrieveGroups(any())(any()))
        .thenReturn(Future.successful(Some(testGroups)))
      submitWithAuthorisedUser(controller.submit(), FakeRequest().withFormUrlEncodedBody("groupAddress" -> "CohoEntered")) {
        result =>
          status(result) shouldBe 303
          redirectLocation(result).get shouldBe controllers.groups.routes.GroupUtrController.show().url
      }
    }
  }

  "handbackFromALF" should {
    val testGroups = Groups(groupRelief = true, Some(GroupCompanyName("testGroupCompanyname1", "CohoEntered")),
      Some(GroupsAddressAndType("foo", NewAddress("l1", "l2", None, None, None, None, None))),
      None)
    s"return 303 to ${controllers.groups.routes.GroupUtrController.show().url}" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))

      when(mockCompanyRegistrationConnector.retrieveEmail(Matchers.any())(Matchers.any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(Email("verified@email", "GG", linkSent = true, verified = true, returnLinkEmailSent = true))))

      when(mockGroupService.retrieveGroups(Matchers.any())(Matchers.any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(testGroups)))

      when(mockAddressLookupService.getAddress(Matchers.eq(alfId))(Matchers.any[HeaderCarrier]))
        .thenReturn(Future.successful(NewAddress("l1", "l2", None, None, None, None, None)))

      when(mockGroupService.updateGroupAddress(Matchers.any(), Matchers.any())(Matchers.any[HeaderCarrier]))
        .thenReturn(Future.successful(testGroups))

      showWithAuthorisedUser(controller.handbackFromALF(Some(alfId))) {
        result =>
          status(result) shouldBe 303
          redirectLocation(result).get shouldBe controllers.groups.routes.GroupUtrController.show().url
      }
    }
  }
}