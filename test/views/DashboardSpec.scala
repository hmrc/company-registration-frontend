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
import controllers.reg.DashboardController
import models._
import org.jsoup.Jsoup
import org.mockito.Mockito._
import org.mockito.Matchers
import play.api.test.Helpers._
import services.{DashboardBuilt, DashboardService}
import org.mockito.Matchers.any
import play.twirl.api.Html

import scala.concurrent.Future

class DashboardSpec extends SCRSSpec {

  val mockDashboardService = mock[DashboardService]

  class Setup {
    val controller = new DashboardController {
      override val authConnector = mockAuthConnector
      override val keystoreConnector = mockKeystoreConnector
      override val dashboardService = mockDashboardService
      override val companyRegistrationConnector = mockCompanyRegistrationConnector
      override val companiesHouseURL = "testUrl"
      }
  }

  val regId = "reg-12345"

  "DashboardController.show" should {

    "make sure that the dashboard has the correct elements when the registration status is held" in new Setup {

      val dashboard = Dashboard(
        IncorpAndCTDashboard(
          "held", Some("10 October 2017"), Some("trans-12345"), Some("pay-12345"), None, None, Some("ack-12345"), None
        ),
        PAYEDashboard("notStarted", None, None, PAYELinks("payeURL", "otrsUrl", None, Some("foo"))),
        "testCompanyName"
      )

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(Matchers.any())(Matchers.any(), any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      AuthBuilder.showWithAuthorisedUser(controller.show, mockAuthConnector) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "Your business registration overview"

          Map(
            "incorpStatusText" -> "Pending",
            "incorpSubmissionDate" -> "10 October 2017",
            "incorpTransID" -> "trans-12345",
            "incorpPaymentReference" -> "pay-12345",
            "incorpSubmittedText" -> "If your application is successful we'll send you an email with the company registration number and certificate within 2 working days of the submission date.",
            "ctStatusText" -> "Pending",
            "ackRef" -> "ack-12345",
            "ctPendingText" -> "We've received your application but can't process it until we've set up the limited company."
          ) foreach { case (element, message) =>
            document.getElementById(element).text() shouldBe message
          }
      }
    }

    "make sure that the dashboard has the correct elements when the registration status is submitted" in new Setup {

      val dashboard = Dashboard(
        IncorpAndCTDashboard(
          "submitted", Some("10 October 2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11 October 2017"), Some("ack-12345"), None
        ),
        PAYEDashboard("notStarted", None, None, PAYELinks("payeURL", "otrsUrl", None, Some("foo"))),
        "testCompanyName"
      )

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(Matchers.any())(Matchers.any(), any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      AuthBuilder.showWithAuthorisedUser(controller.show, mockAuthConnector) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "Your business registration overview"

          Map(
            "incorpStatusText" -> "Registered",
            "crn" -> "crn123",
            "ctStatusText" -> "Pending",
            "submittedAckRef" -> "ack-12345",
            "CTifSuccess" -> "If your application is successful we'll send you:"
          ) foreach { case (element, message) =>
            document.getElementById(element).text() shouldBe message
          }
      }
    }

    "make sure that the dashboard has the correct elements when ETMP has approved the CT submission" in new Setup {

      val dashboard = Dashboard(
        IncorpAndCTDashboard(
          "acknowledged", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), None
        ),
        PAYEDashboard("notStarted", None, None, PAYELinks("payeURL", "otrsUrl", None, Some("foo"))),
        "testCompanyName"
      )

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(Matchers.any())(Matchers.any(), any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      AuthBuilder.showWithAuthorisedUser(controller.show, mockAuthConnector) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "Your business registration overview"

          Map(
            "incorpStatusText" -> "Registered",
            "crn" -> "crn123",
            "ctStatusText" -> "Registered"
          ) foreach { case (element, message) =>
            document.getElementById(element).text() shouldBe message
          }
      }
    }

    "make sure that the dashboard has the correct elements when ETMP has rejected the CT submission" in new Setup {

      val dashboard = Dashboard(
        IncorpAndCTDashboard(
          "submitted", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), Some("06")
        ),
        PAYEDashboard("notStarted", None, None, PAYELinks("payeURL", "otrsUrl", None, Some("foo"))),
        "testCompanyName"
      )

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(Matchers.any())(Matchers.any(), any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      AuthBuilder.showWithAuthorisedUser(controller.show, mockAuthConnector) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "Your business registration overview"

          Map(
            "incorpStatusText" -> "Registered",
            "crn" -> "crn123",
            "ctStatusText" -> "Unsuccessful"
          ) foreach { case (element, message) =>
            document.getElementById(element).text() shouldBe message
          }
      }
    }


    "make sure that the dashboard has the correct elements when ETMP has accepted the CT submission" in new Setup {

      val dashboard = Dashboard(
        IncorpAndCTDashboard(
          "acknowledged", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), Some("04")
        ),
        PAYEDashboard("notStarted", None, None, PAYELinks("payeURL", "otrsUrl", None, Some("foo"))),
        "testCompanyName"
      )

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(Matchers.any())(Matchers.any(), any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      AuthBuilder.showWithAuthorisedUser(controller.show, mockAuthConnector) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "Your business registration overview"

          Map(
            "incorpStatusText" -> "Registered",
            "crn" -> "crn123",
            "ctStatusText" -> "Registered"
          ) foreach { case (element, message) =>
            document.getElementById(element).text() shouldBe message
          }
      }
    }

    "make sure that the dashboard has the correct elements for a PAYE not eligible status" in new Setup {

      val dashboard = Dashboard(
        IncorpAndCTDashboard(
          "acknowledged", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), Some("04")
        ),
        PAYEDashboard("notEligible", None, None, PAYELinks("payeURL", "otrsUrl", None, Some("foo"))),
        "testCompanyName"
      )

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(Matchers.any())(Matchers.any(), any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      AuthBuilder.showWithAuthorisedUser(controller.show, mockAuthConnector) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "Your business registration overview"
          document.getElementById("payeStatusText").text() shouldBe "Not eligible"
      }
    }

    "make sure that the dashboard has the correct elements for a PAYE unavailable status" in new Setup {

      val dashboard = Dashboard(
        IncorpAndCTDashboard(
          "acknowledged", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), Some("04")
        ),
        PAYEDashboard(PAYEStatuses.UNAVAILABLE, None, None, PAYELinks("payeURL", "otrsUrl", None, Some("foo"))),
        "testCompanyName"
      )

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(Matchers.any())(Matchers.any(), any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      AuthBuilder.showWithAuthorisedUser(controller.show, mockAuthConnector) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "Your business registration overview"
          document.getElementById("payeStatusText").text() shouldBe "Temporarily unavailable"
      }
    }

    "make sure that the dashboard has the correct elements for a PAYE not started status" in new Setup {

      val dashboard = Dashboard(
        IncorpAndCTDashboard(
          "acknowledged", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), Some("04")
        ),
        PAYEDashboard("notStarted", None, None, PAYELinks("payeURL", "otrsUrl", None, Some("foo"))),
        "testCompanyName"
      )

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(Matchers.any())(Matchers.any(), any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      AuthBuilder.showWithAuthorisedUser(controller.show, mockAuthConnector) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "Your business registration overview"
          document.getElementById("payeStatusText").text() shouldBe "Register"
          document.getElementById("payeRegUrl").attr("href") shouldBe dashboard.payeDash.links.payeRegUrl
      }
    }

    "make sure that the dashboard has the correct elements for a PAYE draft status" in new Setup {

      val dashboard = Dashboard(
        IncorpAndCTDashboard(
          "acknowledged", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), Some("04")
        ),
        PAYEDashboard("draft", None, None, PAYELinks("payeURL", "otrsUrl", None, Some("foo"))),
        "testCompanyName"
      )

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(Matchers.any())(Matchers.any(), any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      AuthBuilder.showWithAuthorisedUser(controller.show, mockAuthConnector) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "Your business registration overview"
          document.getElementById("payeStatusText").text() shouldBe "Incomplete"

      }
    }

    "make sure that the dashboard has the correct elements for a PAYE held status" in new Setup {

      val dashboard = Dashboard(
        IncorpAndCTDashboard(
          "acknowledged", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), Some("04")
        ),
        PAYEDashboard("held", None, Some("ABCD12345678901"), PAYELinks("payeURL", "otrsUrl", None, Some("foo"))),
        "testCompanyName"
      )

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(Matchers.any())(Matchers.any(), any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      AuthBuilder.showWithAuthorisedUser(controller.show, mockAuthConnector) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "Your business registration overview"

          Map(
            "payeStatusText" -> "Pending",
            "PAYERef" -> "ABCD12345678901"
          ) foreach { case (element, message) =>
            document.getElementById(element).text() shouldBe message
          }
      }
    }

    "make sure that the dashboard has the correct elements for a PAYE submitted status" in new Setup {

      val dashboard = Dashboard(
        IncorpAndCTDashboard(
          "acknowledged", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), Some("04")
        ),
        PAYEDashboard("submitted", Some("15 May 2017"), Some("ABCD12345678901"), PAYELinks("payeURL", "otrsUrl", None, Some("foo"))),
        "testCompanyName"
      )

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(Matchers.any())(Matchers.any(), any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      AuthBuilder.showWithAuthorisedUser(controller.show, mockAuthConnector) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "Your business registration overview"

          Map(
            "payeStatusText" -> "Pending",
            "PAYERef" -> "ABCD12345678901",
            "PAYEDate" -> "15 May 2017"
          ) foreach { case (element, message) =>
            document.getElementById(element).text() shouldBe message
          }
      }
    }

    "make sure that the dashboard has the correct elements for a PAYE acknowledged status" in new Setup {

      val dashboard = Dashboard(
        IncorpAndCTDashboard(
          "acknowledged", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), Some("04")
        ),
        PAYEDashboard("acknowledged", None, None, PAYELinks("payeURL", "otrsUrl", None, Some("foo"))),
        "testCompanyName"
      )

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(Matchers.any())(Matchers.any(), any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      AuthBuilder.showWithAuthorisedUser(controller.show, mockAuthConnector) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "Your business registration overview"
          document.getElementById("payeStatusText").text() shouldBe "Registered"


      }
    }

    "make sure that the dashboard has the correct elements for a PAYE invalid status" in new Setup {

      val dashboard = Dashboard(
        IncorpAndCTDashboard(
          "acknowledged", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), Some("04")
        ),
        PAYEDashboard("invalid", None, None, PAYELinks("payeURL", "otrsUrl", None, Some("foo"))),
        "testCompanyName"
      )

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(Matchers.any())(Matchers.any(), any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      AuthBuilder.showWithAuthorisedUser(controller.show, mockAuthConnector) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "Your business registration overview"
          document.getElementById("payeStatusText").text() shouldBe "Incomplete"


      }
    }

    "make sure that the dashboard has the correct elements for a PAYE rejected status" in new Setup {

      val dashboard = Dashboard(
        IncorpAndCTDashboard(
          "acknowledged", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), Some("04")
        ),
        PAYEDashboard("rejected", None, None, PAYELinks("payeURL", "otrsUrl", Some("bar"), None)),
        "testCompanyName"
      )

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(Matchers.any())(Matchers.any(), any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      AuthBuilder.showWithAuthorisedUser(controller.show, mockAuthConnector) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "Your business registration overview"
          document.getElementById("payeStatusText").text() shouldBe "Unsuccessful"
          document.getElementById("PAYERej").attr("href") shouldBe "bar"
      }
    }

    "make sure that the dashboard has the correct elements when the VAT section is displayed and CT status is acknowledgeed" in new Setup {
      val dashboard = Dashboard(
        IncorpAndCTDashboard(
          "acknowledged", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), None
        ),
        PAYEDashboard("notStarted", None, None, PAYELinks("payeURL", "otrsUrl", None, None)),
        "testCompanyName",
        hasVATCred = false

      )

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(Matchers.any())(Matchers.any(), any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      AuthBuilder.showWithAuthorisedUser(controller.show, mockAuthConnector) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "Your business registration overview"
          document.getElementById("vatStatusText").text() shouldBe "Register using another HMRC service"
          document.getElementById("vatUrl").attr("href") shouldBe "https://online.hmrc.gov.uk/registration/newbusiness/introduction"
      }
    }

    "make sure that the dashboard shows the VAT section when the CT submission status is rejected (06 ETMP status)" in new Setup {
      val dashboard = Dashboard(
        IncorpAndCTDashboard(
          "submitted", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), Some("06")
        ),
        PAYEDashboard("notStarted", None, None, PAYELinks("payeURL", "otrsUrl", None, None)),
        "testCompanyName",
        hasVATCred = false
      )

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(Matchers.any())(Matchers.any(), any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      AuthBuilder.showWithAuthorisedUser(controller.show, mockAuthConnector) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "Your business registration overview"
          document.getElementById("vatStatusText").text() shouldBe "Register using another HMRC service"
          document.getElementById("vatUrl").attr("href") shouldBe "https://online.hmrc.gov.uk/registration/newbusiness/introduction"
      }
    }

    "make sure that the dashboard shows the VAT section when the CT submission status is submitted" in new Setup {
      val dashboard = Dashboard(
        IncorpAndCTDashboard(
          "submitted", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), None
        ),
        PAYEDashboard("notStarted", None, None, PAYELinks("payeURL", "otrsUrl", None, None)),
        "testCompanyName",
        hasVATCred = false
      )

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(Matchers.any())(Matchers.any(), any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      AuthBuilder.showWithAuthorisedUser(controller.show, mockAuthConnector) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "Your business registration overview"
          document.getElementById("vatStatusText").text() shouldBe "Register using another HMRC service"
          document.getElementById("vatUrl").attr("href") shouldBe "https://online.hmrc.gov.uk/registration/newbusiness/introduction"
      }
    }

    "make sure that the dashboard does not display the VAT block if the user has a VAT cred" in new Setup {
      val dashboard = Dashboard(
        IncorpAndCTDashboard(
          "acknowledged", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), None
        ),
        PAYEDashboard("notStarted", None, None, PAYELinks("payeURL", "otrsUrl", None, None)),
        "testCompanyName",
        hasVATCred = true
      )

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(Matchers.any())(Matchers.any(), any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      AuthBuilder.showWithAuthorisedUser(controller.show, mockAuthConnector) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "Your business registration overview"
          document.getElementById("vatStatusText") shouldBe null
          document.getElementById("vatUrl") shouldBe null
      }
    }

    "make sure that the dashboard does not display the VAT block if the company is not incorporated yet" in new Setup {
      val dashboard = Dashboard(
        IncorpAndCTDashboard(
          "held", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), None
        ),
        PAYEDashboard("notStarted", None, None, PAYELinks("payeURL", "otrsUrl", None, None)),
        "testCompanyName",
        hasVATCred = false
      )

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(Matchers.any())(Matchers.any(), any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      AuthBuilder.showWithAuthorisedUser(controller.show, mockAuthConnector) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "Your business registration overview"
          document.getElementById("vatStatusText") shouldBe null
          document.getElementById("vatUrl") shouldBe null

      }
    }


    "make sure payeCancel link is in view if cancelURL is defined" in new Setup {
      val dashboard = Dashboard(
        IncorpAndCTDashboard(
          "acknowledged", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), Some("04")
        ),
        PAYEDashboard("draft", None, None, PAYELinks("payeURL", "otrsUrl", None, Some("cancelURL"))),
        "testCompanyName"
      )

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(Matchers.any())(Matchers.any(), any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      AuthBuilder.showWithAuthorisedUser(controller.show, mockAuthConnector) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "Your business registration overview"
          document.getElementById("payeStatusText").text() shouldBe "Incomplete"
          document.getElementById("cancelPayeLink").text() shouldBe "Cancel registration"
          document.getElementById("cancelPayeLink").attr("href") shouldBe "/register-your-company/cancel-paye"
      }
    }

    "make sure payeCancel link is not in view if cancelURL is not defined" in new Setup {
      val dashboard = Dashboard(
        IncorpAndCTDashboard(
          "acknowledged", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), Some("04")
        ),
        PAYEDashboard("draft", None, None, PAYELinks("payeURL", "otrsUrl", None, None)),
        "testCompanyName"
      )

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(Matchers.any())(Matchers.any(), any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      AuthBuilder.showWithAuthorisedUser(controller.show, mockAuthConnector) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "Your business registration overview"
          document.getElementById("payeStatusText").text() shouldBe "Incomplete"
          document.getElementById("cancelPayeLink") shouldBe null
      }
    }

  }
}
