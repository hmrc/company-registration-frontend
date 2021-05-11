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
import controllers.groups.GroupNameController
import helpers.SCRSSpec
import models.{Email, NewAddress, _}
import org.mockito.{ArgumentMatchers, Matchers}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{AnyContentAsEmpty, MessagesControllerComponents}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class GroupNameControllerSpec extends SCRSSpec with GuiceOneAppPerSuite with MockitoSugar with AuthBuilder {

  implicit val appConfig: FrontendAppConfig = app.injector.instanceOf[FrontendAppConfig]
  val mockMcc = app.injector.instanceOf[MessagesControllerComponents]

  class Setup {
    implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
    val controller = new GroupNameController(
      mockAuthConnector,
      mockGroupService,
      mockCompanyRegistrationConnector,
      mockKeystoreConnector,
      mockMcc
    )

    def cTDoc(status: String, groupBlock: String): JsValue = Json.parse(
      s"""
         | {
         |    "internalId" : "Int-f9bf61e1-9f5e-42b6-8676-0949fb1253e7",
         |    "registrationID" : "2971",
         |    "status" : "$status",
         |    "formCreationTimestamp" : "2019-04-09T09:06:55+01:00",
         |    "language" : "en",
         |    "confirmationReferences" : {
         |        "acknowledgement-reference" : "BRCT00000000017",
         |        "transaction-id" : "000-434-2971"
         |    },
         |    "companyDetails" : {
         |        "companyName" : "Company Name Ltd",
         |        "cHROAddress" : {
         |            "premises" : "14",
         |            "address_line_1" : "St Test Walk",
         |            "address_line_2" : "Testley",
         |            "country" : "UK",
         |            "locality" : "Testford",
         |            "postal_code" : "TE1 1ST",
         |            "region" : "Testshire"
         |        },
         |        "pPOBAddress" : {
         |            "addressType" : "RO",
         |            "address" : {
         |                "addressLine1" : "14 St Test Walk",
         |                "addressLine2" : "Testley",
         |                "addressLine3" : "Testford",
         |                "addressLine4" : "Testshire",
         |                "postCode" : "TE1 1ST",
         |                "country" : "UK",
         |                "txid" : "93cf1cfc-75fd-4ac0-96ac-5f0018c70a8f"
         |            }
         |        },
         |        "jurisdiction" : "ENGLAND_AND_WALES"
         |    },
         |    "verifiedEmail" : {
         |        "address" : "user@test.com",
         |        "type" : "GG",
         |        "link-sent" : true,
         |        "verified" : true,
         |        "return-link-email-sent" : false
         |    }
         |    $groupBlock
         |}""".stripMargin)

  }

  "The GroupNameController" should {
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

    when(mockGroupService.retrieveGroups(ArgumentMatchers.any[String])(ArgumentMatchers.any[HeaderCarrier]))
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
    val testGroups = Groups(
      groupRelief = true,
      Some(GroupCompanyName("testGroupCompanyname1", "type")),
      Some(GroupsAddressAndType("type", NewAddress("l1", "l2", None, None, None, None, None))),
      None
    )
    mockKeystoreFetchAndGet("registrationID", Some("1"))
    CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
    when(mockCompanyRegistrationConnector.retrieveEmail(any())(any()))
      .thenReturn(Future.successful(Some(Email("verified@email", "GG", linkSent = true, verified = true, returnLinkEmailSent = true))))
    when(mockGroupService.retrieveGroups(any())(any()))
      .thenReturn(Future.successful(Some(testGroups)))
    when(mockGroupService.returnValidShareholdersAndUpdateGroups(any(), any())(any()))
      .thenReturn(Future.successful(List("1"), testGroups))

    showWithAuthorisedUser(controller.show) {
      result =>
        status(result) shouldBe OK
    }
  }

  "display and empty page whilst the user is authorised and no name stored in CR" in new Setup {
    val testGroups = Groups(
      groupRelief = true,
      None,
      None,
      None
    )
    mockKeystoreFetchAndGet("registrationID", Some("1"))
    CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
    when(mockCompanyRegistrationConnector.retrieveEmail(any())(any()))
      .thenReturn(Future.successful(Some(Email("verified@email", "GG", linkSent = true, verified = true, returnLinkEmailSent = true))))
    when(mockGroupService.retrieveGroups(any())(any()))
      .thenReturn(Future.successful(Some(testGroups)))
    when(mockGroupService.returnValidShareholdersAndUpdateGroups(any(), any())(any()))
      .thenReturn(Future.successful(List("1"), testGroups))

    showWithAuthorisedUser(controller.show) {
      result =>
        status(result) shouldBe OK
    }
  }

  "return a bad request if the form submitted is incorrect" in new Setup {
    val testGroups = Groups(
      groupRelief = true,
      Some(GroupCompanyName("testGroupCompanyname1", "type")),
      Some(GroupsAddressAndType("type", NewAddress("l1", "l2", None, None, None, None, None))),
      None
    )
    mockKeystoreFetchAndGet("registrationID", Some("1"))
    CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
    when(mockCompanyRegistrationConnector.retrieveEmail(any())(any()))
      .thenReturn(Future.successful(Some(Email("verified@email", "GG", linkSent = true, verified = true, returnLinkEmailSent = true))))
    when(mockGroupService.retrieveGroups(any())(any()))
      .thenReturn(Future.successful(Some(testGroups)))
    when(mockGroupService.returnValidShareholdersAndUpdateGroups(any(), any())(any()))
      .thenReturn(Future.successful(List("1"), testGroups))
    when(mockGroupService.updateGroupName(any(), any(), any())(any()))
      .thenReturn(Future.successful(testGroups))

    submitWithAuthorisedUser(controller.submit(), FakeRequest().withFormUrlEncodedBody(
      "group-Name" -> "otherName",
      "other-Name" -> "bob co"
    )) {
      result =>
        status(result) shouldBe BAD_REQUEST
    }
  }


  "return a 303 if the user has entered OTHER name" in new Setup {
    val testGroups = Groups(
      groupRelief = true,
      Some(GroupCompanyName("testGroupCompanyname1", "type")),
      Some(GroupsAddressAndType("type", NewAddress("l1", "l2", None, None, None, None, None))),
      None
    )
    mockKeystoreFetchAndGet("registrationID", Some("1"))
    CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
    when(mockCompanyRegistrationConnector.retrieveEmail(any())(any()))
      .thenReturn(Future.successful(Some(Email("verified@email", "GG", linkSent = true, verified = true, returnLinkEmailSent = true))))
    when(mockGroupService.retrieveGroups(any())(any()))
      .thenReturn(Future.successful(Some(testGroups)))
    when(mockGroupService.returnValidShareholdersAndUpdateGroups(any(), any())(any()))
      .thenReturn(Future.successful(List("1"), testGroups))
    when(mockGroupService.updateGroupName(any(), any(), any())(any()))
      .thenReturn(Future.successful(testGroups))

    submitWithAuthorisedUser(controller.submit(), FakeRequest().withFormUrlEncodedBody(
      "groupName" -> "otherName",
      "otherName" -> "bob co"
    )) {
      result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers("Location") shouldBe "/register-your-company/owning-companys-address"
    }
  }


  "return a 303 if the user has selected the pre-popped name radio button" in new Setup {
    val testGroups = Groups(
      groupRelief = true,
      Some(GroupCompanyName("testGroupCompanyname1", "type")),
      Some(GroupsAddressAndType("type", NewAddress("l1", "l2", None, None, None, None, None))),
      None
    )
    mockKeystoreFetchAndGet("registrationID", Some("1"))
    CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
    when(mockCompanyRegistrationConnector.retrieveEmail(any())(any()))
      .thenReturn(Future.successful(Some(Email("verified@email", "GG", linkSent = true, verified = true, returnLinkEmailSent = true))))
    when(mockGroupService.retrieveGroups(any())(any()))
      .thenReturn(Future.successful(Some(testGroups)))
    when(mockGroupService.returnValidShareholdersAndUpdateGroups(any(), any())(any()))
      .thenReturn(Future.successful(List("1"), testGroups))
    when(mockGroupService.updateGroupName(any(), any(), any())(any()))
      .thenReturn(Future.successful(testGroups))

    submitWithAuthorisedUser(controller.submit(), FakeRequest().withFormUrlEncodedBody(
      "groupName" -> "Bob Group",
      "otherName" -> ""
    )) {
      result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers("Location") shouldBe "/register-your-company/owning-companys-address"
    }
  }
}
