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

import java.util.Locale

import builders.AuthBuilder
import config.AppConfig
import connectors.BusinessRegistrationConnector
import controllers.reg.{ControllerErrorHandler, PPOBController}
import fixtures.PPOBFixture
import helpers.SCRSSpec
import models._
import models.handoff._
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n._
import play.api.libs.json.{Json, Writes}
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.NavModelRepo
import services.{AddressLookupFrontendService, NavModelNotFoundException}
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditResult
import utils.{JweCommon, SCRSFeatureSwitches}
import views.html.reg.{PrinciplePlaceOfBusiness => PrinciplePlaceOfBusinessView}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PPOBControllerSpec()(implicit lang: Lang) extends SCRSSpec with PPOBFixture with GuiceOneAppPerSuite with AuthBuilder {

  lazy val mockNavModelRepoObj = mock[NavModelRepo]
  lazy val mockBusinessRegConnector = mock[BusinessRegistrationConnector]
  lazy val mockAddressLookupFrontendService = mock[AddressLookupFrontendService]
  lazy val mockMcc = app.injector.instanceOf[MessagesControllerComponents]
  lazy val mockControllerErrorHandler = app.injector.instanceOf[ControllerErrorHandler]
  override lazy val mockSCRSFeatureSwitches = app.injector.instanceOf[SCRSFeatureSwitches]
  lazy val mockPrinciplePlaceOfBusinessView = app.injector.instanceOf[PrinciplePlaceOfBusinessView]
  implicit val langs = app.injector.instanceOf[Langs]

  val regId = "reg-12345"

  trait Setup {
    val controller = new PPOBController(
      mockAuthConnector,
      mockS4LConnector,
      mockKeystoreConnector,
      mockCompanyRegistrationConnector,
      mockHandOffService,
      mockBusinessRegConnector,
      mockNavModelRepoObj,
      mockJweCommon,
      mockAddressLookupFrontendService,
      mockPPOBService,
      mockSCRSFeatureSwitches,
      mockMcc,
      mockControllerErrorHandler,
      mockPrinciplePlaceOfBusinessView
    )
    (
      mockAppConfig,
      global
    )

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
      when(mockHandOffService.fetchNavModel(ArgumentMatchers.any[Boolean])(ArgumentMatchers.any())).thenReturn(Future.failed[HandOffNavModel](new NavModelNotFoundException))

      showWithAuthorisedUserRetrieval(controller.back, extID) {
        result =>
          status(result) mustBe SEE_OTHER
          redirectLocation(result) mustBe Some("/register-your-company/post-sign-in")
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
      when(mockJweCommon.encrypt[BackHandoff](ArgumentMatchers.any[BackHandoff])(ArgumentMatchers.any[Writes[BackHandoff]])).thenReturn(None)

      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      when(mockHandOffService.fetchNavModel(ArgumentMatchers.any[Boolean])(ArgumentMatchers.any())).thenReturn(Future.successful(handOffNavModelData))
      when(mockHandOffService.buildBackHandOff(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(BackHandoff("ext-xxxx-xxxx", "12345", Json.obj(), Json.obj(), Json.obj())))

      showWithAuthorisedUserRetrieval(controller.back, extID) {
        result =>
          status(result) mustBe SEE_OTHER
      }
    }
  }

  "show" should {

    "return a 200 and show the page when check status returns Some of reg id" in new Setup {
      mockCheckStatus()
      when(mockPPOBService.fetchAddressesAndChoice(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(CHROAddress("38", "line 1", None, "Telford", "UK", None, None, None)), Some(NewAddress("line 1", "line 2", None, None, None, None, None)), PPOBChoice("")))
      showWithAuthorisedUser(controller.show) {
        result =>
          status(result) mustBe OK
      }
    }

    "return a 303 and show the page when check status returns None" in new Setup {
      mockCheckStatus(None)

      showWithAuthorisedUser(controller.show) {
        result =>
          status(result) mustBe SEE_OTHER
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
          status(result) mustBe 303
          redirectLocation(result).get mustBe controllers.reg.routes.CompanyContactDetailsController.show.url
      }
    }

    "handle an RO Address selection correctly" in new Setup {
      val validUserDetails = UserDetailsModel("", "", "", None, None, None, None, "", "")

      mockCheckStatus()
      when(mockPPOBService.saveAddress(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.any())(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(validCompanyDetails))
      when(mockPPOBService.retrieveCompanyDetails(ArgumentMatchers.anyString())(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(validCompanyDetails))
      when(mockPPOBService.auditROAddress(ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.anyString(), ArgumentMatchers.any())(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.successful(AuditResult.Success))

      submitWithAuthorisedUserRetrieval(controller.submit, submission("RO"), credID) {
        result =>
          status(result) mustBe 303
          redirectLocation(result).get mustBe controllers.reg.routes.CompanyContactDetailsController.show.url
      }
    }

    "handle a none RO/PPOB Address selection correctly" in new Setup {
      mockCheckStatus()
      when(mockAddressLookupFrontendService.initialiseAlfJourney(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[MessagesProvider]))
        .thenReturn(Future.successful("TEST/redirectUrl"))

      submitWithAuthorisedUserRetrieval(controller.submit, submission("Other"), credID) {
        result =>
          status(result) mustBe 303
          redirectLocation(result).get mustBe "TEST/redirectUrl"
      }
    }

    "handle an invalid submission correctly" in new Setup {
      mockCheckStatus()
      when(mockPPOBService.fetchAddressesAndChoice(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(
          Some(CHROAddress("38", "line 1", None, "Telford", "UK", None, None, None)),
          Some(NewAddress("line 1", "line 2", None, None, None, None, None)),
          PPOBChoice(""))
        )

      submitWithAuthorisedUserRetrieval(controller.submit, FakeRequest().withFormUrlEncodedBody("whoops" -> "not good"), credID) {
        result =>
          status(result) mustBe 400
      }
    }

    "handle an invalid address type correctly" in new Setup {
      mockCheckStatus()
      when(mockCompanyRegistrationConnector.retrieveCompanyDetails(ArgumentMatchers.anyString())(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(validCompanyDetails)))

      submitWithAuthorisedUserRetrieval(controller.submit, submission("Bad"), credID) {
        result =>
          status(result) mustBe 400
      }
    }
  }

  "saveALFAddress" should {
    val validCompanyDetails = CompanyDetails("TestLTD", validCHROAddress, PPOB("RO", None), "UK")

    "return a PPOBChoice with a value of PPOB if the supplied ppob option is defined" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      val alfId = "123"

      when(mockAddressLookupFrontendService.getAddress(ArgumentMatchers.eq(alfId))(ArgumentMatchers.any[HeaderCarrier]))
        .thenReturn(validNewAddress)

      when(mockPPOBService.saveAddress(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.any[Option[NewAddress]]())(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(validCompanyDetails))

      when(mockBusinessRegConnector.updatePrePopAddress(ArgumentMatchers.any[String], ArgumentMatchers.any[Address])(ArgumentMatchers.any[HeaderCarrier]))
        .thenReturn(Future.successful(true))

      mockCheckStatus()

      showWithAuthorisedUser(controller.saveALFAddress(Some(alfId))) {
        result =>
          status(result) mustBe SEE_OTHER
          redirectLocation(result) mustBe Some(controllers.reg.routes.CompanyContactDetailsController.show.url)
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