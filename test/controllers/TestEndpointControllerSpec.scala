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

package controllers

import builders.AuthBuilder
import config.FrontendAppConfig
import connectors._
import controllers.test.TestEndpointController
import fixtures.{CorporationTaxFixture, SCRSFixtures}
import forms.{AccountingDatesForm, AccountingDatesFormT}
import helpers.SCRSSpec
import models._
import models.connectors.ConfirmationReferences
import models.handoff._
import org.joda.time.{DateTime, LocalDate}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.Json
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.NavModelRepo
import services.{BankHolidays, DashboardService, TimeService}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.time.workingdays.BankHolidaySet
import utils.BooleanFeatureSwitch
import views.html.dashboard.{Dashboard => DashboardView}
import views.html.reg.{TestEndpoint => TestEndpointView}
import views.html.test.{TestEndpointSummary => TestEndpointSummaryView}
import views.html.test.{FeatureSwitch => FeatureSwitchView}
import views.html.test.{PrePopAddresses => PrePopAddressesView}
import views.html.test.{PrePopContactDetails => PrePopContactDetailsView}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class TestEndpointControllerSpec extends SCRSSpec with SCRSFixtures with MockitoSugar with CorporationTaxFixture with GuiceOneAppPerSuite with AuthBuilder {

  val mockNavModelRepoObj = mock[NavModelRepo]
  val applicantData = AboutYouChoice("Director")
  val applicantDataEmpty = AboutYouChoice("")
  val applicantDataSeq = Seq("completionCapacity" -> "Director")

  val cacheMap = CacheMap("", Map("" -> Json.toJson("")))

  val userIds = UserIDs("testInternal", "testExternal")

  val mockDynamicStubConnector = mock[DynamicStubConnector]
  val mockBusinessRegistrationConnector = mock[BusinessRegistrationConnector]
  val mockDashboardService = mock[DashboardService]
  lazy val mockControllerComponents = app.injector.instanceOf[MessagesControllerComponents]

  lazy val mockDashboardView = app.injector.instanceOf[DashboardView]
  lazy val mockTestEndpointView = app.injector.instanceOf[TestEndpointView]
  lazy val mockTestEndpointSummaryView = app.injector.instanceOf[TestEndpointSummaryView]
  lazy val mockFeatureSwitchView = app.injector.instanceOf[FeatureSwitchView]
  lazy val mockPrePopAddressesView = app.injector.instanceOf[PrePopAddressesView]
  lazy val mockPrePopContactDetailsView = app.injector.instanceOf[PrePopContactDetailsView]
  lazy val appConfig: FrontendAppConfig = app.injector.instanceOf[FrontendAppConfig]

  class Setup {
    resetMocks()

    val controller = new TestEndpointController (
      mockAuthConnector,
      mockS4LConnector,
      mockKeystoreConnector,
      mockCompanyRegistrationConnector,
      mockSCRSFeatureSwitches,
      mockMetaDataService,
      mockDynamicStubConnector,
      mockBusinessRegistrationConnector,
      mockNavModelRepoObj,
      mockDashboardService,
      mockTimeService,
      mockHandOffService,
      mockFeatureSwitchManager,
      mockControllerComponents,
      mockTestEndpointView,
      mockTestEndpointSummaryView,
      mockFeatureSwitchView,
      mockPrePopAddressesView,
      mockPrePopContactDetailsView,
      mockDashboardView
    )(
      appConfig,
      global
      ){
      override lazy val accDForm = new AccountingDatesFormT {
        override val timeService = new TimeService {
          override val bHS: BankHolidaySet = BankHolidays.bankHolidaySet
          override val dayEndHour: Int = 1
          override def currentDateTime: DateTime = DateTime.now
          override def currentLocalDate: LocalDate = LocalDate.now
        }
        override val now: LocalDate = LocalDate.now
      }
    }

  }

  val registrationID = "testRegID"
  val internalID = Some("internalID")

  "TestEndpointController" should {
    "use the correct AuthConnector" in new Setup {
      controller.authConnector shouldBe a[AuthConnector]
    }
    "use the correct S4LConnector" in new Setup {
      controller.s4LConnector shouldBe a[S4LConnector]
    }
    "use the correct company registration connector" in new Setup {
      controller.compRegConnector shouldBe a[CompanyRegistrationConnector]
    }
  }

  "getAllS4LEntries" should {

    val corporationTaxModel = buildCorporationTaxModel()

    "Return a 200" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      when(mockMetaDataService.getApplicantData(ArgumentMatchers.any())(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(applicantData))
      CTRegistrationConnectorMocks.retrieveCompanyDetails(Some(validCompanyDetailsResponse))
      mockS4LFetchAndGet[CompanyNameHandOffIncoming]("HandBackData", Some(validCompanyNameHandBack))
      CTRegistrationConnectorMocks.retrieveContactDetails(CompanyContactDetailsSuccessResponse(validCompanyContactDetailsResponse))
      CTRegistrationConnectorMocks.retrieveTradingDetails(Some(TradingDetails("true")))
      CTRegistrationConnectorMocks.retrieveAccountingDetails(validAccountingResponse)

      when(mockCompanyRegistrationConnector.retrieveCorporationTaxRegistration(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(corporationTaxModel))

      showWithAuthorisedUserRetrieval(controller.getAllS4LEntries, internalID) {

        result =>
          status(result) shouldBe OK
      }
    }

    "Return a 200 even if nothing is returned" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      when(mockMetaDataService.getApplicantData(ArgumentMatchers.any())(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(applicantDataEmpty))
      CTRegistrationConnectorMocks.retrieveCompanyDetails(None)
      mockS4LFetchAndGet[CompanyNameHandOffIncoming]("HandBackData", None)
      mockS4LFetchAndGet[PPOBModel]("PPOB", None)
      CTRegistrationConnectorMocks.retrieveContactDetails(CompanyContactDetailsNotFoundResponse)
      CTRegistrationConnectorMocks.retrieveTradingDetails(None)
      CTRegistrationConnectorMocks.retrieveAccountingDetails(AccountingDetailsNotFoundResponse)
      when(mockCompanyRegistrationConnector.retrieveCorporationTaxRegistration(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(corporationTaxModel))

      showWithAuthorisedUserRetrieval(controller.getAllS4LEntries, internalID) {
        result =>
          status(result) shouldBe OK
      }
    }
  }

  "clearAllS4LEntries" should {
    "Return a 200" in new Setup {
      mockS4LClear()
      showWithAuthorisedUserRetrieval(controller.clearAllS4LEntries, internalID) {
        result =>
          status(result) shouldBe OK
      }
    }
  }

  "postAllS4LEntries" should {

    "Return a 303" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      when(mockMetaDataService.updateApplicantDataEndpoint(ArgumentMatchers.eq(applicantData))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(validBusinessRegistrationResponse))
      mockS4LSaveForm[CompanyNameHandOffIncoming]("HandBackData", cacheMap)
      mockS4LSaveForm[PPOBModel]("PPOB", cacheMap)
      CTRegistrationConnectorMocks.updateCompanyDetails(validCompanyDetailsRequest)
      CTRegistrationConnectorMocks.updateAccountingDetails(validAccountingResponse)
      CTRegistrationConnectorMocks.updateContactDetails(CompanyContactDetailsSuccessResponse(validCompanyContactDetailsResponse))
      CTRegistrationConnectorMocks.updateTradingDetails(TradingDetailsSuccessResponse(TradingDetails("false")))
      val request = FakeRequest().withFormUrlEncodedBody(
        futureDateData.toSeq ++
          handBackFormData ++
          validPPOBFormDataWithROAddress ++
          companyContactFormData ++
          companyDetailsRequestFormData ++
          applicantDataSeq ++
          Seq("regularPayments" -> "false", "choice" -> "HMRCEndDate"): _*
      )

      submitWithAuthorisedUser(controller.postAllS4LEntries, request) {
        result =>
          status(result) shouldBe SEE_OTHER
      }
    }
  }

  "clearKeystore" should {
    "remove all elements in the users keystore collection" in new Setup {
      mockKeystoreClear()

      showWithAuthorisedUserRetrieval(controller.clearKeystore, internalID) {
        result =>
          status(result) shouldBe OK
      }
    }
  }

  "showFeatureSwitch" should {

    val featureSwitchTrue = BooleanFeatureSwitch("cohoFirstHandOff", true)
    val featureSwitchFalse = BooleanFeatureSwitch("cohoFirstHandOff", false)

    "render the feature switch page when the system property for 'cohoFirstHandOff' is true" in new Setup {
      when(mockSCRSFeatureSwitches(ArgumentMatchers.contains("cohoFirstHandOff"))).thenReturn(Some(featureSwitchTrue))

      val result = controller.showFeatureSwitch(FakeRequest())

      status(result) shouldBe OK
    }

    "render the feature switch page when the system property for 'cohoFirstHandOff' is false" in new Setup {
      when(mockSCRSFeatureSwitches(ArgumentMatchers.contains("cohoFirstHandOff"))).thenReturn(Some(featureSwitchFalse))

      val result = controller.showFeatureSwitch(FakeRequest())

      status(result) shouldBe OK
    }

    "render the feature switch page when the system property for 'cohoFirstHandOff' is not found" in new Setup {
      when(mockSCRSFeatureSwitches(ArgumentMatchers.contains("cohoFirstHandOff"))).thenReturn(None)
      val result = controller.showFeatureSwitch(FakeRequest())

      status(result) shouldBe OK
    }
  }

  "updateFeatureSwitch" should {

    "submit a valid feature switch form" in new Setup {
      val request = FakeRequest().withFormUrlEncodedBody("firstHandOff" -> "true", "legacyEnv" -> "true", "takeovers" -> "true")
      val result = controller.updateFeatureSwitch()(request)

      status(result) shouldBe OK
    }

    "render the feature switch view with errors when the form data cannot be bound" in new Setup {
      val request = FakeRequest().withFormUrlEncodedBody("firstHandOff" -> "")
      val result = controller.updateFeatureSwitch()(request)

      status(result) shouldBe BAD_REQUEST
    }
  }

  "setupTestNavModel" should {

    val navModel = HandOffNavModel(
      Sender(Map(
        "1" -> NavLinks("testForwardLinkFromSender1", "testReverseLinkFromSender1"),
        "3" -> NavLinks("testForwardLinkFromSender3", "testReverseLinkFromSender3"))
      ),
      Receiver(Map(
        "0" -> NavLinks("testForwardLinkFromReceiver0", "testReverseLinkFromReceiver0"),
        "2" -> NavLinks("testForwardLinkFromReceiver2", "testReverseLinkFromReceiver2")
      ),
        Map("testJumpKey" -> "testJumpLink")
      )
    )

    "cache a fully formed nav model for use in acceptance tests" in new Setup {
      when(mockHandOffService.cacheNavModel(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(Some(navModel)))
      val result = controller.setupTestNavModel(FakeRequest())
      status(result) shouldBe OK
    }
  }

  "simulateDesPost" should {

    val ackRef = "12345"

    "return a 200" in new Setup {
      when(mockDynamicStubConnector.simulateDesPost(ArgumentMatchers.eq(ackRef))(ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(200)))

      val result = await(controller.simulateDesPost(ackRef)(FakeRequest()))
      status(result) shouldBe OK
    }
  }

  "verifyEmail" should {

    val registrationId = "12345"
    val email = Email("testEmailAddress", "GG", true, true, true)
    val json = Json.obj("test" -> "ing")
    val brResponse = BusinessRegistrationSuccessResponse(BusinessRegistration(
      registrationId,
      "testTimeStamp",
      "en",
      Some("Director"),
      Links(None)
    ))

    "return a 200 and a successful email response" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some(registrationId))
      when(mockCompanyRegistrationConnector.retrieveEmail(ArgumentMatchers.eq(registrationId))(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(email)))
      when(mockCompanyRegistrationConnector.verifyEmail(ArgumentMatchers.eq(registrationId), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(json))
      when(mockBusinessRegistrationConnector.retrieveMetadata(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(brResponse))

      showWithAuthorisedUser(controller.verifyEmail(true)) {
        result =>
          status(result) shouldBe OK
          contentAsJson(await(result)) shouldBe json
      }
    }

    "return a 200 and an unsuccessful email response if an email couldn't be found" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some(registrationId))
      when(mockBusinessRegistrationConnector.retrieveMetadata(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(brResponse))
      when(mockCompanyRegistrationConnector.retrieveEmail(ArgumentMatchers.eq(registrationId))(ArgumentMatchers.any()))
        .thenReturn(Future.successful(None))

      showWithAuthorisedUser(controller.verifyEmail(true)) {
        result =>
          status(result) shouldBe OK
          contentAsJson(await(result)) shouldBe Json.parse("""{"message":"could not find an email for the current logged in user"}""")
      }
    }
  }

  "testEndpointSummary" should {

    "return a 200" in new Setup {
      val result = controller.testEndpointSummary(FakeRequest())
      status(result) shouldBe OK
    }
  }

  "dashboardStubbed" should {
    "return the dashboard when no parameters are passed into the function" in new Setup {
      when(mockDashboardService.getCurrentPayeThresholds)
        .thenReturn(Map("weekly" -> 1, "monthly" -> 1, "annually" -> 1))

      val result = await(controller.dashboardStubbed()(FakeRequest()))
      status(result) shouldBe 200
    }
    "return the dashboard when parameters are passed in" in new Setup {
      when(mockDashboardService.getCurrentPayeThresholds)
        .thenReturn(Map("weekly" -> 1, "monthly" -> 1, "annually" -> 1))

      val result = await(controller.dashboardStubbed("draft", "held", "true", "true", "draft", "true", "ackrefstatuses")(FakeRequest()))
      status(result) shouldBe 200
    }
  }
  "links" should {
    "return links when true true is passed in" in new Setup {
      controller.links(true, true) shouldBe ServiceLinks("regURL", "otrsURL", Some("restartUrl"), Some("cancelUrl"))
    }
    "return no links when false false is passed in" in new Setup {
      controller.links(false, false) shouldBe ServiceLinks("regURL", "otrsURL", None, None)
    }
  }

  "handOff6" should {

    val transId = "trans-id-12345"

    "return a 200 and display the confirmation refs as Json" in new Setup {

      val response = ConfirmationReferencesSuccessResponse(ConfirmationReferences(transId, Some("pay-ref-123"), Some("12"), ""))

      mockKeystoreFetchAndGet("registrationID", Some(registrationID))

      when(mockCompanyRegistrationConnector.updateReferences(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(response))

      showWithAuthorisedUser(controller.handOff6(Some(transId))) {
        result =>
          status(result) shouldBe 200
      }
    }

    "return a 400 if any error returns from company registration" in new Setup {

      val response = ConfirmationReferencesBadRequestResponse

      mockKeystoreFetchAndGet("registrationID", Some(registrationID))

      when(mockCompanyRegistrationConnector.updateReferences(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(response))

      showWithAuthorisedUser(controller.handOff6(Some(transId))) {
        result =>
          status(result) shouldBe 400
      }
    }
  }

  "generateTxId" should {
    "Return the given transaction ID if supplied " in new Setup {
      val result = controller.generateTxId(Some("a_txid"), "regid")
      result shouldBe "a_txid"
    }
    "Return generate a transaction ID based on the regid if no txid is supplied " in new Setup {
      val result = controller.generateTxId(None, "regid")
      result shouldBe "TRANS-ID-regid"
    }
  }
}