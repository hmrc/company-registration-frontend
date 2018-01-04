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
import models._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.{CouldNotBuild, DashboardBuilt, DashboardService, RejectedIncorp}
import org.mockito.Mockito._
import org.mockito.Matchers.{any, eq => eqTo}
import uk.gov.hmrc.play.test.WithFakeApplication

import scala.concurrent.Future

class DashboardControllerSpec extends SCRSSpec with WithFakeApplication {

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

  "show" should {

    val regId = "12345"

    val dashboard = Dashboard(
      companyName = "testCompanyName",
      IncorpAndCTDashboard(
        "held", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), None, None, Some("ack-12345"), None
      ),
      ServiceDashboard("draft", None, None,ServiceLinks("", "", None, Some(""))),
      None
    )

    "return a 200 and render the dashboard view" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some(regId))
      when(mockDashboardService.buildDashboard(eqTo(regId))(any(), any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      AuthBuilder.showWithAuthorisedUser(controller.show, mockAuthConnector) {
        fRes =>
          val res = await(fRes)
          status(res) shouldBe 200
          contentAsString(res) should include("<title>Company registration overview</title>")
      }
    }

    "return a 200 and render the Registration unsuccessful view when the users incorporation is rejected" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some(regId))
      when(mockDashboardService.buildDashboard(eqTo(regId))(any(), any()))
        .thenReturn(Future.successful(RejectedIncorp))

      AuthBuilder.showWithAuthorisedUser(controller.show, mockAuthConnector) {
        fRes =>
          val res = await(fRes)
          status(res) shouldBe 200
          contentAsString(res) should include("<title>Company registration unsuccessful</title>")
      }
    }

    "return a 303 and redirect to HO1 if the dashboard case class could not be created, i.e. the ct document is in draft" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some(regId))
      when(mockDashboardService.buildDashboard(eqTo(regId))(any(), any()))
        .thenReturn(Future.successful(CouldNotBuild))

      AuthBuilder.showWithAuthorisedUser(controller.show, mockAuthConnector) {
        fRes =>
          val res = await(fRes)
          status(res) shouldBe 303
          redirectLocation(res) shouldBe Some("/register-your-company/basic-company-details")
      }
    }

    "return a 500 if build dashboard returns something unexpected" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some(regId))
      when(mockDashboardService.buildDashboard(eqTo(regId))(any(), any()))
        .thenReturn(Future.failed(new RuntimeException("")))

      AuthBuilder.showWithAuthorisedUser(controller.show, mockAuthConnector) {
        fRes =>
          val res = await(fRes)
          status(res) shouldBe 500

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
