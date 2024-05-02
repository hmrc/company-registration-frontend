/*
 * Copyright 2023 HM Revenue & Customs
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

import java.time._

import builders.AuthBuilder
import config.AppConfig
import connectors._
import controllers.dashboard.CancelRegistrationController
import forms.CancelForm
import helpers.SCRSSpec
import mocks.ServiceConnectorMock
import models.external.OtherRegStatus
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status._
import play.api.i18n.{Lang, Messages}
import play.api.mvc.{AnyContent, MessagesControllerComponents, Request}
import play.api.test.FakeRequest
import uk.gov.hmrc.play.http.ws.WSHttp
import views.html.dashboard.{CancelPaye => CancelPayeView, CancelVat => CancelVatView}

import scala.concurrent.Future

class CancelRegistrationControllerSpec extends SCRSSpec with MockitoSugar with GuiceOneAppPerSuite with ServiceConnectorMock with AuthBuilder {

  val mockHttp = mock[WSHttp]
  lazy val cancelPayeView = app.injector.instanceOf[CancelPayeView]
  lazy val cancelVatView = app.injector.instanceOf[CancelVatView]
  lazy val mockMcc = app.injector.instanceOf[MessagesControllerComponents]
  lazy implicit val appConfig = app.injector.instanceOf[AppConfig]


  class Setup(r: Request[AnyContent]) {
    val controller = new CancelRegistrationController (
      mockPAYEConnector,
      mockVATConnector,
      mockKeystoreConnector,
      mockAuthConnector,
      mockCompanyRegistrationConnector,
      mockMcc,
      cancelPayeView,
      cancelVatView
    )(
      appConfig,
      ec
    )
    implicit val request = r
    implicit val messagesApi: Messages = controller.messagesApi.preferred(Seq(Lang("en")))
  }

  val localDate = LocalDateTime.now()
  val testRegID = "1"
  val ackRef = "testAckRef"
  val validStatus = OtherRegStatus("testStatus", Some(localDate), Some(ackRef), Some("foo"), None)
  val url = s"${mockServiceConnector.serviceBaseUrl + mockServiceConnector.serviceUri}/1/status"

  "showCancelService" should {

    "display the page if the user is logged in and has a registration id where the registration is not" +
      " draft / rejected (same permissions as dashboard) & cancelURL exists" in new Setup(r = FakeRequest()) {
      mockKeystoreFetchAndGet("registrationID", Some(testRegID))
      CTRegistrationConnectorMocks.retrieveCTRegistration(buildCorporationTaxModel(status = "foo"))
      getStatusMock(testRegID)(SuccessfulResponse(validStatus))
      canStatusBeCancelledMock(testRegID)(Future.successful("foo"))
      val form = CancelForm.form.fill(false)
      val view = cancelPayeView(form)

      val res = await(controller.showCancelService(mockServiceConnector, view))
      status(res) mustBe 200
    }

    "not display the page and should redirect if the user is logged in and does not have a registration id " in new Setup(r = FakeRequest()) {
      mockKeystoreFetchAndGet("registrationID", None)

      val res = await(controller.showCancelService(mockServiceConnector, cancelPayeView(CancelForm.form.fill(false))))
      status(res) mustBe SEE_OTHER
    }
    "not display the page if user:logged in, has regID,regID is draft" in new Setup(r = FakeRequest()) {
      mockKeystoreFetchAndGet("registrationID", Some(testRegID))
      CTRegistrationConnectorMocks.retrieveCTRegistration()

      val res = await(controller.showCancelService(mockServiceConnector, cancelPayeView(CancelForm.form.fill(false))))
      status(res) mustBe SEE_OTHER
    }
    "not display the page if user:logged in, has regID,regID is rejected" in new Setup(r = FakeRequest()) {
      mockKeystoreFetchAndGet("registrationID", Some(testRegID))
      CTRegistrationConnectorMocks.retrieveCTRegistration(buildCorporationTaxModel(status = "rejected"))

      val res = await(controller.showCancelService(mockServiceConnector, cancelPayeView(CancelForm.form.fill(false))))
      status(res) mustBe SEE_OTHER
    }
    "not display the page if user:logged in, has regID,regID is not draft / rejected, no cancelURL is returned" in new Setup(r = FakeRequest()) {
      mockKeystoreFetchAndGet("registrationID", Some(testRegID))
      CTRegistrationConnectorMocks.retrieveCTRegistration(buildCorporationTaxModel(status = "foo"))
      getStatusMock(testRegID)(SuccessfulResponse(OtherRegStatus("", None, None, None, None)))
      canStatusBeCancelledMock(testRegID)(Future.failed(cantCancel))

      val res = await(controller.showCancelService(mockServiceConnector, cancelPayeView(CancelForm.form.fill(false))))
      status(res) mustBe SEE_OTHER
    }
  }
  "showCancelPaye" should {
    "return 200 with authorised user & cancel url exists and registration is not draft / rejected" in new Setup(r = FakeRequest()) {
      mockKeystoreFetchAndGet("registrationID", Some(testRegID))
      CTRegistrationConnectorMocks.retrieveCTRegistration(buildCorporationTaxModel(status = "foo"))
      getStatusMock(testRegID)(SuccessfulResponse(validStatus))
      canStatusBeCancelledMock(testRegID)(Future.successful("foo"), mockPAYEConnector)
      mockAuthorisedUser(Future.successful({}))

      showWithAuthorisedUser(controller.showCancelPAYE) {
        result =>
          status(result) mustBe OK
      }
    }
  }
  "showCancelVAT" should {
    "return 200 with authorised user & cancel url exists and registration is not draft / rejected" in new Setup(r = FakeRequest()) {
      mockKeystoreFetchAndGet("registrationID", Some(testRegID))
      CTRegistrationConnectorMocks
        .retrieveCTRegistration(buildCorporationTaxModel(status = "foo"))
      getStatusMock(testRegID)(SuccessfulResponse(validStatus))
      canStatusBeCancelledMock(testRegID)(Future.successful("foo"), mockVATConnector)

      showWithAuthorisedUser(controller.showCancelVAT) {
        result =>
          status(result) mustBe OK
      }
    }
  }

  "submitCancelService" should {
    "redirect to dashboard if cancelURL does not exist by the time of the submission " +
      "(and user is logged in and has a registration id not = draft / rejected" in new Setup(r = FakeRequest().withFormUrlEncodedBody("cancelService" -> "true")) {
      mockKeystoreFetchAndGet("registrationID", Some(testRegID))
      CTRegistrationConnectorMocks.retrieveCTRegistration(buildCorporationTaxModel(status = "foo"))
      getStatusMock(testRegID)(SuccessfulResponse(OtherRegStatus("", None, None, None, None)))
      cancelRegMock(testRegID)(NotCancelled)

      val res = await(controller.submitCancelService(mockServiceConnector, _ => cancelVatView(CancelForm.form.fill(true))))
      status(res) mustBe SEE_OTHER
    }

    "redirect to dashboard if cancelURL does exist, the registration is cancelled successfully, " +
      "user is logged in, registrationID not = draft / rejected" in new Setup(r = FakeRequest().withFormUrlEncodedBody("cancelService" -> "true")) {
      mockKeystoreFetchAndGet("registrationID", Some(testRegID))
      CTRegistrationConnectorMocks.retrieveCTRegistration(buildCorporationTaxModel(status = "foo"))
      getStatusMock(testRegID)(SuccessfulResponse(OtherRegStatus("", None, None, Some("foo"), None)))
      cancelRegMock(testRegID)(Cancelled)

      val res = await(controller.submitCancelService(mockServiceConnector, _ => cancelVatView(CancelForm.form.fill(true))))
      status(res) mustBe SEE_OTHER
    }
    "redirect to dashboard where user has reg id in draft state/cancelURL exists" in new Setup(r = FakeRequest().withFormUrlEncodedBody("cancelService" -> "true")) {
      mockKeystoreFetchAndGet("registrationID", Some(testRegID))
      CTRegistrationConnectorMocks.retrieveCTRegistration(buildCorporationTaxModel(status = "foo"))
      getStatusMock(testRegID)(SuccessfulResponse(OtherRegStatus("", None, None, Some("foo"), None)))
      cancelRegMock(testRegID)(Cancelled)

      val res = await(controller.submitCancelService(mockServiceConnector, _ => cancelVatView(CancelForm.form.fill(true))))
      status(res) mustBe SEE_OTHER
    }
    "return redirect when all conditions are met to cancel a service, but user selects false" in new Setup(r = FakeRequest().withFormUrlEncodedBody("cancelService" -> "true")) {
      mockKeystoreFetchAndGet("registrationID", Some(testRegID))
      CTRegistrationConnectorMocks.retrieveCTRegistration(buildCorporationTaxModel(status = "foo"))
      getStatusMock(testRegID)(SuccessfulResponse(OtherRegStatus("", None, None, Some("foo"), None)))

      val res = await(controller.submitCancelService(mockServiceConnector, _ => cancelVatView(CancelForm.form.fill(false))))
      status(res) mustBe SEE_OTHER
    }
  }
  "submitCancelPaye" should {
    "return redirect when cancel is successful" in new Setup(r = FakeRequest()) {
      mockKeystoreFetchAndGet("registrationID", Some(testRegID))
      CTRegistrationConnectorMocks.retrieveCTRegistration(buildCorporationTaxModel(status = "foo"))
      getStatusMock(testRegID)(SuccessfulResponse(OtherRegStatus("", None, None, Some("foo"), None)), mockPAYEConnector)
      cancelRegMock(testRegID)(Cancelled, mockPAYEConnector)

      submitWithAuthorisedUser(controller.submitCancelPAYE, FakeRequest().withFormUrlEncodedBody("cancelService" -> "true")) {
        result =>
          status(result) mustBe SEE_OTHER
      }
    }
  }
  "submitCancelVAT" should {
    "return redirect when cancel is successful" in new Setup(r = FakeRequest()) {
      mockKeystoreFetchAndGet("registrationID", Some(testRegID))
      CTRegistrationConnectorMocks.retrieveCTRegistration(buildCorporationTaxModel(status = "foo"))
      getStatusMock(testRegID)(SuccessfulResponse(OtherRegStatus("", None, None, Some("foo"), None)), mockVATConnector)
      cancelRegMock(testRegID)(Cancelled, mockVATConnector)

      submitWithAuthorisedUser(controller.submitCancelVAT, FakeRequest().withFormUrlEncodedBody("cancelService" -> "true")) {
        result =>
          status(result) mustBe SEE_OTHER
      }
    }
  }
}