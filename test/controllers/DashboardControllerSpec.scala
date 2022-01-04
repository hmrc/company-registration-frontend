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
import controllers.dashboard.DashboardController
import controllers.reg.ControllerErrorHandler
import helpers.SCRSSpec
import models.{Dashboard, IncorpAndCTDashboard, ServiceDashboard, ServiceLinks}
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.{CouldNotBuild, DashboardBuilt, DashboardService, RejectedIncorp}
import uk.gov.hmrc.auth.core.retrieve.{Credentials, ~}
import uk.gov.hmrc.auth.core.{AffinityGroup, Enrolments}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import views.html.dashboard.{Dashboard => DashboardView}
import views.html.reg.{RegistrationUnsuccessful => RegistrationUnsuccessfulView}

class DashboardControllerSpec extends SCRSSpec with GuiceOneAppPerSuite with AuthBuilder {

  val mockDashboardService = mock[DashboardService]

  class Setup {

    reset(mockDashboardService)
    lazy val mockMcc = app.injector.instanceOf[MessagesControllerComponents]
    lazy val mockFrontendAppConfig = app.injector.instanceOf[FrontendAppConfig]
    lazy val mockControllerErrorHandler = app.injector.instanceOf[ControllerErrorHandler]
    lazy val mockDashboardView = app.injector.instanceOf[DashboardView]
    lazy val mockRegistrationUnsuccessfulView = app.injector.instanceOf[RegistrationUnsuccessfulView]

    val controller = new DashboardController(
      mockAuthConnector,
      mockKeystoreConnector,
      mockCompanyRegistrationConnector,
      mockDashboardService,
      mockMcc,
      mockControllerErrorHandler,
      mockDashboardView,
      mockRegistrationUnsuccessfulView
    )(
      mockFrontendAppConfig,
      global
    )
  }

  val payeThresholds = Map("weekly" -> 113, "monthly" -> 490, "annually" -> 5876)
  val vatThresholds = Map("yearly" -> 10000)
  val authDetails = new ~(
    new ~(
      new ~(
        new ~(
          Some(AffinityGroup.Organisation),
          Enrolments(Set())
        ), Some("test")
      ), Some("test")
    ), Credentials("test", "test")
  )

  "show" should {

    val regId = "12345"

    val dashboard = Dashboard(
      companyName = "testCompanyName",
      IncorpAndCTDashboard(
        "held", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), None, None, Some("ack-12345"), None, None
      ),
      ServiceDashboard("draft", None, None, ServiceLinks("", "", None, Some("")), Some(payeThresholds)),
      ServiceDashboard("submitted", None, Some("ack123"), ServiceLinks("vatURL", "otrsUrl", None, Some("foo")), Some(vatThresholds))
    )

    "return a 200 and render the dashboard view" when {
      "the dashboard has a held registration and the incorporation is not rejected" in new Setup {
        mockKeystoreFetchAndGet("registrationID", Some(regId))
        when(mockDashboardService.buildDashboard(eqTo(regId), any())(any()))
          .thenReturn(Future.successful(DashboardBuilt(dashboard)))
        when(mockDashboardService.checkForEmailMismatch(any(), any())(any(), any()))
          .thenReturn(Future.successful(true))

        showWithAuthorisedUserRetrieval(controller.show, authDetails) {
          fRes =>
            val res = await(fRes)
            status(res) shouldBe 200

            val document: Document = Jsoup.parse(contentAsString(res))
            document.title should include("Company registration overview")
        }

        verify(mockDashboardService, times(1)).checkForEmailMismatch(any(), any())(any(), any())
      }
    }

    "return a 200 and render the Registration unsuccessful view" when {
      "the users incorporation is rejected" in new Setup {
        mockKeystoreFetchAndGet("registrationID", Some(regId))
        when(mockDashboardService.buildDashboard(eqTo(regId), any())(any()))
          .thenReturn(Future.successful(RejectedIncorp))
        when(mockDashboardService.checkForEmailMismatch(any(), any())(any(), any()))
          .thenReturn(Future.successful(true))

        showWithAuthorisedUserRetrieval(controller.show, authDetails) {
          fRes =>
            val res = await(fRes)
            status(res) shouldBe 200

            val document: Document = Jsoup.parse(contentAsString(res))
            document.title should include("Your company registration was unsuccessful")
        }

        verify(mockDashboardService, times(1)).checkForEmailMismatch(any(), any())(any(), any())
      }
    }

    "return a 303 and redirect to HO1" when {
      "the dashboard case class could not be created, i.e. the ct document is in draft" in new Setup {
        mockKeystoreFetchAndGet("registrationID", Some(regId))
        when(mockDashboardService.buildDashboard(eqTo(regId), any())(any()))
          .thenReturn(Future.successful(CouldNotBuild))
        when(mockDashboardService.checkForEmailMismatch(any(), any())(any(), any()))
          .thenReturn(Future.successful(true))

        showWithAuthorisedUserRetrieval(controller.show, authDetails) {
          fRes =>
            val res = await(fRes)
            status(res) shouldBe 303
            redirectLocation(res) shouldBe Some("/register-your-company/basic-company-details")
        }

        verify(mockDashboardService, times(1)).checkForEmailMismatch(any(), any())(any(), any())
      }
    }

    "return a 500" when {
      "building the dashboard returns something unexpected" in new Setup {
        mockKeystoreFetchAndGet("registrationID", Some(regId))
        when(mockDashboardService.buildDashboard(eqTo(regId), any())(any()))
          .thenReturn(Future.failed(new Exception("")))
        when(mockDashboardService.checkForEmailMismatch(any(), any())(any(), any()))
          .thenReturn(Future.successful(true))

        showWithAuthorisedUserRetrieval(controller.show, authDetails) {
          fRes =>
            val res = await(fRes)
            status(res) shouldBe 500
        }

        verify(mockDashboardService, times(1)).checkForEmailMismatch(any(), any())(any(), any())
      }
    }
  }

  "submit" should {

    "redirect to post sign in" in new Setup {
      val res = await(controller.submit(FakeRequest()))
      status(res) shouldBe 303
    }
  }
}