/*
 * Copyright 2018 HM Revenue & Customs
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
import connectors._
import controllers.dashboard.CancelRegistrationController
import forms.CancelForm
import helpers.SCRSSpec
import mocks.ServiceConnectorMock
import models.external.OtherRegStatus
import org.joda.time.DateTime
import org.scalatest.mockito.MockitoSugar
import play.api.http.Status._
import play.api.i18n.Messages
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.play.http.ws.WSHttp
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.WithFakeApplication
import scala.concurrent.Future

class CancelRegistrationControllerSpec extends SCRSSpec with MockitoSugar with WithFakeApplication with ServiceConnectorMock {
  val mockHttp = mock[WSHttp]

  class Setup(r:Request[AnyContent]) {
    val controller = new CancelRegistrationController {
      override val authConnector = mockAuthConnector
      override val companyRegistrationConnector = mockCompanyRegistrationConnector
      override val keystoreConnector= mockKeystoreConnector
      override val payeConnector = mockServiceConnector
      override val vatConnector = mockServiceConnector
    }
    implicit val request = r
    implicit val messagesApi: Messages = mock[Messages]
  }

  val localDate = DateTime.now()
  val ackRef = "testAckRef"
  val validStatus = OtherRegStatus("testStatus", Some(localDate), Some(ackRef),Some("foo"), None)
  val url = s"${mockServiceConnector.serviceBaseUrl + mockServiceConnector.serviceUri}/1/status"

  "showCancelService" should {

    "display the page if the user is logged in and has a registration id where the registration is not draft / rejected (same permissions as dashboard) & cancelURL exists" in new Setup(r = FakeRequest()) {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks.retrieveCTRegistration(buildCorporationTaxModel(status = "foo"))
      getStatusMock(SuccessfulResponse(validStatus))
      canStatusBeCancelledMock(Future.successful("foo"))

      val res = await(controller.showCancelService(mockServiceConnector,views.html.dashboard.CancelPaye(CancelForm.form.fill(false))))
      status(res) shouldBe 200
    }

    "not display the page and should redirect if the user is logged in and does not have a registration id " in new Setup(r = FakeRequest()) {
      mockKeystoreFetchAndGet("registrationID", None)

      val res = await(controller.showCancelService(mockServiceConnector,views.html.dashboard.CancelPaye(CancelForm.form.fill(false))))
      status(res) shouldBe SEE_OTHER
    }
    "not display the page if user:logged in, has regID,regID is draft" in new Setup(r = FakeRequest()) {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks.retrieveCTRegistration()

      val res = await(controller.showCancelService(mockServiceConnector,views.html.dashboard.CancelPaye(CancelForm.form.fill(false))))
      status(res) shouldBe SEE_OTHER
    }
    "not display the page if user:logged in, has regID,regID is rejected" in new Setup(r = FakeRequest()) {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks.retrieveCTRegistration(buildCorporationTaxModel(status="rejected"))
      val res = await(controller.showCancelService(mockServiceConnector,views.html.dashboard.CancelPaye(CancelForm.form.fill(false))))
      status(res) shouldBe SEE_OTHER
    }
    "not display the page if user:logged in, has regID,regID is not draft / rejected, no cancelURL is returned" in new Setup(r = FakeRequest()) {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks.retrieveCTRegistration(buildCorporationTaxModel(status = "foo"))
      getStatusMock(SuccessfulResponse(OtherRegStatus("", None, None, None, None)))
      canStatusBeCancelledMock(Future.failed(cantCancel))

      val res = await(controller.showCancelService(mockServiceConnector,views.html.dashboard.CancelPaye(CancelForm.form.fill(false))))
      status(res) shouldBe SEE_OTHER
    }
  }
  "showCancelPaye" should {
    "return 200 with authorised user & cancel url exists and registration is not draft / rejected" in new Setup(r = FakeRequest()) {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks
        .retrieveCTRegistration(buildCorporationTaxModel(status = "foo"))
      getStatusMock(SuccessfulResponse(validStatus))
      canStatusBeCancelledMock(Future.successful("foo"))

    AuthBuilder.showWithAuthorisedUser(controller.showCancelPAYE, mockAuthConnector) {
    result =>
      status(result) shouldBe OK
  }
}
  }
  "showCancelVAT" should {
    "return 200 with authorised user & cancel url exists and registration is not draft / rejected" in new Setup(r = FakeRequest()) {
    mockKeystoreFetchAndGet("registrationID", Some("1"))
    CTRegistrationConnectorMocks
      .retrieveCTRegistration(buildCorporationTaxModel(status = "foo"))
   getStatusMock(SuccessfulResponse(validStatus))
    canStatusBeCancelledMock(Future.successful("foo"))

  AuthBuilder.showWithAuthorisedUser(controller.showCancelVAT, mockAuthConnector) {
     result =>
      status(result) shouldBe OK
  }
    }
  }

  "submitCancelService" should {
    "redirect to dashboard if cancelURL does not exist by the time of the submission (and user is logged in and has a registration id not = draft / rejected" in new Setup(r = FakeRequest().withFormUrlEncodedBody("cancelService" -> "true")) {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks.retrieveCTRegistration(buildCorporationTaxModel(status = "foo"))
      getStatusMock(SuccessfulResponse(OtherRegStatus("", None, None, None, None)))
      cancelRegMock(NotCancelled)

      val res = await(controller.submitCancelService(mockServiceConnector,_ => views.html.dashboard.CancelVat(CancelForm.form.fill(true))))
      status(res) shouldBe SEE_OTHER
    }

    "redirect to dashboard if cancelURL does exist, the registration is cancelled successfully, user is logged in, registrationID not = draft / rejected" in new Setup(r = FakeRequest().withFormUrlEncodedBody("cancelService" -> "true")) {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks.retrieveCTRegistration(buildCorporationTaxModel(status = "foo"))
      getStatusMock(SuccessfulResponse(OtherRegStatus("", None, None, Some("foo"), None)))
      cancelRegMock(Cancelled)

      val res = await(controller.submitCancelService(mockServiceConnector,_ => views.html.dashboard.CancelVat(CancelForm.form.fill(true))))
      status(res) shouldBe SEE_OTHER
    }
    "redirect to dashboard where user has reg id in draft state/cancelURL exists" in new Setup(r = FakeRequest().withFormUrlEncodedBody("cancelService" -> "true")) {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks.retrieveCTRegistration(buildCorporationTaxModel(status = "foo"))
      getStatusMock(SuccessfulResponse(OtherRegStatus("", None, None, Some("foo"), None)))
      cancelRegMock(Cancelled)

      val res = await(controller.submitCancelService(mockServiceConnector,_ => views.html.dashboard.CancelVat(CancelForm.form.fill(true))))
      status(res) shouldBe SEE_OTHER
      }
    "return bad request when form is incorrect" in new Setup(r = FakeRequest().withFormUrlEncodedBody("cancelService" -> "foobarwizzandbang")) {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks.retrieveCTRegistration(buildCorporationTaxModel(status = "foo"))
      getStatusMock(SuccessfulResponse(OtherRegStatus("", None, None, Some("foo"), None)))

      val res = await(controller.submitCancelService(mockServiceConnector, _ => views.html.dashboard.CancelVat(CancelForm.form.bind(Map("cancelService" -> "foobarwizzandbang")))))
      status(res) shouldBe BAD_REQUEST
    }
    "return redirect when all conditions are met to cancel a service, but user selects false" in new Setup(r = FakeRequest().withFormUrlEncodedBody("cancelService" -> "true")) {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks.retrieveCTRegistration(buildCorporationTaxModel(status = "foo"))
      getStatusMock(SuccessfulResponse(OtherRegStatus("", None, None, Some("foo"), None)))

      val res = await(controller.submitCancelService(mockServiceConnector, _ => views.html.dashboard.CancelVat(CancelForm.form.fill(false))))
      status(res) shouldBe SEE_OTHER
    }
  }
  "submitCancelPaye" should {
    "return redirect when cancel is successful" in new Setup(r = FakeRequest()) {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks.retrieveCTRegistration(buildCorporationTaxModel(status = "foo"))
      getStatusMock(SuccessfulResponse(OtherRegStatus("", None, None, Some("foo"), None)))
      cancelRegMock(Cancelled)

      AuthBuilder.submitWithAuthorisedUser(controller.submitCancelPAYE, mockAuthConnector, FakeRequest().withFormUrlEncodedBody("cancelService" -> "true")) {
        result =>
          status(result) shouldBe SEE_OTHER
      }
    }
  }
  "submitCancelVAT" should {
    "return redirect when cancel is successful"  in new Setup(r = FakeRequest()) {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks.retrieveCTRegistration(buildCorporationTaxModel(status = "foo"))
      getStatusMock(SuccessfulResponse(OtherRegStatus("", None, None, Some("foo"), None)))
      cancelRegMock(Cancelled)

      AuthBuilder.submitWithAuthorisedUser(controller.submitCancelVAT, mockAuthConnector, FakeRequest().withFormUrlEncodedBody("cancelService" -> "true")) {
        result =>
          status(result) shouldBe SEE_OTHER
      }
    }
    }
}
