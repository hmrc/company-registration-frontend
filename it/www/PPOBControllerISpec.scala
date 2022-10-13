/*
 * Copyright 2021 HM Revenue & Customs
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

package www

import java.util.UUID

import fixtures.Fixtures
import itutil.{IntegrationSpecBase, LoginStub, RequestsFinder}
import models._
import play.api.http.HeaderNames
import play.api.libs.crypto.DefaultCookieSigner

class PPOBControllerISpec extends IntegrationSpecBase with LoginStub with Fixtures with RequestsFinder {
  val userId = "test-user-id"
  val regId = "12345"


  class Setup {
    val csrfToken = () => UUID.randomUUID().toString
    val sessionCookie = () => getSessionCookie(Map("csrfToken" -> csrfToken()), userId)
  }

  lazy val defaultCookieSigner: DefaultCookieSigner = app.injector.instanceOf[DefaultCookieSigner]

  "submit should hand off to ALF with the correct Json" in new Setup {

    stubSuccessfulLogin(userId = userId)
    stubFootprint(200, footprintResponse(regId))
    stubGet(s"/company-registration/corporation-tax-registration/$regId/corporation-tax-registration", 200, statusResponseFromCR(rID = "12345"))

    stubKeystore(SessionId, regId)
    stubPost("/api/v2/init", 200, "{}", responseHeader = ("Location", "foo"))
    val fResponse = buildClient(controllers.reg.routes.PPOBController.submit.url)
      .withHttpHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck")
      .post(Map("Csrf-Token" -> Seq("nocheck"), "addressChoice" -> Seq("OtherAddress")))

    await(fResponse).status mustBe 303
    val result = getPOSTRequestJsonBody("/api/v2/init").as[AlfJourneyConfig]
    val expected = AlfJourneyConfig(
      version = AlfJourneyConfig.defaultConfigVersion,
      options = JourneyOptions(
        continueUrl = s"http://localhost:9970${controllers.reg.routes.PPOBController.saveALFAddress(None).url}",
        homeNavHref = "http://www.hmrc.gov.uk/",
        accessibilityFooterUrl = "http://localhost:12346/accessibility-statement/company-registration",
        deskProServiceName = "SCRS",
        showPhaseBanner = true,
        alphaPhase = false,
        showBackButtons = true,
        includeHMRCBranding = false,
        disableTranslations = true,

        selectPageConfig = SelectPageConfig(
          proposalListLimit = 30,
          showSearchAgainLink = true
        ),

        confirmPageConfig = ConfirmPageConfig(
          showSearchAgainLink = false,
          showSubHeadingAndInfo = false,
          showChangeLink = true
        ),

        timeoutConfig = TimeoutConfig(
          timeoutAmount = 999999,
          timeoutUrl = "http://localhost:9970/register-your-company/error/timeout"
        )
      ),
      labels = JourneyLabels(en = LanguageLabels(
        appLevelLabels = AppLevelLabels(
          navTitle = Some("Set up a limited company and register for Corporation Tax"),
          phaseBannerHtml = None
        ),

        SelectPageLabels(
          title = Some("Choose an address"),
          heading = Some("Choose an address"),
          searchAgainLinkText = Some("Search again"),
          editAddressLinkText = Some("The address is not on the list")
        ),

        LookupPageLabels(
          title = None,
          heading = None,
          filterLabel = Some("Property name or number"),
          submitLabel = Some("Find address"),
          manualAddressLinkText = Some("Enter address manually")
        ),
        EditPageLabels(
          title = Some("Enter an address"),
          heading = Some("Enter an address"),
          line1Label = Some("Address line 1"),
          line2Label = Some("Address line 2"),
          line3Label = Some("Address line 3")
        ),
        ConfirmPageLabels(
          title = None,
          heading = None,
          submitLabel = Some("Confirm and continue"),
          changeLinkText = Some("Change")
        )
      ), cy = LanguageLabels(
        appLevelLabels = AppLevelLabels(
          navTitle = Some("Sefydlu cwmni cofrestredig a chofrestru ar gyfer Treth Gorfforaeth"),
          phaseBannerHtml = None
        ),

        SelectPageLabels(
          title = Some("Dewiswch gyfeiriad"),
          heading = Some("Dewiswch gyfeiriad"),
          searchAgainLinkText = Some("Chwilio eto"),
          editAddressLinkText = Some("Nid yw’r cyfeiriad ar y rhestr")
        ),

        LookupPageLabels(
          title = None,
          heading = None,
          filterLabel = Some("Enw neu rif yr eiddo"),
          submitLabel = Some("Dod o hyd i’r cyfeiriad"),
          manualAddressLinkText = Some("Nodwch y cyfeiriad â llaw")
        ),
        EditPageLabels(
          title = Some("Nodwch gyfeiriad"),
          heading = Some("Nodwch gyfeiriad"),
          line1Label = Some("Cyfeiriad - llinell 1"),
          line2Label = Some("Cyfeiriad - llinell 2"),
          line3Label = Some("Cyfeiriad - llinell 3")
        ),
        ConfirmPageLabels(
          title = None,
          heading = None,
          submitLabel = Some("Cadarnhau ac yn eich blaen"),
          changeLinkText = Some("Newid")
        )
      )
      )

    )

    result mustBe expected
  }

}
