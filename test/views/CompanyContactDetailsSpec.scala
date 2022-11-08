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

package views

import _root_.helpers.SCRSSpec
import builders.AuthBuilder
import controllers.reg.{CompanyContactDetailsController, ControllerErrorHandler}
import fixtures.{CompanyContactDetailsFixture, UserDetailsFixture}
import models.Email
import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.Json
import play.api.mvc.MessagesControllerComponents
import play.api.test.Helpers._
import services.MetricsService
import views.html.reg.{CompanyContactDetails => CompanyContactDetailsView}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CompanyContactDetailsSpec extends SCRSSpec with CompanyContactDetailsFixture with UserDetailsFixture with GuiceOneAppPerSuite
  with AuthBuilder {
  lazy val mockControllerComponents = app.injector.instanceOf[MessagesControllerComponents]
  lazy val mockControllerErrorHandler = app.injector.instanceOf[ControllerErrorHandler]
  lazy val mockCompanyContactDetailsView = app.injector.instanceOf[CompanyContactDetailsView]

  class Setup {

    object Selectors extends BaseSelectors

    val controller = new CompanyContactDetailsController (
      mockAuthConnector,
      mockS4LConnector,
      mock[MetricsService],
      mockCompanyRegistrationConnector,
      mockKeystoreConnector,
      mockCompanyContactDetailsService,
      mockSCRSFeatureSwitches,
      mockControllerComponents,
      mockControllerErrorHandler,
      mockCompanyContactDetailsView
    )
    (
      mockAppConfig,
      global
      )


    val ctDocFirstTimeThrough =
      Json.parse(
        s"""
           |{
           |    "OID" : "123456789",
           |    "registrationID" : "1",
           |    "status" : "draft",
           |    "formCreationTimestamp" : "2016-10-25T12:20:45Z",
           |    "language" : "en",
           |    "verifiedEmail" : {
           |        "address" : "user@test.com",
           |        "type" : "GG",
           |        "link-sent" : true,
           |        "verified" : true,
           |        "return-link-email-sent" : false
           |    }
           |}""".stripMargin)
  }

  "show" should {
    "display the CompanyContactDetails page with the correct elements passed into it" in new Setup {
      CompanyContactDetailsServiceMocks.fetchContactDetails(validCompanyContactDetailsModel)
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks.retrieveCTRegistration(ctDocFirstTimeThrough)
      when(mockCompanyRegistrationConnector.fetchCompanyName(any())(any(), any())).thenReturn(Future.successful("testCompanyname1"))
      when(mockCompanyRegistrationConnector.retrieveEmail(any())(any(), any())).thenReturn(Future.successful(Some(Email("verified@email", "GG", true, true, true))))

      showWithAuthorisedUser(controller.show) {
        result =>
          val document = Jsoup.parse(contentAsString(result))

          document.title must include("Give us one or more ways to contact testCompanyname1")
          document.select(Selectors.h1).text() mustBe "Give us one or more ways to contact testCompanyname1"
          intercept[Exception](document.getElementById("contactNameLabel").text())
          document.getElementById("continue").text() mustBe "Save and continue"
      }
    }
  }
}