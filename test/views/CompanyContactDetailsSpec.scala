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

package views

import _root_.helpers.SCRSSpec
import builders.AuthBuilder
import controllers.reg.CompanyContactDetailsController
import fixtures.{CompanyContactDetailsFixture, UserDetailsFixture}
import org.jsoup.Jsoup
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import play.api.test.Helpers._
import services.MetricsService
import uk.gov.hmrc.auth.core.retrieve.{Name, ~}
import uk.gov.hmrc.play.test.WithFakeApplication

import scala.concurrent.Future

class CompanyContactDetailsSpec extends SCRSSpec with CompanyContactDetailsFixture with UserDetailsFixture
  with WithFakeApplication with AuthBuilder {

  class Setup {
    val controller = new CompanyContactDetailsController {
      override val authConnector = mockAuthConnector
      override val s4LConnector = mockS4LConnector
      override val companyContactDetailsService = mockCompanyContactDetailsService
      override val metricsService = mock[MetricsService]
      override val companyRegistrationConnector = mockCompanyRegistrationConnector
      override val keystoreConnector= mockKeystoreConnector
      override val appConfig = mockAppConfig
    }
  }

  "show" should{
    "display the CompanyContactDetails page with the correct elements" in new Setup {
      CompanyContactDetailsServiceMocks.fetchContactDetails(validCompanyContactDetailsModel)
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks.retrieveCTRegistration()
      when(mockCompanyRegistrationConnector.fetchCompanyName(any())(any())).thenReturn(Future.successful("testCompanyname1"))

      showWithAuthorisedUserRetrieval(controller.show, new ~(Name(Some("first"), Some("last")), Some("email"))) {
        result =>
          val document = Jsoup.parse(contentAsString(result))

          document.title() shouldBe "Give us one or more ways to contact testCompanyname1"
          document.getElementById("main-heading").text() shouldBe "Give us one or more ways to contact testCompanyname1"
          intercept[Exception](document.getElementById("contactNameLabel").text())
          document.getElementById("contactEmailLabel").text() shouldBe "Email address"
          document.getElementById("contactDaytimePhoneLabel").text() shouldBe "Contact number Give a mobile number, if you have one"
          document.getElementById("contactMobileLabel").text() shouldBe "Other contact number"
          document.getElementById("next").attr("value") shouldBe "Save and continue"
          document.getElementById("helpMessage1").text() shouldBe "We will only do this if we have questions about the company's Corporation Tax."
      }
    }
  }
}
