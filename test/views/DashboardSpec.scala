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
import controllers.dashboard.DashboardController
import controllers.reg.ControllerErrorHandler
import models.external.Statuses
import models.{Dashboard, IncorpAndCTDashboard, ServiceDashboard, ServiceLinks}
import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.MessagesApi
import play.api.mvc.MessagesControllerComponents
import play.api.test.Helpers._
import services.{DashboardBuilt, DashboardService}
import uk.gov.hmrc.auth.core.retrieve.{Credentials, ~}
import uk.gov.hmrc.auth.core.{AffinityGroup, Enrolment, EnrolmentIdentifier, Enrolments}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import views.html.dashboard.{Dashboard => DashboardView}
import views.html.reg.{RegistrationUnsuccessful => RegistrationUnsuccessfulView}

class DashboardSpec extends SCRSSpec with GuiceOneAppPerSuite with AuthBuilder {

  lazy val mockDashboardService = mock[DashboardService]
  lazy val mockControllerComponents = app.injector.instanceOf[MessagesControllerComponents]
  lazy val mockControllerErrorHandler = app.injector.instanceOf[ControllerErrorHandler]
  lazy val mockDashboardView = app.injector.instanceOf[DashboardView]
  lazy val mockRegistrationUnsuccessfulView = app.injector.instanceOf[RegistrationUnsuccessfulView]

  class Setup {
    val controller = new DashboardController (
      mockAuthConnector,
      mockKeystoreConnector,
      mockCompanyRegistrationConnector,
      mockDashboardService,
      mockControllerComponents,
      mockControllerErrorHandler,
      mockDashboardView,
      mockRegistrationUnsuccessfulView
    )(
      mockAppConfig,
      global
    ) {
      override lazy val companiesHouseURL = "testUrl"
    }
  }

  val regId = "reg-12345"
  val emptyEnrolments = Enrolments(Set())

  def authDetails(enrolments: Set[uk.gov.hmrc.auth.core.Enrolment] = Set()) = new ~(
    new ~(
      new ~(
        new ~(
          Some(AffinityGroup.Organisation),
          Enrolments(enrolments)
        ), Some("test")
      ), Some("test")
    ), Credentials("test", "test")
  )

  val payeThresholds = Map("weekly" -> 113, "monthly" -> 490, "annually" -> 5876)
  val newPayeThresholds = Map("weekly" -> 116, "monthly" -> 503, "annually" -> 6032)
  val vatThresholds = Map("yearly" -> 10000)

  "DashboardController.show" should {

    "make sure that the dashboard has the correct elements when the registration status is held" in new Setup {

      val dashboard = Dashboard(
        "testCompanyName",
        IncorpAndCTDashboard(
          "held", Some("10 October 2017"), Some("trans-12345"), Some("pay-12345"), None, None, Some("ack-12345"), None, None
        ),
        ServiceDashboard("notStarted", None, None, ServiceLinks("payeURL", "otrsUrl", None, Some("foo")), Some(payeThresholds)),
        ServiceDashboard("submitted", None, Some("ack123"), ServiceLinks("vatURL", "otrsUrl", None, Some("foo")), Some(vatThresholds))
      )

      when(mockKeystoreConnector.fetchAndGet[String](ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      when(mockDashboardService.checkForEmailMismatch(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title must include("Company registration overview")

          Map(
            "incorpStatusText" -> "Pending",
            "incorpSubmissionDate" -> "10 October 2017",
            "incorpTransID" -> "trans-12345",
            "incorpPaymentReference" -> "pay-12345",
            "incorpSubmittedText" -> "If your application is successful we’ll send you an email with the company registration number and certificate within 2 working days of the submission date.",
            "ctStatusText" -> "Pending",
            "ackRef" -> "ack-12345",
            "ctPendingText" -> "We’ve received your application but can’t process it until we’ve set up the limited company."
          ) foreach { case (element, message) =>
            document.getElementById(element).text() mustBe message
          }
      }
    }

    "make sure that the dashboard has the correct elements when the registration status is locked but not submission date" in new Setup {

      val dashboard = Dashboard(
        "testCompanyName",
        IncorpAndCTDashboard(
          "locked", None, Some("trans-12345"), Some("pay-12345"), None, None, Some("ack-12345"), None, None
        ),
        ServiceDashboard("notStarted", None, None, ServiceLinks("payeURL", "otrsUrl", None, Some("foo")), Some(payeThresholds)),
        ServiceDashboard("submitted", None, Some("ack123"), ServiceLinks("vatURL", "otrsUrl", None, Some("foo")), Some(vatThresholds))
      )

      when(mockKeystoreConnector.fetchAndGet[String](ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      when(mockDashboardService.checkForEmailMismatch(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title must include("Company registration overview")

          Map(
            "incorpStatusText" -> "Pending",
            "incorpTransID" -> "trans-12345",
            "incorpPaymentReference" -> "pay-12345",
            "incorpSubmittedText" -> "If your application is successful we’ll send you an email with the company registration number and certificate within 2 working days of the submission date.",
            "ctStatusText" -> "Pending",
            "ackRef" -> "ack-12345",
            "ctPendingText" -> "We’ve received your application but can’t process it until we’ve set up the limited company.",
            "incorpSubmissionDate" -> ""
          ) foreach { case (element, message) =>
            document.getElementById(element).text() mustBe message
          }
      }
    }

    "make sure that the dashboard has the correct elements when the registration status is submitted" in new Setup {

      val dashboard = Dashboard(
        "testCompanyName",
        IncorpAndCTDashboard(
          "submitted", Some("10 October 2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11 October 2017"), Some("ack-12345"), None, None
        ),
        ServiceDashboard("notStarted", None, None, ServiceLinks("payeURL", "otrsUrl", None, Some("foo")), Some(payeThresholds)),
        ServiceDashboard("submitted", None, Some("ack123"), ServiceLinks("vatURL", "otrsUrl", None, Some("foo")), Some(vatThresholds))
      )

      when(mockKeystoreConnector.fetchAndGet[String](ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      when(mockDashboardService.checkForEmailMismatch(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title must include("Company registration overview")

          Map(
            "incorpStatusText" -> "Registered",
            "crn" -> "crn123",
            "ctStatusText" -> "Pending",
            "submittedAckRef" -> "ack-12345",
            "CTifSuccess" -> "If your application is successful we’ll send you:"
          ) foreach { case (element, message) =>
            document.getElementById(element).text() mustBe message
          }
      }
    }

    "make sure that the dashboard has the correct elements when ETMP has approved the CT submission" in new Setup {

      val dashboard = Dashboard(
        "testCompanyName",
        IncorpAndCTDashboard(
          "acknowledged", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), None, Some("CTUTR")
        ),
        ServiceDashboard("notStarted", None, None, ServiceLinks("payeURL", "otrsUrl", None, Some("foo")), Some(payeThresholds)),
        ServiceDashboard("submitted", None, Some("ack123"), ServiceLinks("vatURL", "otrsUrl", None, Some("foo")), Some(vatThresholds))
      )

      when(mockKeystoreConnector.fetchAndGet[String](ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      when(mockDashboardService.checkForEmailMismatch(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title must include("Company registration overview")

          Map(
            "incorpStatusText" -> "Registered",
            "crn" -> "crn123",
            "ctStatusText" -> "Registered"
          ) foreach { case (element, message) =>
            document.getElementById(element).text() mustBe message
          }

      }
    }

    Set(
      "06", "07", "08", "09", "10"
    ) foreach { status =>
      s"make sure that the dashboard has the correct elements when ETMP has rejected the CT submission ($status status)" in new Setup {

        val dashboard = Dashboard(
          "testCompanyName",
          IncorpAndCTDashboard(
            "submitted", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), Some(status), None
          ),
          ServiceDashboard("notStarted", None, None, ServiceLinks("payeURL", "otrsUrl", None, Some("foo")), None),
          ServiceDashboard("submitted", None, Some("ack123"), ServiceLinks("vatURL", "otrsUrl", None, Some("foo")), Some(vatThresholds))
        )

        when(mockKeystoreConnector.fetchAndGet[String](ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(regId)))

        when(mockDashboardService.buildDashboard(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(DashboardBuilt(dashboard)))

        when(mockDashboardService.checkForEmailMismatch(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(true))

        showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
          result =>
            val document = Jsoup.parse(contentAsString(result))
            document.title must include("Company registration overview")
            Map(
              "incorpStatusText" -> "Registered",
              "crn" -> "crn123",
              "ctStatusText" -> "Registered"
            ) foreach { case (element, message) =>
              document.getElementById(element).text() mustBe message
            }
        }
      }
    }


    Set(
      "04", "05"
    ) foreach { status =>
      s"make sure that the dashboard has the correct elements when ETMP has accepted the CT submission ($status status)" in new Setup {
        val dashboard = Dashboard(
          "testCompanyName",
          IncorpAndCTDashboard(
            "acknowledged", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), Some(status), Some("exampleUTR")
          ),
          ServiceDashboard("notStarted", None, None, ServiceLinks("payeURL", "otrsUrl", None, Some("foo")), Some(payeThresholds)),
          ServiceDashboard("submitted", None, Some("ack123"), ServiceLinks("vatURL", "otrsUrl", None, Some("foo")), Some(vatThresholds))
        )

        when(mockKeystoreConnector.fetchAndGet[String](ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(regId)))

        when(mockDashboardService.buildDashboard(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(DashboardBuilt(dashboard)))

        when(mockDashboardService.checkForEmailMismatch(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(true))

        showWithAuthorisedUserRetrieval(controller.show, authDetails(Set(Enrolment("IR-CT", Seq(EnrolmentIdentifier("UTR", "exampleUTR")), "activated")))) {
          result =>
            val document = Jsoup.parse(contentAsString(result))
            document.title must include("Company registration overview")

            Map(
              "incorpStatusText" -> "Registered",
              "crn" -> "crn123",
              "ctStatusText" -> "Registered",
              "ctutrText" -> "exampleUTR"
            ) foreach { case (element, message) =>
              document.getElementById(element).text() mustBe message
            }
        }
      }
    }

    Set(
      "04", "05"
    ) foreach { status =>
      s"make sure that the dashboard has the correct elements when ETMP has accepted the CT submission but CTUTR mismatched ($status status)" in new Setup {
        val dashboard = Dashboard(
          "testCompanyName",
          IncorpAndCTDashboard(
            "acknowledged", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), Some(status), None
          ),
          ServiceDashboard("notStarted", None, None, ServiceLinks("payeURL", "otrsUrl", None, Some("foo")), Some(payeThresholds)),
          ServiceDashboard("submitted", None, Some("ack123"), ServiceLinks("vatURL", "otrsUrl", None, Some("foo")), Some(vatThresholds))
        )

        when(mockKeystoreConnector.fetchAndGet[String](ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(regId)))

        when(mockDashboardService.buildDashboard(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(DashboardBuilt(dashboard)))

        when(mockDashboardService.checkForEmailMismatch(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(true))

        showWithAuthorisedUserRetrieval(controller.show, authDetails(Set(Enrolment("IR-CT", Seq(EnrolmentIdentifier("UTR", "exampleUTR")), "activated")))) {
          result =>
            val document = Jsoup.parse(contentAsString(result))
            document.title must include("Company registration overview")

            Map(
              "incorpStatusText" -> "Registered",
              "crn" -> "crn123",
              "ctStatusText" -> "Registered"
            ) foreach { case (element, message) =>
              document.getElementById(element).text() mustBe message
            }

            document.getElementById("ctutrText") mustBe null
        }
      }
    }

    Set(
      "04", "05"
    ) foreach { status =>
      s"make sure that the dashboard has the correct elements when ETMP has accepted the CT submission but no IR_CT enrolement  ($status status)" in new Setup {
        val dashboard = Dashboard(
          "testCompanyName",
          IncorpAndCTDashboard(
            "acknowledged", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), Some(status), None
          ),
          ServiceDashboard("notStarted", None, None, ServiceLinks("payeURL", "otrsUrl", None, Some("foo")), Some(payeThresholds)),
          ServiceDashboard("submitted", None, Some("ack123"), ServiceLinks("vatURL", "otrsUrl", None, Some("foo")), Some(vatThresholds))
        )

        when(mockKeystoreConnector.fetchAndGet[String](ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(regId)))

        when(mockDashboardService.buildDashboard(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(DashboardBuilt(dashboard)))

        when(mockDashboardService.checkForEmailMismatch(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(true))

        showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
          result =>
            val document = Jsoup.parse(contentAsString(result))
            document.title must include("Company registration overview")

            Map(
              "incorpStatusText" -> "Registered",
              "crn" -> "crn123",
              "noCTEnrolmentMessage" -> "We’ve sent you an activation code in the post. Use this to activate your Corporation Tax enrolment so you can manage Corporation Tax online."
            ) foreach { case (element, message) =>
              document.getElementById(element).text() mustBe message
            }

            document.getElementById("ctutrText") mustBe null

        }
      }
    }

    "make sure that the dashboard has the correct elements for a PAYE not eligible status" in new Setup {

      val dashboard = Dashboard(
        "testCompanyName",
        IncorpAndCTDashboard(
          "acknowledged", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), Some("04"), Some("CTUTR")
        ),
        ServiceDashboard("notEligible", None, None, ServiceLinks("payeURL", "otrsUrl", None, Some("foo")), None),
        ServiceDashboard("submitted", None, Some("ack123"), ServiceLinks("vatURL", "otrsUrl", None, Some("foo")), Some(vatThresholds))
      )

      when(mockKeystoreConnector.fetchAndGet[String](ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      when(mockDashboardService.checkForEmailMismatch(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title must include("Company registration overview")
          document.getElementById("payeStatusText").text() mustBe "Not eligible"
      }
    }

    "make sure that the dashboard has the correct elements for a PAYE unavailable status" in new Setup {

      val dashboard = Dashboard(
        "testCompanyName",
        IncorpAndCTDashboard(
          "acknowledged", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), Some("04"), Some("CTUTR")
        ),
        ServiceDashboard(Statuses.UNAVAILABLE, None, None, ServiceLinks("payeURL", "otrsUrl", None, Some("foo")), None),
        ServiceDashboard("submitted", None, Some("ack123"), ServiceLinks("vatURL", "otrsUrl", None, Some("foo")), Some(vatThresholds))
      )

      when(mockKeystoreConnector.fetchAndGet[String](ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      when(mockDashboardService.checkForEmailMismatch(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title must include("Company registration overview")
          document.getElementById("payeStatusText").text() mustBe "Temporarily unavailable"
      }
    }

    "make sure that the dashboard has the correct elements for a PAYE not started status" in new Setup {

      val dashboard = Dashboard(
        "testCompanyName",
        IncorpAndCTDashboard(
          "acknowledged", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), Some("04"), Some("CTUTR")
        ),
        ServiceDashboard("notStarted", None, None, ServiceLinks("payeURL", "otrsUrl", None, Some("foo")), Some(payeThresholds)),
        ServiceDashboard("submitted", None, Some("ack123"), ServiceLinks("vatURL", "otrsUrl", None, Some("foo")), Some(vatThresholds))
      )

      when(mockKeystoreConnector.fetchAndGet[String](ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      when(mockDashboardService.checkForEmailMismatch(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title must include("Company registration overview")
          document.getElementById("payeStatusText").text() mustBe "Register for PAYE"
      }
    }

    "make sure that the dashboard has the correct elements for a PAYE draft status" in new Setup {

      val dashboard = Dashboard(
        "testCompanyName",
        IncorpAndCTDashboard(
          "acknowledged", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), Some("04"), Some("CTUTR")
        ),
        ServiceDashboard("draft", None, None, ServiceLinks("payeURL", "otrsUrl", None, Some("foo")), Some(payeThresholds)),
        ServiceDashboard("submitted", None, Some("ack123"), ServiceLinks("vatURL", "otrsUrl", None, Some("foo")), Some(vatThresholds))
      )

      when(mockKeystoreConnector.fetchAndGet[String](ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      when(mockDashboardService.checkForEmailMismatch(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title must include("Company registration overview")
          document.getElementById("payeStatusText").text() mustBe "Incomplete"

      }
    }

    "make sure that the dashboard has the correct elements for a PAYE held status" in new Setup {

      val dashboard = Dashboard(
        "testCompanyName",
        IncorpAndCTDashboard(
          "acknowledged", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), Some("04"), Some("CTUTR")
        ),
        ServiceDashboard("held", None, Some("ABCD12345678901"), ServiceLinks("payeURL", "otrsUrl", None, Some("foo")), None),
        ServiceDashboard("submitted", None, Some("ack123"), ServiceLinks("vatURL", "otrsUrl", None, Some("foo")), Some(vatThresholds))
      )

      when(mockKeystoreConnector.fetchAndGet[String](ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      when(mockDashboardService.checkForEmailMismatch(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title must include("Company registration overview")

          Map(
            "payeStatusText" -> "Pending",
            "PAYERef" -> "ABCD12345678901"
          ) foreach { case (element, message) =>
            document.getElementById(element).text() mustBe message
          }
      }
    }

    "make sure that the dashboard has the correct elements for a PAYE submitted status" in new Setup {

      val dashboard = Dashboard(
        "testCompanyName",
        IncorpAndCTDashboard(
          "acknowledged", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), Some("04"), Some("CTUTR")
        ),
        ServiceDashboard("submitted", Some("15 May 2017"), Some("ABCD12345678901"), ServiceLinks("payeURL", "otrsUrl", None, Some("foo")), Some(payeThresholds)),
        ServiceDashboard("submitted", None, Some("ack123"), ServiceLinks("vatURL", "otrsUrl", None, Some("foo")), Some(vatThresholds))
      )

      when(mockKeystoreConnector.fetchAndGet[String](ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      when(mockDashboardService.checkForEmailMismatch(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title must include("Company registration overview")

          Map(
            "payeStatusText" -> "Pending",
            "PAYERef" -> "ABCD12345678901",
            "PAYEDate" -> "15 May 2017"
          ) foreach { case (element, message) =>
            document.getElementById(element).text() mustBe message
          }
      }
    }

    "make sure that the dashboard has the correct elements for a PAYE acknowledged status" in new Setup {

      val dashboard = Dashboard(
        "testCompanyName",
        IncorpAndCTDashboard(
          "acknowledged", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), Some("04"), Some("CTUTR")
        ),
        ServiceDashboard("acknowledged", None, None, ServiceLinks("payeURL", "otrsUrl", None, Some("foo")), Some(payeThresholds)),
        ServiceDashboard("submitted", None, Some("ack123"), ServiceLinks("vatURL", "otrsUrl", None, Some("foo")), Some(vatThresholds))
      )

      when(mockKeystoreConnector.fetchAndGet[String](ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      when(mockDashboardService.checkForEmailMismatch(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title must include("Company registration overview")
          document.getElementById("payeStatusText").text() mustBe "Registered"
      }
    }

    Set(
      "06", "07", "08", "09", "10"
    ) foreach { status =>
      s"make sure that the dashboard does not show the PAYE section when the CT submission status is rejected ($status ETMP status)" in new Setup {
        val dashboard = Dashboard(
          "testCompanyName",
          IncorpAndCTDashboard(
            "submitted", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), Some(status), None
          ),
          ServiceDashboard("notStarted", None, None, ServiceLinks("payeURL", "otrsUrl", None, None), None),
          ServiceDashboard("submitted", None, Some("ack123"), ServiceLinks("vatURL", "otrsUrl", None, Some("foo")), Some(vatThresholds)),
          hasVATCred = false
        )

        when(mockKeystoreConnector.fetchAndGet[String](ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(regId)))

        when(mockDashboardService.buildDashboard(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(DashboardBuilt(dashboard)))

        when(mockDashboardService.checkForEmailMismatch(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(true))

        showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
          result =>
            val document = Jsoup.parse(contentAsString(result))
            document.title must include("Company registration overview")
            intercept[NullPointerException](document.getElementById("payeStatusText").text())
        }
      }
    }

    "make sure that the dashboard has the correct elements for a PAYE invalid status" in new Setup {

      val dashboard = Dashboard(
        "testCompanyName",
        IncorpAndCTDashboard(
          "acknowledged", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), Some("04"), Some("CTUTR")
        ),
        ServiceDashboard("invalid", None, None, ServiceLinks("payeURL", "otrsUrl", None, Some("foo")), Some(payeThresholds)),
        ServiceDashboard("submitted", None, Some("ack123"), ServiceLinks("vatURL", "otrsUrl", None, Some("foo")), Some(vatThresholds))
      )

      when(mockKeystoreConnector.fetchAndGet[String](ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      when(mockDashboardService.checkForEmailMismatch(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title must include("Company registration overview")
          document.getElementById("payeStatusText").text() mustBe "Incomplete"


      }
    }

    "make sure that the dashboard has the correct elements for a PAYE rejected status" in new Setup {

      val dashboard = Dashboard(
        "testCompanyName",
        IncorpAndCTDashboard(
          "acknowledged", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), Some("04"), Some("CTUTR")
        ),
        ServiceDashboard("rejected", None, None, ServiceLinks("payeURL", "otrsUrl", Some("bar"), None), Some(payeThresholds)),
        ServiceDashboard("submitted", None, Some("ack123"), ServiceLinks("vatURL", "otrsUrl", None, Some("foo")), Some(vatThresholds))
      )

      when(mockKeystoreConnector.fetchAndGet[String](ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      when(mockDashboardService.checkForEmailMismatch(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title must include("Company registration overview")
          document.getElementById("payeStatusText").text() mustBe "Unsuccessful"
      }
    }

    "make sure that the dashboard has the correct elements when the VAT section is displayed and CT status is acknowledged" in new Setup {
      val dashboard = Dashboard(
        "testCompanyName",
        IncorpAndCTDashboard(
          "acknowledged", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), Some("04"), Some("CTUTR")
        ),
        ServiceDashboard("notStarted", None, None, ServiceLinks("payeURL", "otrsUrl", None, None), Some(payeThresholds)),
        ServiceDashboard("submitted", None, Some("ack123"), ServiceLinks("vatURL", "otrsUrl", None, Some("foo")), Some(vatThresholds)),
        hasVATCred = false
      )

      when(mockKeystoreConnector.fetchAndGet[String](ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      when(mockDashboardService.checkForEmailMismatch(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title must include("Company registration overview")
          document.getElementById("legacyVATStatusText").text() mustBe "Register using another HMRC service (opens in new tab)"
      }
    }

    Set(
      "06", "07", "08", "09", "10"
    ) foreach { status =>
      s"make sure that the dashboard does not show the VAT section when the CT submission status is rejected ($status ETMP status)" in new Setup {
        val dashboard = Dashboard(
          "testCompanyName",
          IncorpAndCTDashboard(
            "submitted", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), Some(status), Some("CTUTR")
          ),
          ServiceDashboard("notStarted", None, None, ServiceLinks("payeURL", "otrsUrl", None, None), Some(payeThresholds)),
          ServiceDashboard("submitted", None, Some("ack123"), ServiceLinks("vatURL", "otrsUrl", None, Some("foo")), Some(vatThresholds)),
          hasVATCred = false
        )

        when(mockKeystoreConnector.fetchAndGet[String](ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(regId)))

        when(mockDashboardService.buildDashboard(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(DashboardBuilt(dashboard)))

        when(mockDashboardService.checkForEmailMismatch(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(true))

        showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
          result =>
            val document = Jsoup.parse(contentAsString(result))
            document.title must include("Company registration overview")
            intercept[NullPointerException](document.getElementById("legacyVATStatusText").text())
        }
      }
    }

    "make sure that the dashboard shows the VAT section when the CT submission status is submitted" in new Setup {
      val dashboard = Dashboard(
        "testCompanyName",
        IncorpAndCTDashboard(
          "submitted", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), None, Some("CTUTR")
        ),
        ServiceDashboard("notStarted", None, None, ServiceLinks("payeURL", "otrsUrl", None, None), Some(payeThresholds)),
        ServiceDashboard("submitted", None, Some("ack123"), ServiceLinks("vatURL", "otrsUrl", None, Some("foo")), Some(vatThresholds)),
        hasVATCred = false
      )

      when(mockKeystoreConnector.fetchAndGet[String](ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      when(mockDashboardService.checkForEmailMismatch(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title must include("Company registration overview")
          document.getElementById("legacyVATStatusText").text() mustBe "Register using another HMRC service (opens in new tab)"
      }
    }

    "make sure that the dashboard does not display the VAT block if the user has a VAT cred" in new Setup {
      val dashboard = Dashboard(
        "testCompanyName",
        IncorpAndCTDashboard(
          "acknowledged", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), None, Some("CTUTR")
        ),
        ServiceDashboard("notStarted", None, None, ServiceLinks("payeURL", "otrsUrl", None, None), Some(payeThresholds)),
        ServiceDashboard("submitted", None, Some("ack123"), ServiceLinks("vatURL", "otrsUrl", None, Some("foo")), Some(vatThresholds)),
        hasVATCred = true
      )

      when(mockKeystoreConnector.fetchAndGet[String](ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      when(mockDashboardService.checkForEmailMismatch(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title must include("Company registration overview")
          document.getElementById("vatStatusText") mustBe null
          document.getElementById("vatUrl") mustBe null
      }
    }

    "make sure that the dashboard does not display the VAT block if the company is not incorporated yet" in new Setup {
      val dashboard = Dashboard(
        "testCompanyName",
        IncorpAndCTDashboard(
          "held", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), None, None
        ),
        ServiceDashboard("notStarted", None, None, ServiceLinks("payeURL", "otrsUrl", None, None), Some(payeThresholds)),
        ServiceDashboard("submitted", None, Some("ack123"), ServiceLinks("vatURL", "otrsUrl", None, Some("foo")), Some(vatThresholds)),
        hasVATCred = false
      )

      when(mockKeystoreConnector.fetchAndGet[String](ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      when(mockDashboardService.checkForEmailMismatch(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title must include("Company registration overview")
          document.getElementById("vatStatusText") mustBe null
          document.getElementById("vatUrl") mustBe null

      }
    }

    "make sure payeCancel link is in view if cancelURL is defined" in new Setup {
      val dashboard = Dashboard(
        "testCompanyName",
        IncorpAndCTDashboard(
          "acknowledged", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), Some("04"), Some("CTUTR")
        ),
        ServiceDashboard("draft", None, None, ServiceLinks("payeURL", "otrsUrl", None, Some("cancelURL")), Some(payeThresholds)),
        ServiceDashboard("submitted", None, Some("ack123"), ServiceLinks("vatURL", "otrsUrl", None, Some("foo")), Some(vatThresholds))
      )

      when(mockKeystoreConnector.fetchAndGet[String](ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      when(mockDashboardService.checkForEmailMismatch(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title must include("Company registration overview")
          document.getElementById("payeStatusText").text() mustBe "Incomplete"
      }
    }

    "make sure payeCancel link is not in view if cancelURL is not defined" in new Setup {
      val dashboard = Dashboard(
        "testCompanyName",
        IncorpAndCTDashboard(
          "acknowledged", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), Some("04"), Some("CTUTR")
        ),
        ServiceDashboard("draft", None, None, ServiceLinks("payeURL", "otrsUrl", None, None), Some(payeThresholds)),
        ServiceDashboard("submitted", None, Some("ack123"), ServiceLinks("vatURL", "otrsUrl", None, Some("foo")), Some(vatThresholds))
      )

      when(mockKeystoreConnector.fetchAndGet[String](ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      when(mockDashboardService.checkForEmailMismatch(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title must include("Company registration overview")
          document.getElementById("payeStatusText").text() mustBe "Incomplete"
      }
    }
  }

  "paye thresholds" should {
    "be the 2017 thresholds" in new Setup {

      val dashboard = Dashboard(
        "testCompanyName",
        IncorpAndCTDashboard(
          "acknowledged", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), Some("04"), Some("CTUTR")
        ),
        ServiceDashboard("notStarted", None, None, ServiceLinks("payeURL", "otrsUrl", None, Some("foo")), Some(payeThresholds)),
        ServiceDashboard("submitted", None, Some("ack123"), ServiceLinks("vatURL", "otrsUrl", None, Some("foo")), Some(vatThresholds))
      )

      when(mockKeystoreConnector.fetchAndGet[String](ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      when(mockDashboardService.checkForEmailMismatch(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.getElementById("employer-help-thresholds").text() mustBe "Use this service to register a company if it will do either of the following in the next 2 months:"
      }
    }

    "be the 2018 thresholds" in new Setup {
      val dashboard = Dashboard(
        "testCompanyName",
        IncorpAndCTDashboard(
          "acknowledged", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), Some("04"), Some("CTUTR")
        ),
        ServiceDashboard("notStarted", None, None, ServiceLinks("payeURL", "otrsUrl", None, Some("foo")), Some(newPayeThresholds)),
        ServiceDashboard("submitted", None, Some("ack123"), ServiceLinks("vatURL", "otrsUrl", None, Some("foo")), Some(vatThresholds))
      )

      when(mockKeystoreConnector.fetchAndGet[String](ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      when(mockDashboardService.checkForEmailMismatch(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.getElementById("employer-help-thresholds").text() mustBe "Use this service to register a company if it will do either of the following in the next 2 months:"
      }
    }
  }
}
