/*
 * Copyright 2019 HM Revenue & Customs
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
import models.external.Statuses
import models.{Dashboard, IncorpAndCTDashboard, ServiceDashboard, ServiceLinks}
import org.jsoup.Jsoup
import org.mockito.Matchers
import org.mockito.Mockito._
import play.api.i18n.MessagesApi
import play.api.test.Helpers._
import services.{DashboardBuilt, DashboardService}
import uk.gov.hmrc.auth.core.retrieve.{Credentials, ~}
import uk.gov.hmrc.auth.core.{AffinityGroup, Enrolment, EnrolmentIdentifier, Enrolments}
import uk.gov.hmrc.play.test.WithFakeApplication

import scala.concurrent.Future

class DashboardSpec extends SCRSSpec with WithFakeApplication with AuthBuilder {

  val mockDashboardService = mock[DashboardService]


  class Setup {
    val controller = new DashboardController {
      override val authConnector = mockAuthConnector
      override val keystoreConnector = mockKeystoreConnector
      override val dashboardService = mockDashboardService
      override val compRegConnector = mockCompanyRegistrationConnector
      override val companiesHouseURL = "testUrl"
      override val appConfig = mockAppConfig
      override val messagesApi = fakeApplication.injector.instanceOf[MessagesApi]
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

  val payeThresholds    = Map("weekly" -> 113, "monthly" -> 490, "annually" -> 5876)
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

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

              when(mockDashboardService.checkForEmailMismatch(Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(true))

      showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "Company registration overview"

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

    "make sure that the dashboard has the correct elements when the registration status is locked but not submission date" in new Setup {

      val dashboard = Dashboard(
        "testCompanyName",
        IncorpAndCTDashboard(
          "locked", None, Some("trans-12345"), Some("pay-12345"), None, None, Some("ack-12345"), None, None
        ),
        ServiceDashboard("notStarted", None, None, ServiceLinks("payeURL", "otrsUrl", None, Some("foo")), Some(payeThresholds)),
        ServiceDashboard("submitted", None, Some("ack123"), ServiceLinks("vatURL", "otrsUrl", None, Some("foo")), Some(vatThresholds))
      )

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      when(mockDashboardService.checkForEmailMismatch(Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(true))

      showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "Company registration overview"

          Map(
            "incorpStatusText" -> "Pending",
            "incorpTransID" -> "trans-12345",
            "incorpPaymentReference" -> "pay-12345",
            "incorpSubmittedText" -> "If your application is successful we'll send you an email with the company registration number and certificate within 2 working days of the submission date.",
            "ctStatusText" -> "Pending",
            "ackRef" -> "ack-12345",
            "ctPendingText" -> "We've received your application but can't process it until we've set up the limited company."
          ) foreach { case (element, message) =>
            document.getElementById(element).text() shouldBe message
          }
          intercept[NullPointerException](document.getElementById("incorpSubmissionDate").`val`)
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

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

              when(mockDashboardService.checkForEmailMismatch(Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(true))

      showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "Company registration overview"

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
        "testCompanyName",
        IncorpAndCTDashboard(
          "acknowledged", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), None, Some("CTUTR")
        ),
        ServiceDashboard("notStarted", None, None, ServiceLinks("payeURL", "otrsUrl", None, Some("foo")), Some(payeThresholds)),
        ServiceDashboard("submitted", None, Some("ack123"), ServiceLinks("vatURL", "otrsUrl", None, Some("foo")), Some(vatThresholds))
      )

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

      when(mockDashboardService.checkForEmailMismatch(Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(true))

      showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "Company registration overview"

          Map(
            "incorpStatusText" -> "Registered",
            "crn" -> "crn123",
            "ctStatusText" -> "Registered"
          ) foreach { case (element, message) =>
            document.getElementById(element).text() shouldBe message
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

        when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(Some(regId)))

        when(mockDashboardService.buildDashboard(Matchers.any(), Matchers.any())(Matchers.any()))
          .thenReturn(Future.successful(DashboardBuilt(dashboard)))

        when(mockDashboardService.checkForEmailMismatch(Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(true))

        showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
          result =>
            val document = Jsoup.parse(contentAsString(result))
            document.title() shouldBe "Company registration overview"
            Map(
              "incorpStatusText" -> "Registered",
              "crn" -> "crn123",
              "ctStatusText" -> "Registered"
            ) foreach { case (element, message) =>
              document.getElementById(element).text() shouldBe message
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

        when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(Some(regId)))

        when(mockDashboardService.buildDashboard(Matchers.any(), Matchers.any())(Matchers.any()))
          .thenReturn(Future.successful(DashboardBuilt(dashboard)))

        when(mockDashboardService.checkForEmailMismatch(Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(true))

        showWithAuthorisedUserRetrieval(controller.show, authDetails(Set(Enrolment("IR-CT", Seq(EnrolmentIdentifier("UTR", "exampleUTR")), "activated")))) {
          result =>
            val document = Jsoup.parse(contentAsString(result))
            document.title() shouldBe "Company registration overview"

            Map(
              "incorpStatusText" -> "Registered",
              "crn" -> "crn123",
              "ctStatusText" -> "Registered",
              "ctutrText" -> "exampleUTR"
            ) foreach { case (element, message) =>
              document.getElementById(element).text() shouldBe message
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

        when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(Some(regId)))

        when(mockDashboardService.buildDashboard(Matchers.any(), Matchers.any())(Matchers.any()))
          .thenReturn(Future.successful(DashboardBuilt(dashboard)))

        when(mockDashboardService.checkForEmailMismatch(Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(true))

        showWithAuthorisedUserRetrieval(controller.show, authDetails(Set(Enrolment("IR-CT", Seq(EnrolmentIdentifier("UTR", "exampleUTR")), "activated")))) {
          result =>
            val document = Jsoup.parse(contentAsString(result))
            document.title() shouldBe "Company registration overview"

            Map(
              "incorpStatusText" -> "Registered",
              "crn" -> "crn123",
              "ctStatusText" -> "Registered"
            ) foreach { case (element, message) =>
              document.getElementById(element).text() shouldBe message
            }

            document.getElementById("ctutrText") shouldBe null
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

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

              when(mockDashboardService.checkForEmailMismatch(Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(true))

      showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "Company registration overview"
          document.getElementById("payeStatusText").text() shouldBe "Not eligible"
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

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

              when(mockDashboardService.checkForEmailMismatch(Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(true))

      showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "Company registration overview"
          document.getElementById("payeStatusText").text() shouldBe "Temporarily unavailable"
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

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

              when(mockDashboardService.checkForEmailMismatch(Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(true))

      showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "Company registration overview"
          document.getElementById("payeStatusText").text() shouldBe "Register for PAYE"
          document.getElementById("payeRegUrl").attr("href") shouldBe dashboard.payeDash.links.startURL
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

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

              when(mockDashboardService.checkForEmailMismatch(Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(true))

      showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "Company registration overview"
          document.getElementById("payeStatusText").text() shouldBe "Incomplete"

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

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

              when(mockDashboardService.checkForEmailMismatch(Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(true))

      showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "Company registration overview"

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
        "testCompanyName",
        IncorpAndCTDashboard(
          "acknowledged", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), Some("04"), Some("CTUTR")
        ),
        ServiceDashboard("submitted", Some("15 May 2017"), Some("ABCD12345678901"), ServiceLinks("payeURL", "otrsUrl", None, Some("foo")), Some(payeThresholds)),
        ServiceDashboard("submitted", None, Some("ack123"), ServiceLinks("vatURL", "otrsUrl", None, Some("foo")), Some(vatThresholds))
      )

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

              when(mockDashboardService.checkForEmailMismatch(Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(true))

      showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "Company registration overview"

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
        "testCompanyName",
        IncorpAndCTDashboard(
          "acknowledged", Some("10-10-2017"), Some("trans-12345"), Some("pay-12345"), Some("crn123"), Some("11-10-2017"), Some("ack-12345"), Some("04"), Some("CTUTR")
        ),
        ServiceDashboard("acknowledged", None, None, ServiceLinks("payeURL", "otrsUrl", None, Some("foo")), Some(payeThresholds)),
        ServiceDashboard("submitted", None, Some("ack123"), ServiceLinks("vatURL", "otrsUrl", None, Some("foo")), Some(vatThresholds))
      )

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

              when(mockDashboardService.checkForEmailMismatch(Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(true))

      showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "Company registration overview"
          document.getElementById("payeStatusText").text() shouldBe "Registered"
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

        when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(Some(regId)))

        when(mockDashboardService.buildDashboard(Matchers.any(), Matchers.any())(Matchers.any()))
          .thenReturn(Future.successful(DashboardBuilt(dashboard)))

        when(mockDashboardService.checkForEmailMismatch(Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(true))

        showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
          result =>
            val document = Jsoup.parse(contentAsString(result))
            document.title() shouldBe "Company registration overview"
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

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

              when(mockDashboardService.checkForEmailMismatch(Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(true))

      showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "Company registration overview"
          document.getElementById("payeStatusText").text() shouldBe "Incomplete"


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

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

              when(mockDashboardService.checkForEmailMismatch(Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(true))

      showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "Company registration overview"
          document.getElementById("payeStatusText").text() shouldBe "Unsuccessful"
          document.getElementById("payeRej").attr("href") shouldBe "bar"
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

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

              when(mockDashboardService.checkForEmailMismatch(Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(true))

      showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "Company registration overview"
          document.getElementById("legacyVATStatusText").text() shouldBe "Register using another HMRC service (link opens in a new tab)"
          document.getElementById("vatUrl").attr("href") shouldBe "https://online.hmrc.gov.uk/registration/newbusiness/introduction"
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

        when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(Some(regId)))

        when(mockDashboardService.buildDashboard(Matchers.any(), Matchers.any())(Matchers.any()))
          .thenReturn(Future.successful(DashboardBuilt(dashboard)))

        when(mockDashboardService.checkForEmailMismatch(Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(true))

        showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
          result =>
            val document = Jsoup.parse(contentAsString(result))
            document.title() shouldBe "Company registration overview"
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

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

              when(mockDashboardService.checkForEmailMismatch(Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(true))

      showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "Company registration overview"
          document.getElementById("legacyVATStatusText").text() shouldBe "Register using another HMRC service (link opens in a new tab)"
          document.getElementById("vatUrl").attr("href") shouldBe "https://online.hmrc.gov.uk/registration/newbusiness/introduction"
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

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

              when(mockDashboardService.checkForEmailMismatch(Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(true))

      showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "Company registration overview"
          document.getElementById("vatStatusText") shouldBe null
          document.getElementById("vatUrl") shouldBe null
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

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

              when(mockDashboardService.checkForEmailMismatch(Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(true))

      showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "Company registration overview"
          document.getElementById("vatStatusText") shouldBe null
          document.getElementById("vatUrl") shouldBe null

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

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

              when(mockDashboardService.checkForEmailMismatch(Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(true))

      showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "Company registration overview"
          document.getElementById("payeStatusText").text() shouldBe "Incomplete"
          document.getElementById("payeCancelLink").text() shouldBe "Cancel registration"
          document.getElementById("payeCancelLink").attr("href") shouldBe "cancelURL"
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

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

              when(mockDashboardService.checkForEmailMismatch(Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(true))

      showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "Company registration overview"
          document.getElementById("payeStatusText").text() shouldBe "Incomplete"
          document.getElementById("payeCancelLink") shouldBe null
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

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

              when(mockDashboardService.checkForEmailMismatch(Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(true))

      showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.getElementById("employer-help-thresholds").text() shouldBe "pay any employees - including company directors - £113 or more a week (this is the same as £490 a month or £5,876 a year)"
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

      when(mockKeystoreConnector.fetchAndGet[String](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(regId)))

      when(mockDashboardService.buildDashboard(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(DashboardBuilt(dashboard)))

              when(mockDashboardService.checkForEmailMismatch(Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(true))

      showWithAuthorisedUserRetrieval(controller.show, authDetails()) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.getElementById("employer-help-thresholds").text() shouldBe "pay any employees - including company directors - £116 or more a week (this is the same as £503 a month or £6,032 a year)"
      }
    }
  }
}
