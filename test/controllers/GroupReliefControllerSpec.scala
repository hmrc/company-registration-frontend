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
import controllers.groups.GroupReliefController
import controllers.reg.ControllerErrorHandler
import helpers.SCRSSpec
import models.Groups
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier
import views.html.groups.GroupReliefView
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class GroupReliefControllerSpec extends SCRSSpec with GuiceOneAppPerSuite with MockitoSugar with AuthBuilder {

  lazy val mockMcc = app.injector.instanceOf[MessagesControllerComponents]
  lazy val mockFrontendAppConfig = app.injector.instanceOf[FrontendAppConfig]
  lazy val mockControllerErrorHandler = app.injector.instanceOf[ControllerErrorHandler]
  lazy val mockGroupReliefView = app.injector.instanceOf[GroupReliefView]

  class Setup {
    reset(mockCompanyRegistrationConnector)
    val controller = new GroupReliefController(
      mockAuthConnector,
      mockGroupService,
      mockCompanyRegistrationConnector,
      mockKeystoreConnector,
      mockMcc,
      mockControllerErrorHandler,
      mockGroupReliefView
    )(
      mockFrontendAppConfig
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

  "The GroupReliefController" should {
    "redirect whilst the user is un authorised when sending a GET" in new Setup {
      showWithUnauthorisedUser(controller.show()) {
        result => {
          val response = await(result)
          status(response) shouldBe SEE_OTHER
        }
      }
    }

    "Redirect the user to post sign in if the user is authorised but has no registration id in session" in new Setup {
      val regID = "12345"

      mockKeystoreFetchAndGet("registrationID", None)
      when(mockCompanyRegistrationConnector.fetchCompanyName(any[String]())(any[HeaderCarrier]()))
        .thenReturn(Future.successful("Company Name"))
      when(mockGroupService.retrieveGroups(any())(any()))
        .thenReturn(Future.successful(None))

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
      when(mockGroupService.retrieveGroups(any[String])(any[HeaderCarrier]))
        .thenReturn(Future.successful(None))
      when(mockCompanyRegistrationConnector.fetchCompanyName(any[String]())(any[HeaderCarrier]()))
        .thenReturn(Future.successful("Company Name"))

      showWithAuthorisedUser(controller.show()) {

        result => {
          val response = await(result)
          status(response) shouldBe SEE_OTHER
          response.header.headers("Location") shouldBe "/register-your-company/post-sign-in"
        }
      }
    }

    "display the page whilst the user is authorised and already has a group block saved" in new Setup {
      val groupBlock = """ ,"groups" : {"groupRelief" : true} """
      mockKeystoreFetchAndGet("registrationID", Some("reg123"))

      CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", groupBlock))
      when(mockGroupService.retrieveGroups(any[String])(any[HeaderCarrier]))
        .thenReturn(Future.successful(None))
      when(mockCompanyRegistrationConnector.fetchCompanyName(any[String]())(any[HeaderCarrier]()))
        .thenReturn(Future.successful("Company Name"))

      showWithAuthorisedUser(controller.show()) {

        result => {
          val response = await(result)
          status(response) shouldBe OK
        }
      }
    }

    "display the page whilst the user is authorised and doesn't have a group block saved" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("reg123"))

      CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
      when(mockGroupService.retrieveGroups(any[String])(any[HeaderCarrier]))
        .thenReturn(Future.successful(None))
      when(mockCompanyRegistrationConnector.fetchCompanyName(any[String]())(any[HeaderCarrier]()))
        .thenReturn(Future.successful("Company Name"))

      showWithAuthorisedUser(controller.show()) {

        result => {
          val response = await(result)
          status(response) shouldBe OK
        }
      }
    }

    "return a 303 and redirect to Owning Company's Name page if the user has entered valid data" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("reg123"))

      CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
      when(mockGroupService.retrieveGroups(any[String])(any[HeaderCarrier]))
        .thenReturn(Future.successful(None))
      when(mockCompanyRegistrationConnector.fetchCompanyName(any[String]())(any[HeaderCarrier]()))
        .thenReturn(Future.successful("Company Name"))
      when(mockGroupService.updateGroupRelief(any[Boolean], any[String])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Groups(groupRelief = true, None, None, None)))

      submitWithAuthorisedUser(controller.submit(), FakeRequest().withFormUrlEncodedBody("groupRelief" -> "true")) {
        result =>
          status(result) shouldBe SEE_OTHER
          result.header.headers("Location") shouldBe "/register-your-company/owning-companys-name"
      }
    }

    "return a 303 and redirect to PSC if the user has entered valid data" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("reg123"))

      CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
      when(mockGroupService.retrieveGroups(any[String])(any[HeaderCarrier]))
        .thenReturn(Future.successful(None))
      when(mockCompanyRegistrationConnector.fetchCompanyName(any[String]())(any[HeaderCarrier]()))
        .thenReturn(Future.successful("Company Name"))
      when(mockGroupService.updateGroupRelief(any[Boolean], any[String])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Groups(groupRelief = true, None, None, None)))

      submitWithAuthorisedUser(controller.submit(), FakeRequest().withFormUrlEncodedBody("groupRelief" -> "false")) {
        result =>
          status(result) shouldBe SEE_OTHER
          result.header.headers("Location") shouldBe "/register-your-company/psc-handoff"
      }
    }

    "return a bad request when the form submitted contains incorrect data" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("reg123"))

      CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
      when(mockGroupService.retrieveGroups(any[String])(any[HeaderCarrier]))
        .thenReturn(Future.successful(None))
      when(mockCompanyRegistrationConnector.fetchCompanyName(any[String]())(any[HeaderCarrier]()))
        .thenReturn(Future.successful("d"))
      when(mockGroupService.updateGroupRelief(any[Boolean], any[String])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Groups(groupRelief = true, None, None, None)))

      submitWithAuthorisedUser(controller.submit(), FakeRequest().withFormUrlEncodedBody("groupRelief" -> "maybe")) {
        result =>
          status(result) shouldBe BAD_REQUEST

      }
    }
  }
}