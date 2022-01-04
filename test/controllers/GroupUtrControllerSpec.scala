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
import config.FrontendAppConfig
import controllers.groups.GroupUtrController
import controllers.reg.ControllerErrorHandler
import helpers.SCRSSpec
import models.{Email, NewAddress, _}
import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier
import views.html.groups.GroupUtrView
import scala.concurrent.ExecutionContext.Implicits.global


import scala.concurrent.Future

class GroupUtrControllerSpec extends SCRSSpec with GuiceOneAppPerSuite with MockitoSugar with AuthBuilder {

  lazy val mockMcc = app.injector.instanceOf[MessagesControllerComponents]
  implicit lazy val mockFrontendAppConfig = app.injector.instanceOf[FrontendAppConfig]
  lazy val mockControllerErrorHandler = app.injector.instanceOf[ControllerErrorHandler]
  lazy val mockGroupUtrView = app.injector.instanceOf[GroupUtrView]

  class Setup {
    implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
    val controller = new GroupUtrController(
      mockAuthConnector,
      mockKeystoreConnector,
      mockGroupService,
      mockCompanyRegistrationConnector,
      mockMcc,
      mockGroupUtrView
    )
  }

  "show" should {
    "The GroupUtrController" should {
      "redirect whilst the user is un authorised when sending a GET" in new Setup {
        showWithUnauthorisedUser(controller.show) {
          result => {
            val response = await(result)
            status(response) shouldBe SEE_OTHER
          }
        }
      }
    }


    "Redirect the user to post sign in if the user is authorised but has no registration id in session" in new Setup {
      mockKeystoreFetchAndGet("registrationID", None)

      showWithAuthorisedUser(controller.show) {
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

      when(mockGroupService.retrieveGroups(ArgumentMatchers.any[String])(ArgumentMatchers.any[HeaderCarrier]))
        .thenReturn(Future.successful(None))

      showWithAuthorisedUser(controller.show) {

        result => {
          val response = await(result)
          status(response) shouldBe SEE_OTHER
          response.header.headers("Location") shouldBe "/register-your-company/post-sign-in"
        }
      }
    }

    "display the page whilst the user is authorised" in new Setup {
      val testGroups = Groups(groupRelief = true, Some(GroupCompanyName("testGroupCompanyname1", "type")),
        Some(GroupsAddressAndType("type", NewAddress("l1", "l2", None, None, None, None, None))),
        None)
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
      when(mockCompanyRegistrationConnector.retrieveEmail(any())(any()))
        .thenReturn(Future.successful(Some(Email("verified@email", "GG", linkSent = true, verified = true, returnLinkEmailSent = true))))
      when(mockGroupService.retrieveGroups(any())(any()))
        .thenReturn(Future.successful(Some(testGroups)))

      showWithAuthorisedUser(controller.show) {
        result =>
          status(result) shouldBe OK
      }
    }

    "display an empty page whilst the user is authorised" in new Setup {
      val testGroups = Groups(groupRelief = true, Some(GroupCompanyName("testGroupCompanyname1", "type")),
        Some(GroupsAddressAndType("type", NewAddress("l1", "l2", None, None, None, None, None))),
        Some(GroupUTR(None)))
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
      when(mockCompanyRegistrationConnector.retrieveEmail(any())(any()))
        .thenReturn(Future.successful(Some(Email("verified@email", "GG", linkSent = true, verified = true, returnLinkEmailSent = true))))
      when(mockGroupService.retrieveGroups(any())(any()))
        .thenReturn(Future.successful(Some(testGroups)))

      showWithAuthorisedUser(controller.show) {
        result =>
          status(result) shouldBe OK
          Jsoup.parse(contentAsString(result)).getElementById("groupUtr-noutr").attr("checked") shouldBe "checked"
      }
    }

    "display and prepop the page whilst the user is authorised" in new Setup {
      val testGroups = Groups(groupRelief = true, Some(GroupCompanyName("testGroupCompanyname1", "type")),
        Some(GroupsAddressAndType("type", NewAddress("l1", "l2", None, None, None, None, None))),
        Some(GroupUTR(Some("1234567890"))))
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
      when(mockCompanyRegistrationConnector.retrieveEmail(any())(any()))
        .thenReturn(Future.successful(Some(Email("verified@email", "GG", linkSent = true, verified = true, returnLinkEmailSent = true))))
      when(mockGroupService.retrieveGroups(any())(any()))
        .thenReturn(Future.successful(Some(testGroups)))

      showWithAuthorisedUser(controller.show) {
        result =>
          status(result) shouldBe OK
          Jsoup.parse(contentAsString(result)).getElementById("groupUtr-utr").attr("checked") shouldBe "checked"
          Jsoup.parse(contentAsString(result)).getElementById("utr").attr("value") shouldBe "1234567890"
      }
    }

    "redirect to the address page if the user skipped it" in new Setup {
      val testGroups = Groups(groupRelief = true, Some(GroupCompanyName("testGroupCompanyname1", "type")),
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
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) should contain(controllers.groups.routes.GroupAddressController.show.url)
      }
    }

    "redirect to the company name if the user skipped it" in new Setup {
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
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) should contain(controllers.groups.routes.GroupNameController.show.url)
      }
    }

    "redirect to the start of the journey if the user's data is incorrect for the page" in new Setup {
      val testGroups = Groups(groupRelief = false, None,
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
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) should contain(controllers.reg.routes.SignInOutController.postSignIn().url)
      }
    }
  }

  "submit" should {
    "return a bad request if the form submitted is incorrect" in new Setup {
      val testGroups = Groups(groupRelief = true, Some(GroupCompanyName("testGroupCompanyname1", "type")),
        Some(GroupsAddressAndType("type", NewAddress("l1", "l2", None, None, None, None, None))),
        None)
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
      when(mockCompanyRegistrationConnector.retrieveEmail(any())(any()))
        .thenReturn(Future.successful(Some(Email("verified@email", "GG", linkSent = true, verified = true, returnLinkEmailSent = true))))
      when(mockGroupService.retrieveGroups(any())(any()))
        .thenReturn(Future.successful(Some(testGroups)))

      submitWithAuthorisedUser(controller.submit, FakeRequest().withFormUrlEncodedBody(
        "groupUtr" -> "xxx",
        "utr" -> "1234567890"
      )) {
        result =>
          status(result) shouldBe BAD_REQUEST
      }
    }


    "return a 303 if the user has selected Yes and entered a UTR" in new Setup {
      val testGroups = Groups(groupRelief = true, Some(GroupCompanyName("testGroupCompanyname1", "type")),
        Some(GroupsAddressAndType("type", NewAddress("l1", "l2", None, None, None, None, None))),
        None)
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
      when(mockCompanyRegistrationConnector.retrieveEmail(any())(any()))
        .thenReturn(Future.successful(Some(Email("verified@email", "GG", linkSent = true, verified = true, returnLinkEmailSent = true))))
      when(mockGroupService.retrieveGroups(any())(any()))
        .thenReturn(Future.successful(Some(testGroups)))
      when(mockGroupService.updateGroupUtr(any(), any(), any())(any()))
        .thenReturn(Future.successful(testGroups.copy(groupUTR = Some(GroupUTR(Some("1234567890"))))))

      submitWithAuthorisedUser(controller.submit, FakeRequest().withFormUrlEncodedBody(
        "groupUtr" -> "utr",
        "utr" -> "1234567890"
      )) {
        result =>
          status(result) shouldBe SEE_OTHER
          result.header.headers("Location") shouldBe "/register-your-company/psc-handoff"
      }
    }

    "return a 303 if the user has selected No for the UTR" in new Setup {
      val testGroups = Groups(groupRelief = true, Some(GroupCompanyName("testGroupCompanyname1", "type")),
        Some(GroupsAddressAndType("type", NewAddress("l1", "l2", None, None, None, None, None))),
        None)
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
      when(mockCompanyRegistrationConnector.retrieveEmail(any())(any()))
        .thenReturn(Future.successful(Some(Email("verified@email", "GG", linkSent = true, verified = true, returnLinkEmailSent = true))))
      when(mockGroupService.retrieveGroups(any())(any()))
        .thenReturn(Future.successful(Some(testGroups)))
      when(mockGroupService.updateGroupUtr(any(), any(), any())(any()))
        .thenReturn(Future.successful(testGroups))

      submitWithAuthorisedUser(controller.submit, FakeRequest().withFormUrlEncodedBody(
        "groupUtr" -> "noutr"
      )) {
        result =>
          status(result) shouldBe SEE_OTHER
          result.header.headers("Location") shouldBe "/register-your-company/psc-handoff"
      }
    }
  }
}