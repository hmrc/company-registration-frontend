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
import controllers.dashboard.DashboardController
import helpers.SCRSSpec
import models.auth.AuthDetails
import models.{Dashboard, IncorpAndCTDashboard, ServiceDashboard, ServiceLinks}
import org.mockito.Matchers.{any, eq => eqTo}
import org.mockito.Mockito._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.{CouldNotBuild, DashboardBuilt, DashboardService, RejectedIncorp}
import uk.gov.hmrc.auth.core.retrieve.{Credentials, ~}
import uk.gov.hmrc.auth.core.{AffinityGroup, Enrolments}
import uk.gov.hmrc.play.test.WithFakeApplication

import scala.concurrent.Future

class DashboardControllerSpec extends SCRSSpec with WithFakeApplication with AuthBuilder {

  val mockDashboardService = mock[DashboardService]


  class Setup {

    reset(mockDashboardService)

    val controller = new DashboardController {
      override val dashboardService = mockDashboardService
      override val keystoreConnector = mockKeystoreConnector
      override val authConnector = mockAuthConnector
      override val companyRegistrationConnector = mockCompanyRegistrationConnector
      override val companiesHouseURL = "testUrl"
    }
  }

  val payeThresholds = Map("weekly" -> 113, "monthly" -> 490, "annually" -> 5876)

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
        "held", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), None, None, Some("ack-12345"), None
      ),
      ServiceDashboard("draft", None, None,ServiceLinks("", "", None, Some("")), Some(payeThresholds)),
      None
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
            contentAsString(res) should include("<title>Company registration overview</title>")
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
            contentAsString(res) should include("<title>Company registration unsuccessful</title>")
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
          .thenReturn(Future.failed(new RuntimeException("")))
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
