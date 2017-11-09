/*
 * Copyright 2017 HM Revenue & Customs
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
import helpers.SCRSSpec
import models.external.OtherRegStatus
import org.joda.time.DateTime
import org.scalatest.mockito.MockitoSugar
import org.mockito._
import org.mockito.Mockito._
import play.api.http.Status._
import uk.gov.hmrc.play.http.ws.WSHttp
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.WithFakeApplication

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads}

class CancelRegistrationControllerSpec extends SCRSSpec with MockitoSugar with WithFakeApplication {
  val mockHttp = mock[WSHttp]

  class Setup {
    val controller = new CancelRegistrationController {
      override val authConnector = mockAuthConnector
      override val companyRegistrationConnector = mockCompanyRegistrationConnector
      override val keystoreConnector= mockKeystoreConnector
      override val payeConnector = mockServiceConnector
    }
  }

  val localDate = DateTime.now()
  val ackRef = "testAckRef"
  val payeStatus = OtherRegStatus("testStatus", Some(localDate), Some(ackRef),Some("foo"), None)
  val url = s"${mockServiceConnector.serviceBaseUrl + mockServiceConnector.serviceUri}/1/status"

  "show" should {

    "display the page if the user is logged in and has a registration id where the registration is not draft / rejected (same permissions as dashboard) & cancelURL exists " in new Setup {

      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks.retrieveCTRegistration(buildCorporationTaxModel(status = "foo"))

      when(mockHttp.GET[OtherRegStatus](Matchers.any[String])(Matchers.any[HttpReads[OtherRegStatus]](), Matchers.any[HeaderCarrier], Matchers.any[ExecutionContext]()))
        .thenReturn(Future.successful(payeStatus))

      when(mockServiceConnector.getStatus(Matchers.any[String])
      (Matchers.any[HeaderCarrier]))
        .thenReturn(Future.successful(SuccessfulResponse(OtherRegStatus("", None, None, Some("foo"), None))))
      when(mockServiceConnector.canStatusBeCancelled(Matchers.any[String])
      (Matchers.any[Function1[String, Future[StatusResponse]]]())(Matchers.any[HeaderCarrier])).thenReturn(
        Future.successful("foo"))

      AuthBuilder.showWithAuthorisedUser(controller.showCancelPAYE, mockAuthConnector) {
        result =>
          status(result) shouldBe OK
      }
    }

    "not display the page and should redirect if the user is logged in and does not have a registration id " in new Setup {

      mockKeystoreFetchAndGet("registrationID", None)

      AuthBuilder.showWithAuthorisedUser(controller.showCancelPAYE, mockAuthConnector) {
        result =>
          status(result) shouldBe SEE_OTHER
      }
    }
    "not display the page if user:logged in, has regID,regID is draft" in new Setup {

      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks.retrieveCTRegistration()
      AuthBuilder.showWithAuthorisedUser(controller.showCancelPAYE, mockAuthConnector) {
        result =>
          status(result) shouldBe SEE_OTHER
      }
    }
    "not display the page if user:logged in, has regID,regID is rejected" in new Setup {

      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks.retrieveCTRegistration(buildCorporationTaxModel(status="rejected"))
      AuthBuilder.showWithAuthorisedUser(controller.showCancelPAYE, mockAuthConnector) {
        result =>
          status(result) shouldBe SEE_OTHER
      }
    }
    "not display the page if user:logged in, has regID,regID is not draft / rejected, no cancelURL is returned" in new Setup {

      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks.retrieveCTRegistration(buildCorporationTaxModel(status = "foo"))
      when(mockServiceConnector.getStatus(Matchers.any[String])
      (Matchers.any[HeaderCarrier]))
        .thenReturn(Future.successful(SuccessfulResponse(OtherRegStatus("", None, None, None, None))))

      when(mockServiceConnector.canStatusBeCancelled(Matchers.any[String])
      (Matchers.any[Function1[String, Future[StatusResponse]]]())(Matchers.any[HeaderCarrier])).thenReturn(
        Future.failed(cantCancel))

      AuthBuilder.showWithAuthorisedUser(controller.showCancelPAYE, mockAuthConnector) {
        result =>
          status(result) shouldBe SEE_OTHER
      }
    }
  }

  "submit" should {
    "redirect to dashboard if cancelURL does not exist by the time of the submission (and user is logged in and has a registration id not = draft / rejected" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks.retrieveCTRegistration(buildCorporationTaxModel(status = "foo"))

      when(mockServiceConnector.getStatus(Matchers.any[String])
      (Matchers.any[HeaderCarrier]))
        .thenReturn(Future.successful(SuccessfulResponse(OtherRegStatus("", None, None, None, None))))


      when(mockServiceConnector.cancelReg(Matchers.any[String])(Matchers.any[Function1[String, Future[StatusResponse]]]())(Matchers.any[HeaderCarrier])).thenReturn(Future.successful(NotCancelled))

      AuthBuilder.submitWithAuthorisedUser(controller.submitCancelPAYE, mockAuthConnector, FakeRequest().withFormUrlEncodedBody("cancelPaye" -> "true")) {
        result =>
          status(result) shouldBe SEE_OTHER

      }


    }

    "redirect to dashboard if cancelURL does exist, the registration is cancelled successfully, user is logged in, registrationID not = draft / rejected" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks.retrieveCTRegistration(buildCorporationTaxModel(status = "foo"))

      when(mockServiceConnector.getStatus(Matchers.any[String])
      (Matchers.any[HeaderCarrier]))
        .thenReturn(Future.successful(SuccessfulResponse(OtherRegStatus("", None, None, Some("foo"), None))))


      when(mockServiceConnector.cancelReg(Matchers.any[String])(Matchers.any[Function1[String, Future[StatusResponse]]]())(Matchers.any[HeaderCarrier])).thenReturn(Future.successful(Cancelled))

      AuthBuilder.submitWithAuthorisedUser(controller.submitCancelPAYE, mockAuthConnector, FakeRequest().withFormUrlEncodedBody("cancelPaye" -> "true")) {
        result =>
          status(result) shouldBe SEE_OTHER

      }


    }
    "redirect to dashboard where user has reg id in draft state/cancelURL exists" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks.retrieveCTRegistration()

      when(mockServiceConnector.getStatus(Matchers.any[String])
      (Matchers.any[HeaderCarrier]))
      .thenReturn(Future.successful(SuccessfulResponse(OtherRegStatus("", None, None, Some("foo"), None))))


      when(mockServiceConnector.cancelReg(Matchers.any[String])(Matchers.any[Function1[String, Future[StatusResponse]]]())(Matchers.any[HeaderCarrier])).thenReturn(Future.successful(Cancelled))

      AuthBuilder.submitWithAuthorisedUser(controller.submitCancelPAYE, mockAuthConnector, FakeRequest().withFormUrlEncodedBody("cancelPaye" -> "true")) {
      result =>
      status(result) shouldBe SEE_OTHER

      }

      }


  }
}
