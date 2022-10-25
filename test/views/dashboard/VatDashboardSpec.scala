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

package views.dashboard

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
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.mvc.MessagesControllerComponents
import play.api.test.Helpers._
import services.{DashboardBuilt, DashboardService}
import uk.gov.hmrc.auth.core.retrieve.{Credentials, ~}
import uk.gov.hmrc.auth.core.{AffinityGroup, Enrolment, EnrolmentIdentifier, Enrolments}
import views.ViewSpec
import views.html.dashboard.{Dashboard => DashboardView}
import views.html.reg.{RegistrationUnsuccessful => RegistrationUnsuccessfulView}
import views.messages.VatDashboardMessages

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class VatDashboardSpec extends SCRSSpec with GuiceOneAppPerSuite with AuthBuilder with ViewSpec {

  val messagesApi = app.injector.instanceOf[MessagesApi]

  val vatDashboardComponent = app.injector.instanceOf[views.html.dashboard.vatDashboard]
  val vatThreshold = 85000

  val vatDash = ServiceDashboard(
    status = "draft",
    lastUpdate = Some("lastUpdateDate"),
    ackRef = Some("ack"),
    links = ServiceLinks(
      startURL = "regURL",
      otrsURL = "otrsURL",
      restartURL = None,
      cancelURL = None
    ),
    thresholds = Some(Map("yearly" -> vatThreshold))
  )

  object Selectors {
    val dashboardHeading = "h2"
    def tableHeading(n: Int) = s"thead th:nth-of-type($n)"
    val disclosureHeading = "details summary span"
    def disclosureP(n: Int) = s"details p:nth-of-type($n)"
    def disclosureBullet(n: Int) = s"details li:nth-of-type($n)"
  }

  "vatDashboard twirl component" when {

    Seq(
      "en" -> VatDashboardMessages.English,
      "cy" -> VatDashboardMessages.Welsh
    ).foreach { case (langCode, expectedContent) =>

      "being rendered with vatFeature='false'" must {

        val renderedView = vatDashboardComponent(vatDash, vatFeatureFlag = false)(fakeRequest(), messagesApi.preferred(Seq(Lang(langCode))))
        implicit val doc = Jsoup.parse(renderedView.toString)

        s"rendered in language code '$langCode'" must {

          behave like pageWithExpectedMessages(Seq(
            Selectors.dashboardHeading -> expectedContent.dashboardHeading,
            Selectors.tableHeading(1) -> expectedContent.vatStatusHeading,
            Selectors.tableHeading(2) -> expectedContent.registerForVATLink,
            Selectors.disclosureHeading -> expectedContent.disclosureHeading,
            Selectors.disclosureP(1) -> expectedContent.disclosureP1,
            Selectors.disclosureBullet(1) -> expectedContent.disclosureBullet1(vatThreshold),
            Selectors.disclosureBullet(2) -> expectedContent.disclosureBullet2(vatThreshold),
            Selectors.disclosureP(2) -> expectedContent.disclosureP2(vatThreshold),
            Selectors.disclosureP(3) -> expectedContent.disclosureP3
          ))
        }
      }
    }
  }
}
