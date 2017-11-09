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

package views

import _root_.helpers.SCRSSpec
import builders.AuthBuilder
import controllers.reg.CompanyContactDetailsController
import fixtures.{CompanyContactDetailsFixture, UserDetailsFixture}
import models.UserDetailsModel
import org.jsoup.Jsoup
import org.mockito.Matchers
import org.mockito.Mockito._
import play.api.test.Helpers._
import services.MetricsService
import uk.gov.hmrc.play.test.WithFakeApplication

import scala.concurrent.{ExecutionContext, Future}

class CompanyContactDetailsSpec extends SCRSSpec with CompanyContactDetailsFixture with UserDetailsFixture
  with WithFakeApplication {

  class Setup {
    val controller = new CompanyContactDetailsController {
      override val authConnector = mockAuthConnector
      override val s4LConnector = mockS4LConnector
      override val companyContactDetailsService = mockCompanyContactDetailsService
      override val metricsService = mock[MetricsService]
      override val companyRegistrationConnector = mockCompanyRegistrationConnector
      override val keystoreConnector= mockKeystoreConnector


    }
  }

  "show" should{
    "display the CompanyContactDetails page with the correct elements" in new Setup {
      CompanyContactDetailsServiceMocks.fetchContactDetails(validCompanyContactDetailsModel)
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks.retrieveCTRegistration()
      when(mockAuthConnector.getUserDetails[UserDetailsModel](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any[ExecutionContext]()))
        .thenReturn(Future.successful(userDetailsModel))

      AuthBuilder.showWithAuthorisedUser(controller.show, mockAuthConnector){
        result =>
          val document = Jsoup.parse(contentAsString(result))

          document.title() shouldBe "Who should we contact about the company's Corporation Tax?"
          document.getElementById("main-heading").text() shouldBe "Who should we contact about the company's Corporation Tax?"
          document.getElementById("contactNameLabel").text() shouldBe "Contact name"
          document.getElementById("contactEmailLabel").text() shouldBe "Email address"
          document.getElementById("contactDaytimePhoneLabel").text() shouldBe "Contact number"
          document.getElementById("contactMobileLabel").text() shouldBe "Other contact number"
          document.getElementById("next").attr("value") shouldBe "Save and continue"
          document.getElementById("helpMessage1").text() shouldBe "Provide at least one of the following contact details."
      }
    }
  }
}
