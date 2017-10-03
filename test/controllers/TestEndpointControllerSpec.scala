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

package controllers

import builders.AuthBuilder
import controllers.test.TestEndpointController
import fixtures.{CorporationTaxFixture, SCRSFixtures}
import helpers.{SCRSSpec, TestActorSystem}
import _root_.connectors._
import mocks.navModelRepoMock
import models._
import models.connectors.ConfirmationReferences
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.Json
import play.api.test.Helpers._
import models.handoff._
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}
import play.api.test.FakeRequest
import uk.gov.hmrc.http.cache.client.CacheMap
import utils.{BooleanFeatureSwitch, SCRSFeatureSwitches}
import services.MetaDataService
import org.mockito.Mockito._
import org.mockito.Matchers
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.test.WithFakeApplication

import scala.concurrent.Future

class TestEndpointControllerSpec extends SCRSSpec with SCRSFixtures with MockitoSugar
  with TestActorSystem with CorporationTaxFixture with navModelRepoMock with WithFakeApplication {

  val mockNavModelRepoObj = mockNavModelRepo
  val applicantData = AboutYouChoice("Director")
  val applicantDataEmpty = AboutYouChoice("")
  val applicantDataSeq  = Seq("completionCapacity" -> "Director")

  val cacheMap = CacheMap("", Map("" -> Json.toJson("")))

  val userIds = UserIDs("testInternal","testExternal")

  val mockSCRSFeatureSwitches = mock[SCRSFeatureSwitches]

  val mockMetaDataService = mock[MetaDataService]
  val mockDynamicStubConnector = mock[DynamicStubConnector]
  val mockBusinessRegistrationConnector = mock[BusinessRegistrationConnector]

  class Setup {
    val controller = new TestEndpointController {
      override val authConnector = mockAuthConnector
      override val s4LConnector = mockS4LConnector
      override val keystoreConnector = mockKeystoreConnector
      override val compRegConnector = mockCompanyRegistrationConnector
      override val scrsFeatureSwitches = mockSCRSFeatureSwitches
      override val metaDataService = mockMetaDataService
      val dynStubConnector = mockDynamicStubConnector
      val brConnector = mockBusinessRegistrationConnector
      val navModelMongo = mockNavModelRepoObj
      override val companyRegistrationConnector = mockCompanyRegistrationConnector
    }

    implicit val user = AuthBuilder.createTestUser
    implicit val hc = HeaderCarrier()

  }

  val registrationID = "testRegID"

  "TestEndpointController" should {
    "use the correct AuthConnector" in new Setup {
      controller.authConnector shouldBe a [AuthConnector]
    }
    "use the correct S4LConnector" in new Setup {
      controller.s4LConnector shouldBe a [S4LConnector]
    }
    "use the correct company registration connector" in new Setup {
      controller.compRegConnector shouldBe a [CompanyRegistrationConnector]
    }
  }

  "getAllS4LEntries" should {

    val corporationTaxModel = buildCorporationTaxModel()

    "Return a 200" in new Setup {
      when(mockAuthConnector.getIds[UserIDs](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(userIds))
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      when(mockMetaDataService.getApplicantData(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(applicantData))
      CTRegistrationConnectorMocks.retrieveCompanyDetails(Some(validCompanyDetailsResponse))
      mockS4LFetchAndGet[CompanyNameHandOffIncoming]("HandBackData", Some(validCompanyNameHandBack))
      CTRegistrationConnectorMocks.retrieveContactDetails(CompanyContactDetailsSuccessResponse(validCompanyContactDetailsResponse))
      CTRegistrationConnectorMocks.retrieveTradingDetails(Some(TradingDetails("true")))
      CTRegistrationConnectorMocks.retrieveAccountingDetails(validAccountingResponse)

      when(mockCompanyRegistrationConnector.retrieveCorporationTaxRegistration(Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(corporationTaxModel))

      AuthBuilder.showWithAuthorisedUser(controller.getAllS4LEntries, mockAuthConnector){

        result =>
          status(result) shouldBe OK
      }
    }

    "Return a 200 even if nothing is returned" in new Setup {
      when(mockAuthConnector.getIds[UserIDs](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(userIds))
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      when(mockMetaDataService.getApplicantData(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(applicantDataEmpty))
      CTRegistrationConnectorMocks.retrieveCompanyDetails(None)
      mockS4LFetchAndGet[CompanyNameHandOffIncoming]("HandBackData", None)
      mockS4LFetchAndGet[PPOBModel]("PPOB", None)
      CTRegistrationConnectorMocks.retrieveContactDetails(CompanyContactDetailsNotFoundResponse)
      CTRegistrationConnectorMocks.retrieveTradingDetails(None)
      CTRegistrationConnectorMocks.retrieveAccountingDetails(AccountingDetailsNotFoundResponse)
      when(mockCompanyRegistrationConnector.retrieveCorporationTaxRegistration(Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(corporationTaxModel))

      AuthBuilder.showWithAuthorisedUser(controller.getAllS4LEntries, mockAuthConnector){
        result =>
          status(result) shouldBe OK
      }
    }
  }

  "clearAllS4LEntries" should {
    "Return a 200" in new Setup {
      when(mockAuthConnector.getIds[UserIDs](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(userIds))
      mockS4LClear()
      AuthBuilder.showWithAuthorisedUser(controller.clearAllS4LEntries, mockAuthConnector){
        result =>
          status(result) shouldBe OK
      }
    }
  }

  "postAllS4LEntries" should {

    "Return a 303" in new Setup {
      when(mockAuthConnector.getIds[UserIDs](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(userIds))
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      when(mockMetaDataService.updateApplicantDataEndpoint(Matchers.eq(applicantData))(Matchers.any[HeaderCarrier]()))
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

      AuthBuilder.submitWithAuthorisedUser(controller.postAllS4LEntries, mockAuthConnector, request){
        result =>
          status(result) shouldBe SEE_OTHER
      }
    }
  }

  "clearKeystore" should {
    "remove all elements in the users keystore collection" in new Setup {
      when(mockAuthConnector.getIds[UserIDs](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(userIds))

      mockKeystoreClear()

      AuthBuilder.showWithAuthorisedUser(controller.clearKeystore, mockAuthConnector){
        result =>
          status(result) shouldBe OK
      }
    }
  }

  "showFeatureSwitch" should {

    val featureSwitchTrue = BooleanFeatureSwitch("cohoFirstHandOff", true)
    val featureSwitchFalse = BooleanFeatureSwitch("cohoFirstHandOff", false)

    "render the feature switch page when the system property for 'cohoFirstHandOff' is true" in new Setup {
      when(mockSCRSFeatureSwitches(Matchers.contains("cohoFirstHandOff"))).thenReturn(Some(featureSwitchTrue))

      val result = controller.showFeatureSwitch(FakeRequest())

      status(result) shouldBe OK
    }

    "render the feature switch page when the system property for 'cohoFirstHandOff' is false" in new Setup {
      when(mockSCRSFeatureSwitches(Matchers.contains("cohoFirstHandOff"))).thenReturn(Some(featureSwitchFalse))

      val result = controller.showFeatureSwitch(FakeRequest())

      status(result) shouldBe OK
    }

    "render the feature switch page when the system property for 'cohoFirstHandOff' is not found" in new Setup {
      when(mockSCRSFeatureSwitches(Matchers.contains("cohoFirstHandOff"))).thenReturn(None)
      val result = controller.showFeatureSwitch(FakeRequest())

      status(result) shouldBe OK
    }
  }

  "updateFeatureSwitch" should {

    "submit a valid feature switch form" in new Setup {
      val request = FakeRequest().withFormUrlEncodedBody("firstHandOff" -> "true", "legacyEnv" -> "true")
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
      mockInsertNavModel("foo",Some(navModel))
      mockKeystoreFetchAndGet[String]("registrationID",Some("foo"))
      mockKeystoreFetchAndGet[HandOffNavModel]("HandOffNavigation",None)
      mockGetNavModel(Some(navModel))
      val result = controller.setupTestNavModel(FakeRequest())
      status(result) shouldBe OK
    }
  }

  "checkSubmissionStatus" should {

    val registrationId = "testRegId"

    val ctRecord = Json.toJson(buildCorporationTaxModel(rid = registrationId))

    val heldRecord = Json.obj("submission" -> "testing")

    "fetch CT record and held submission record and return a 200" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some(registrationId))
      when(mockCompanyRegistrationConnector.retrieveCorporationTaxRegistration(Matchers.eq(registrationId))(Matchers.any()))
        .thenReturn(Future.successful(ctRecord))
      when(mockCompanyRegistrationConnector.fetchHeldSubmission(Matchers.eq(registrationId))(Matchers.any()))
        .thenReturn(Future.successful(Some(heldRecord)))

      AuthBuilder.showWithAuthorisedUser(controller.checkSubmissionStatus, mockAuthConnector){
        result =>
          status(result) shouldBe OK
      }
    }
  }

  "updateTimepoint" should {

    val timepoint = "12345"

    "return a 200" in new Setup {
      when(mockCompanyRegistrationConnector.updateTimepoint(Matchers.eq(timepoint))(Matchers.any()))
        .thenReturn(Future.successful(timepoint))

      val result = controller.updateTimepoint(timepoint)(FakeRequest())
      status(result) shouldBe OK
    }
  }

  "simulateDesPost" should {

    val ackRef = "12345"

    "return a 200" in new Setup {
      when(mockDynamicStubConnector.simulateDesPost(Matchers.eq(ackRef))(Matchers.any()))
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
      when(mockCompanyRegistrationConnector.retrieveEmail(Matchers.eq(registrationId))(Matchers.any()))
        .thenReturn(Future.successful(Some(email)))
      when(mockCompanyRegistrationConnector.verifyEmail(Matchers.eq(registrationId), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(json))
      when(mockBusinessRegistrationConnector.retrieveMetadata(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(brResponse))

      AuthBuilder.showWithAuthorisedUser(controller.verifyEmail(true), mockAuthConnector){
        result =>
          status(result) shouldBe OK
          jsonBodyOf(await(result)) shouldBe json
      }
    }

    "return a 200 and an unsuccessful email response if an email couldn't be found" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some(registrationId))
      when(mockBusinessRegistrationConnector.retrieveMetadata(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(brResponse))
      when(mockCompanyRegistrationConnector.retrieveEmail(Matchers.eq(registrationId))(Matchers.any()))
        .thenReturn(Future.successful(None))

      AuthBuilder.showWithAuthorisedUser(controller.verifyEmail(true), mockAuthConnector){
        result =>
          status(result) shouldBe OK
          jsonBodyOf(await(result)) shouldBe Json.parse("""{"message":"could not find an email for the current logged in user"}""")
      }
    }
  }

  "testEndpointSummary" should{

    "return a 200" in new Setup {
      val result = controller.testEndpointSummary(FakeRequest())
      status(result) shouldBe OK
    }
  }


  "dashboardStubbed" should{
    "return the dashboard when no parameters are passed into the function" in new Setup {
      val result = await(controller.dashboardStubbed()(FakeRequest()))
      status(result) shouldBe 200
    }
    "return the dashboard when parameters are passed in" in new Setup {
      val result = await(controller.dashboardStubbed("draft","held","false")(FakeRequest()))
      status(result) shouldBe 200
    }
  }

  "handOff6" should {

    val transId = "trans-id-12345"

    "return a 200 and display the confirmation refs as Json" in new Setup {

      val response = ConfirmationReferencesSuccessResponse(ConfirmationReferences(transId, "pay-ref-123", "12", ""))

      mockKeystoreFetchAndGet("registrationID", Some(registrationID))

      when(mockCompanyRegistrationConnector.updateReferences(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(response))

      AuthBuilder.showWithAuthorisedUser(controller.handOff6(transId), mockAuthConnector){
        result =>
          status(result) shouldBe 200
      }
    }

    "return a 400 if any error returns from company registration" in new Setup {

      val response = ConfirmationReferencesBadRequestResponse

      mockKeystoreFetchAndGet("registrationID", Some(registrationID))

      when(mockCompanyRegistrationConnector.updateReferences(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(response))

      AuthBuilder.showWithAuthorisedUser(controller.handOff6(transId), mockAuthConnector){
        result =>
          status(result) shouldBe 400
      }
    }
  }
}
