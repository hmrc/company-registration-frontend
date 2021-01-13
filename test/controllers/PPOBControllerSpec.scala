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

import java.util.Locale

import builders.AuthBuilder
import config.FrontendAppConfig
import connectors.BusinessRegistrationConnector
import controllers.reg.PPOBController
import fixtures.PPOBFixture
import helpers.SCRSSpec
import models._
import models.handoff._
import org.mockito.Matchers
import org.mockito.Matchers.{any, eq => eqTo}
import org.mockito.Mockito._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n._
import play.api.libs.json.{Json, Writes}
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.{AddressLookupFrontendService, NavModelNotFoundException}
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditResult
import utils.JweCommon

import scala.concurrent.Future

class PPOBControllerSpec()(implicit lang: Lang) extends SCRSSpec with PPOBFixture with GuiceOneAppPerSuite with AuthBuilder {

  val mockNavModelRepoObj = mockNavModelRepo
  val mockBusinessRegConnector = mock[BusinessRegistrationConnector]
  val mockAddressLookupFrontendService = mock[AddressLookupFrontendService]
  implicit val langs = app.injector.instanceOf[Langs]

  val regId = "reg-12345"

  trait Setup {
    val controller = new PPOBController {
      override val controllerComponents = app.injector.instanceOf[MessagesControllerComponents]
      override val authConnector = mockAuthConnector
      override val s4LConnector = mockS4LConnector
      override val keystoreConnector = mockKeystoreConnector
      override val compRegConnector = mockCompanyRegistrationConnector
      override val pPOBService = mockPPOBService
      override val handOffService = mockHandOffService
      override val businessRegConnector = mockBusinessRegConnector
      override val addressLookupFrontendService = mockAddressLookupFrontendService
      implicit val appConfig: FrontendAppConfig = app.injector.instanceOf[FrontendAppConfig]
      override val jwe: JweCommon = mockJweCommon
      implicit val messages: Messages = app.injector.instanceOf[MessagesApi].preferred(Seq(Lang(Locale.ENGLISH)))
    }

    def mockCheckStatus(ret: Option[String] = Some(regId)) = {
      when(mockKeystoreConnector.fetchAndGet[String](eqTo("registrationID"))(any(), any()))
        .thenReturn(Future.successful(ret))
      when(mockCompanyRegistrationConnector.retrieveCorporationTaxRegistration(eqTo(regId))(any()))
        .thenReturn(Future.successful(buildCorporationTaxModel(rid = regId)))
    }
  }

  val extID = Some("externalID")
  val credID = Credentials("credID", "testProv")

  "back" should {

    "return a 303 an redirect back to post sign in when navmodel not found thrown" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      when(mockHandOffService.fetchNavModel(Matchers.any[Boolean])(Matchers.any())).thenReturn(Future.failed[HandOffNavModel](new NavModelNotFoundException))

      showWithAuthorisedUserRetrieval(controller.back, extID) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/register-your-company/post-sign-in")
      }
    }

    "return a 303 and redirect back to the first stub" in new Setup {
      val navModel = HandOffNavModel(
        Sender(Map(
          "1" -> NavLinks("testReturn", "testAboutYou"),
          "3" -> NavLinks("testSummary", "testRegPay"),
          "5" -> NavLinks("testConfirmation", "testSummary"))),
        Receiver(Map(
          "0" -> NavLinks("testFirstHandOff", ""),
          "2" -> NavLinks("testForward", "testReverse")
        ))
      )
      when(mockJweCommon.encrypt[BackHandoff](Matchers.any[BackHandoff])(Matchers.any[Writes[BackHandoff]])).thenReturn(None)

      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      when(mockHandOffService.fetchNavModel(Matchers.any[Boolean])(Matchers.any())).thenReturn(Future.successful(handOffNavModelData))
      when(mockHandOffService.buildBackHandOff(Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(BackHandoff("ext-xxxx-xxxx", "12345", Json.obj(), Json.obj(), Json.obj())))

      showWithAuthorisedUserRetrieval(controller.back, extID) {
        result =>
          status(result) shouldBe SEE_OTHER
      }
    }
  }

  "show" should {

    "return a 200 and show the page when check status returns Some of reg id" in new Setup {
      mockCheckStatus()
      when(mockPPOBService.fetchAddressesAndChoice(Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(Some(CHROAddress("38", "line 1", None, "Telford", "UK", None, None, None)), Some(NewAddress("line 1", "line 2", None, None, None, None, None)), PPOBChoice("")))
      showWithAuthorisedUser(controller.show) {
        result =>
          status(result) shouldBe OK
      }
    }

    "return a 303 and show the page when check status returns None" in new Setup {
      mockCheckStatus(None)

      showWithAuthorisedUser(controller.show) {
        result =>
          status(result) shouldBe SEE_OTHER
      }
    }
  }

  "submit" should {

    def submission(addr: String) = FakeRequest().withFormUrlEncodedBody("addressChoice" -> s"$addr")

    val validCompanyDetails = CompanyDetails("TestLTD", validCHROAddress, PPOB("RO", None), "UK")

    "handle a PPOB Address selection correctly" in new Setup {
      mockCheckStatus()
      submitWithAuthorisedUserRetrieval(controller.submit, submission("PPOB"), credID) {
        result =>
          status(result) shouldBe 303
          redirectLocation(result).get shouldBe controllers.reg.routes.CompanyContactDetailsController.show().url
      }
    }

    "handle an RO Address selection correctly" in new Setup {
      val validUserDetails = UserDetailsModel("", "", "", None, None, None, None, "", "")

      mockCheckStatus()
      when(mockPPOBService.saveAddress(Matchers.anyString(), Matchers.anyString(), Matchers.any())(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(validCompanyDetails))
      when(mockPPOBService.retrieveCompanyDetails(Matchers.anyString())(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(validCompanyDetails))
      when(mockPPOBService.auditROAddress(Matchers.anyString(), Matchers.any(), Matchers.anyString(), Matchers.any())(Matchers.any[HeaderCarrier](), Matchers.any()))
        .thenReturn(Future.successful(AuditResult.Success))

      submitWithAuthorisedUserRetrieval(controller.submit, submission("RO"), credID) {
        result =>
          status(result) shouldBe 303
          redirectLocation(result).get shouldBe controllers.reg.routes.CompanyContactDetailsController.show().url
      }
    }

    "handle a none RO/PPOB Address selection correctly" in new Setup {
      mockCheckStatus()
      when(mockAddressLookupFrontendService.initialiseAlfJourney(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any[HeaderCarrier](), Matchers.any[MessagesProvider]))
        .thenReturn(Future.successful("TEST/redirectUrl"))

      submitWithAuthorisedUserRetrieval(controller.submit, submission("Other"), credID) {
        result =>
          status(result) shouldBe 303
          redirectLocation(result).get shouldBe "TEST/redirectUrl"
      }
    }

    "handle an invalid submission correctly" in new Setup {
      mockCheckStatus()
      when(mockPPOBService.fetchAddressesAndChoice(Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(
          Some(CHROAddress("38", "line 1", None, "Telford", "UK", None, None, None)),
          Some(NewAddress("line 1", "line 2", None, None, None, None, None)),
          PPOBChoice(""))
        )

      submitWithAuthorisedUserRetrieval(controller.submit, FakeRequest().withFormUrlEncodedBody("whoops" -> "not good"), credID) {
        result =>
          status(result) shouldBe 400
      }
    }

    "handle an invalid address type correctly" in new Setup {
      mockCheckStatus()
      when(mockCompanyRegistrationConnector.retrieveCompanyDetails(Matchers.anyString())(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validCompanyDetails)))

      submitWithAuthorisedUserRetrieval(controller.submit, submission("Bad"), credID) {
        result =>
          status(result) shouldBe 400
      }
    }
  }

  "saveALFAddress" should {
    val validCompanyDetails = CompanyDetails("TestLTD", validCHROAddress, PPOB("RO", None), "UK")

    "return a PPOBChoice with a value of PPOB if the supplied ppob option is defined" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      val alfId = "123"

      when(mockAddressLookupFrontendService.getAddress(Matchers.eq(alfId))(Matchers.any[HeaderCarrier]))
        .thenReturn(validNewAddress)

      when(mockPPOBService.saveAddress(Matchers.anyString(), Matchers.anyString(), Matchers.any[Option[NewAddress]]())(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(validCompanyDetails))

      when(mockBusinessRegConnector.updatePrePopAddress(Matchers.any[String], Matchers.any[Address])(Matchers.any[HeaderCarrier]))
        .thenReturn(Future.successful(true))

      mockCheckStatus()

      showWithAuthorisedUser(controller.saveALFAddress(Some(alfId))) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(controllers.reg.routes.CompanyContactDetailsController.show().url)
      }

    }

    "return a an exception if the call does not have an id" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      mockAuthorisedUser(Future.successful({}))
      mockCheckStatus()

      intercept[Exception](await(controller.saveALFAddress(None)(FakeRequest())))
    }
  }
}