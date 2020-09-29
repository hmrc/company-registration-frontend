/*
 * Copyright 2020 HM Revenue & Customs
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
import models.Email
import org.jsoup.Jsoup
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.MessagesApi
import play.api.libs.json.Json
import play.api.mvc.MessagesControllerComponents
import play.api.test.Helpers._
import services.MetricsService

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class CompanyContactDetailsSpec extends SCRSSpec with CompanyContactDetailsFixture with UserDetailsFixture with GuiceOneAppPerSuite
  with AuthBuilder {

  class Setup {
    val controller = new CompanyContactDetailsController {
      override lazy val controllerComponents = app.injector.instanceOf[MessagesControllerComponents]
      override val authConnector = mockAuthConnector
      override val s4LConnector = mockS4LConnector
      override val companyContactDetailsService = mockCompanyContactDetailsService
      override val metricsService = mock[MetricsService]
      override val compRegConnector = mockCompanyRegistrationConnector
      override val keystoreConnector= mockKeystoreConnector
      override val appConfig = mockAppConfig
      override lazy val messagesApi = app.injector.instanceOf[MessagesApi]
      override val scrsFeatureSwitches = mockSCRSFeatureSwitches
      override val ec = global
    }

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

  "show" should{
    "display the CompanyContactDetails page with the correct elements passed into it" in new Setup {
      CompanyContactDetailsServiceMocks.fetchContactDetails(validCompanyContactDetailsModel)
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CTRegistrationConnectorMocks.retrieveCTRegistration(ctDocFirstTimeThrough)
      when(mockCompanyRegistrationConnector.fetchCompanyName(any())(any())).thenReturn(Future.successful("testCompanyname1"))
      when(mockCompanyRegistrationConnector.retrieveEmail(any())(any())).thenReturn(Future.successful(Some(Email("verified@email","GG",true,true,true))))

      showWithAuthorisedUser(controller.show) {
        result =>
          val document = Jsoup.parse(contentAsString(result))

          document.title should include("Give us one or more ways to contact testCompanyname1")
          document.getElementById("main-heading").text() shouldBe "Give us one or more ways to contact testCompanyname1"
          intercept[Exception](document.getElementById("contactNameLabel").text())
          document.getElementById("contactEmailLabel").text() shouldBe "Email address"
          document.getElementById("contactEmail").attr("value") shouldBe "foo@bar.wibble"
          document.getElementById("contactDaytimePhoneLabel").text() shouldBe "Contact number Give a mobile number, if you have one"
          document.getElementById("contactMobileLabel").text() shouldBe "Other contact number"
          document.getElementById("next").attr("value") shouldBe "Save and continue"
          document.getElementById("helpMessage1").text() shouldBe "We will only do this if we have questions about the company's Corporation Tax."
      }
    }
  }
}