/*
 * Copyright 2026 HM Revenue & Customs
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

import config.{AppConfig, UrBannerUtil}
import _root_.helpers.SCRSSpec
import org.jsoup.Jsoup
import org.scalatest.BeforeAndAfterEach
import org.jsoup.nodes.Document
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.twirl.api.Html
import uk.gov.hmrc.hmrcfrontend.views.viewmodels.language.{Cy, En}
import uk.gov.hmrc.hmrcfrontend.views.viewmodels.userresearchbanner.UserResearchBanner
import views.html.templates.layout

class UrBannerSpec extends SCRSSpec with GuiceOneAppPerSuite with BeforeAndAfterEach {

  lazy val conf: AppConfig = app.injector.instanceOf[AppConfig]
  lazy val msgApi: MessagesApi = app.injector.instanceOf[MessagesApi]
  val req: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
  lazy val msgs: Messages = msgApi.preferred(req)
  lazy val layoutView: layout = app.injector.instanceOf[layout]

  object Selectors {
    val userResearchBanner = ".hmrc-user-research-banner"
    val userResearchBannerHeading = ".hmrc-user-research-banner__title"
    val userResearchBannerCloseButton = ".hmrc-user-research-banner__close"
  }

  lazy val welshMessages: Messages = msgApi.preferred(Seq(Lang("cy")))

  def render(urBanner: Option[UserResearchBanner]): Document =
    Jsoup.parse(layoutView(pageTitle = Some("Title of page"), urBanner = urBanner)(Html(""))(req, msgs, conf).body)

  "UrBannerUtil.getUrBanner" should {

    "return an English banner without a close button by default (linear pages)" in {
      UrBannerUtil.getUrBanner()(conf, msgs) mustBe UserResearchBanner(En, conf.urBannerUrl, hideCloseButton = true)
    }

    "return an English banner with a close button when hideCloseButton = false (non-linear pages)" in {
      UrBannerUtil.getUrBanner(hideCloseButton = false)(conf, msgs) mustBe UserResearchBanner(En, conf.urBannerUrl, hideCloseButton = false)
    }

    "return a Welsh banner with the Welsh survey language parameter" in {
      UrBannerUtil.getUrBanner(hideCloseButton = false)(conf, welshMessages) mustBe
        UserResearchBanner(Cy, conf.urBannerUrl + "&Q_Language=CY", hideCloseButton = false)
    }
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    sys.props += ("feature.userResearchBanner" -> "true")
  }

  override def afterEach(): Unit = {
    sys.props -= "feature.userResearchBanner"
    super.afterEach()
  }

  "the layout" when {

    "the userResearchBanner feature switch is disabled" should {
      "not display the user research banner even when one is provided" in {
        sys.props += ("feature.userResearchBanner" -> "false")
        render(Some(UrBannerUtil.getUrBanner(hideCloseButton = false)(conf, msgs))).select(Selectors.userResearchBanner).size mustBe 0
      }
    }

    "a user research banner with a close button is provided" should {
      def doc = render(Some(UrBannerUtil.getUrBanner(hideCloseButton = false)(conf, msgs)))

      "display the user research banner heading" in {
        doc.select(Selectors.userResearchBannerHeading).text mustBe "Help make GOV.UK better"
      }

      "link to the configured survey url" in {
        doc.select(s"${Selectors.userResearchBanner} a").attr("href") mustBe conf.urBannerUrl
      }

      "display the close button" in {
        doc.select(Selectors.userResearchBannerCloseButton).size mustBe 1
      }
    }

    "a user research banner without a close button is provided" should {
      def doc = render(Some(UrBannerUtil.getUrBanner()(conf, msgs)))

      "display the user research banner" in {
        doc.select(Selectors.userResearchBanner).size mustBe 1
      }

      "not display the close button" in {
        doc.select(Selectors.userResearchBannerCloseButton).size mustBe 0
      }
    }

    "no user research banner is provided" should {
      "not display the user research banner" in {
        render(None).select(Selectors.userResearchBanner).size mustBe 0
      }
    }
  }
}
