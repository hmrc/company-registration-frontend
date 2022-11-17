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

package services

import java.time.LocalDate

import builders.AuthBuilder
import config.AppConfig
import connectors.{NotStarted, SuccessfulResponse}
import helpers.SCRSSpec
import mocks.ServiceConnectorMock
import models._
import models.auth.AuthDetails
import models.connectors.ConfirmationReferences
import models.external.{OtherRegStatus, Statuses}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.{AffinityGroup, Enrolment, EnrolmentIdentifier, Enrolments}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.audit.http.connector.AuditResult.Success
import utils.{BooleanFeatureSwitch, SCRSFeatureSwitches}

import scala.concurrent.{ExecutionContext, Future}

class DashboardServiceSpec extends SCRSSpec with ServiceConnectorMock with AuthBuilder with GuiceOneAppPerSuite {

  val payeTestBaseUrl = "test"
  val payeTestUri = "/paye-uri"
  val vatTestBaseUrl = "test"
  val vatTestUri = "/vat-uri"
  val payeUrl = s"$payeTestBaseUrl$payeTestUri"
  val vatUrl = s"$vatTestBaseUrl$vatTestUri"
  val testOtrsUrl = "OTRS url"

  val mockfeatureFlag = mock[SCRSFeatureSwitches]
  val mockLoggingDays = "MON,TUE,WED,THU,FRI"
  val mockLoggingTimes = "08:00:00_17:00:00"

  trait Setup {
    val service = new DashboardService {
      override val companyRegistrationConnector = mockCompanyRegistrationConnector
      override val keystoreConnector = mockKeystoreConnector
      override val incorpInfoConnector = mockIncorpInfoConnector
      override val payeConnector = mockServiceConnector
      override val vatConnector = mockServiceConnector
      override val auditConnector = mockAuditConnector
      override val otrsUrl = testOtrsUrl
      override val payeBaseUrl = payeTestBaseUrl
      override val payeUri = payeTestUri
      override val vatBaseUrl = vatTestBaseUrl
      override val vatUri = vatTestUri
      override val featureFlag = mockfeatureFlag
      override val loggingDays = mockLoggingDays
      override val loggingTimes = mockLoggingTimes
      override val thresholdService: ThresholdService = mockThresholdService
      override val appConfig: AppConfig = app.injector.instanceOf[AppConfig]
      override val auditService = mockAuditService

    }
  }


  class SetupWithDash(dash: IncorpAndCTDashboard) {
    val service = new DashboardService {
      override val companyRegistrationConnector = mockCompanyRegistrationConnector
      override val keystoreConnector = mockKeystoreConnector
      override val incorpInfoConnector = mockIncorpInfoConnector
      override val payeConnector = mockServiceConnector
      override val vatConnector = mockServiceConnector
      override val auditConnector = mockAuditConnector
      override val otrsUrl = testOtrsUrl
      override val payeBaseUrl = payeTestBaseUrl
      override val payeUri = payeTestUri
      override val vatBaseUrl = vatTestBaseUrl
      override val vatUri = vatTestUri
      override val featureFlag = mockfeatureFlag
      override val loggingDays = mockLoggingDays
      override val loggingTimes = mockLoggingTimes
      override val thresholdService: ThresholdService = mockThresholdService
      override val appConfig: AppConfig = app.injector.instanceOf[AppConfig]
      override val auditService = mockAuditService


      override def buildIncorpCTDashComponent(regId: String, enrolments: Enrolments)(implicit hc: HeaderCarrier): Future[IncorpAndCTDashboard]
      = Future.successful(dash)

      override def getCompanyName(regId: String)(implicit hc: HeaderCarrier) = Future.successful("testCompanyName")

      override def buildVATDashComponent(regId: String, enrolments: Enrolments)(implicit hc: HeaderCarrier): Future[ServiceDashboard]
      = Future.successful(ServiceDashboard("notEnabled", None, None, ServiceLinks("test/vat-uri", "OTRS url", None, None), Some(vatThresholds)))
    }
  }

  override protected def afterEach(): Unit = {
    super.afterEach()
    System.setProperty("feature.system-date", "")
  }

  val regId = "regID-12345"
  val transId = "txID-12345"
  val payRef = "payRef-12345"
  val ackRef = "ackRef-12345"

  def ctRegJson(status: String): JsValue = Json.parse(
    s"""
       |{
       |    "internalId" : "Int-xxx-xxx-xxx",
       |    "registrationID" : "$regId",
       |    "status" : "$status",
       |    "formCreationTimestamp" : "2017-04-25T16:19:29+01:00",
       |    "language" : "en",
       |    "registrationProgress" : "HO5",
       |    "accountingDetails" : {
       |        "accountingDateStatus" : "WHEN_REGISTERED"
       |    },
       |    "accountsPreparation" : {
       |        "businessEndDateChoice" : "HMRC_DEFINED"
       |    },
       |    "verifiedEmail" : {
       |        "address" : "foo@bar.wibble",
       |        "type" : "GG",
       |        "link-sent" : true,
       |        "verified" : true,
       |        "return-link-email-sent" : true
       |    },
       |    "createdTime" : 1493133569538,
       |    "lastSignedIn" : 1493133581149
       |}
    """.stripMargin).as[JsObject] ++ addConfRefs(status) ++ addAckRefs(status)

  def addConfRefs(status: String): JsValue = {
    if (status != "draft") {
      Json.parse(
        s"""
           |{
           |  "confirmationReferences" : {
           |      "acknowledgement-reference":"$ackRef",
           |      "transaction-id" : "$transId",
           |      "payment-reference" : "$payRef",
           |      "payment-amount" : "12"
           |  }
           |}
      """.stripMargin)
    } else {
      Json.obj()
    }
  }

  def addAckRefs(status: String): JsValue = {
    if (status == "acknowledged") {
      Json.parse(
        """
          |{
          |  "acknowledgementReferences" : {
          |      "status" : "04",
          |      "ctUtr" : "CTUTR"
          |  }
          |}
        """.stripMargin)
    } else {
      Json.obj()
    }
  }

  def mockVatFeature(enable: Boolean) = when(mockfeatureFlag.vat).thenReturn(BooleanFeatureSwitch("vat", enabled = enable))

  def ctEnrolment(id: String, active: Boolean) = Enrolments(Set(Enrolment("IR-CT", Seq(EnrolmentIdentifier("UTR", id)), if (active) "activated" else "other")))

  val payeEnrolment = Enrolments(Set(Enrolment("IR-PAYE", Seq(EnrolmentIdentifier("test-paye-identifier", "test-paye-value")), "testState")))
  val vatEnrolment = Enrolments(Set(Enrolment("HMCE-VATDEC-ORG", Seq(EnrolmentIdentifier("test-paye-identifier", "test-paye-value")), "testState")))
  val vatVarEnrolment = Enrolments(Set(Enrolment("HMCE-VATVAR-ORG", Seq(EnrolmentIdentifier("test-paye-identifier", "test-paye-value")), "testState")))
  val noEnrolments = Enrolments(Set())
  val ctAndVatEnrolment = Enrolments(Set(Enrolment("IR-CT", Seq(EnrolmentIdentifier("UTR", "1234567890")), "activated"),
    Enrolment("HMCE-VATDEC-ORG", Seq(EnrolmentIdentifier("test-paye-identifier", "test-paye-value")), "testState")))

  val payeThresholds = Map("weekly" -> 120, "monthly" -> 520, "annually" -> 6240)
  val vatThresholds = Map("yearly" -> 85000)
  "buildDashboard" should {

    val draftDash = IncorpAndCTDashboard("draft", Some("10 October 2017"), Some(transId), Some(payRef), None, None, Some(ackRef), None, None)
    val rejectedDash = IncorpAndCTDashboard("rejected", Some("10 October 2017"), Some(transId), Some(payRef), None, None, Some(ackRef), None, None)
    val heldDash = IncorpAndCTDashboard("held", Some("10 October 2017"), Some(transId), Some(payRef), None, None, Some(ackRef), None, None)

    val payeDash = ServiceDashboard("", None, None, ServiceLinks(payeUrl, "OTRS url", None, Some("/register-your-company/cancel-paye")), Some(payeThresholds))
    val payeStatus = OtherRegStatus("", None, None, Some("foo"), None)
    val vatDashOTRS = ServiceDashboard("notEnabled", None, None, ServiceLinks("test/vat-uri", "OTRS url", None, None), Some(vatThresholds))


    "return a CouldNotBuild DashboardStatus when the status of the registration is draft" in new SetupWithDash(draftDash) {
      getStatusMock(regId)(SuccessfulResponse(payeStatus))

      mockVatFeature(false)
      val res = await(service.buildDashboard(regId, noEnrolments))
      res mustBe CouldNotBuild
    }

    "return a RejectedIncorp DashboardStatus when the status of the registration is rejected" in new SetupWithDash(rejectedDash) {
      getStatusMock(regId)(SuccessfulResponse(payeStatus))

      mockVatFeature(false)
      val res = await(service.buildDashboard(regId, noEnrolments))
      res mustBe RejectedIncorp
    }

    "return a DashboardBuilt DashboardStatus when the status of the registration is any other status" in new SetupWithDash(heldDash) {
      getStatusMock(regId)(SuccessfulResponse(payeStatus))
      when(mockThresholdService.fetchCurrentPayeThresholds()).thenReturn(Map("weekly" -> 120, "monthly" -> 520, "annually" -> 6240))
      when(mockIncorpInfoConnector.getCompanyName(eqTo(transId))(any(), any())).thenReturn(Future.successful("testCompanyName"))

      mockVatFeature(false)
      val res = await(service.buildDashboard(regId, vatEnrolment))
      res mustBe DashboardBuilt(Dashboard("testCompanyName", heldDash, payeDash, vatDashOTRS, hasVATCred = true))
    }
  }

  "buildIncorpCTDashComponent" should {

    val date = LocalDate.parse("2017-10-10")
    val dateAsJson = Json.toJson(date)

    "return a correct IncorpAndCTDashboard when the status is held" in new Setup {
      when(mockCompanyRegistrationConnector.retrieveCorporationTaxRegistration(eqTo(regId))(any(), any())).thenReturn(Future.successful(ctRegJson("held")))
      when(mockCompanyRegistrationConnector.fetchHeldSubmissionTime(eqTo(regId))(any(), any())).thenReturn(Future.successful(Some(dateAsJson)))

      val res = await(service.buildIncorpCTDashComponent(regId, noEnrolments))
      res mustBe IncorpAndCTDashboard("held", Some("10 October 2017"), Some(transId), Some(payRef), None, None, Some(ackRef), None, None)
    }
    "return a correct IncorpAndCTDashboard when the status is locked" in new Setup {
      when(mockCompanyRegistrationConnector.retrieveCorporationTaxRegistration(eqTo(regId))(any(), any())).thenReturn(Future.successful(ctRegJson("locked")))
      when(mockCompanyRegistrationConnector.fetchHeldSubmissionTime(eqTo(regId))(any(), any())).thenReturn(Future.successful(None))

      val res = await(service.buildIncorpCTDashComponent(regId, noEnrolments))
      res mustBe IncorpAndCTDashboard("locked", None, Some(transId), Some(payRef), None, None, Some(ackRef), None, None)
    }

    "return a correct IncorpAndCTDashboard when the status is submitted" in new Setup {
      when(mockCompanyRegistrationConnector.retrieveCorporationTaxRegistration(eqTo(regId))(any(), any()))
        .thenReturn(Future.successful(ctRegJson("submitted")))

      val res = await(service.buildIncorpCTDashComponent(regId, noEnrolments))
      res mustBe IncorpAndCTDashboard("submitted", None, Some(transId), Some(payRef), None, None, Some(ackRef), None, None)
    }

    "return a correct IncorpAndCTDashboard when the status is acknowledged and enrolment has no CTUTR" in new Setup {
      when(mockCompanyRegistrationConnector.retrieveCorporationTaxRegistration(eqTo(regId))(any(), any())).thenReturn(Future.successful(ctRegJson("acknowledged")))

      val res = await(service.buildIncorpCTDashComponent(regId, noEnrolments))
      res mustBe IncorpAndCTDashboard("acknowledged", None, Some(transId), Some(payRef), None, None, Some(ackRef), Some("04"), None)
    }

    def acknowledgedDashboard(ctutr: Option[String]) = IncorpAndCTDashboard("acknowledged", None, Some(transId), Some(payRef), None, None, Some(ackRef), Some("04"), ctutr)

    "return a correct IncorpAndCTDashboard when the status is acknowledged with a matching CTUTR on enrolment" in new Setup {
      when(mockCompanyRegistrationConnector.retrieveCorporationTaxRegistration(eqTo(regId))(any(), any())).thenReturn(Future.successful(ctRegJson("acknowledged")))

      val res = await(service.buildIncorpCTDashComponent(regId, ctEnrolment("CTUTR", active = true)))
      res mustBe acknowledgedDashboard(Some("CTUTR"))
    }

    "return a correct IncorpAndCTDashboard when the status is acknowledged but there is an inactive enrolment" in new Setup {
      when(mockCompanyRegistrationConnector.retrieveCorporationTaxRegistration(eqTo(regId))(any(), any())).thenReturn(Future.successful(ctRegJson("acknowledged")))

      val res = await(service.buildIncorpCTDashComponent(regId, ctEnrolment("CTUTR", active = false)))
      res mustBe acknowledgedDashboard(None)
    }

    "ignore UTR in IncorpAndCTDashboard when the status is acknowledged and our CTUTR doesn't match an active enrolment" in new Setup {
      when(mockCompanyRegistrationConnector.retrieveCorporationTaxRegistration(eqTo(regId))(any(), any())).thenReturn(Future.successful(ctRegJson("acknowledged")))

      val res = await(service.buildIncorpCTDashComponent(regId, ctEnrolment("mismatched UTR", active = true)))
      res mustBe acknowledgedDashboard(None)
    }
  }

  "buildPAYEDashComponent" should {

    val payeLinks = ServiceLinks(payeUrl, testOtrsUrl, None, None)

    "return a Status when one is fetched from paye-registration with cancelURL" in new Setup {
      val payeStatus = OtherRegStatus("held", None, None, Some("foo"), None)
      val payeDash = ServiceDashboard("held", None, None, ServiceLinks(payeUrl, testOtrsUrl, None, Some("/register-your-company/cancel-paye")), Some(payeThresholds))
      getStatusMock(regId)(SuccessfulResponse(payeStatus))
      when(mockThresholdService.fetchCurrentPayeThresholds()).thenReturn(Map("weekly" -> 120, "monthly" -> 520, "annually" -> 6240))

      val result = await(service.buildPAYEDashComponent(regId, payeEnrolment))
      result mustBe payeDash
    }

    "return a Status when one is fetched from paye-registration with restartURL" in new Setup {
      val payeStatus = OtherRegStatus("rejected", None, None, None, Some("bar"))
      val payeDash = ServiceDashboard("rejected", None, None, ServiceLinks(payeUrl, testOtrsUrl, Some("bar"), None), Some(payeThresholds))
      getStatusMock(regId)(SuccessfulResponse(payeStatus))
      when(mockThresholdService.fetchCurrentPayeThresholds()).thenReturn(Map("weekly" -> 120, "monthly" -> 520, "annually" -> 6240))

      val result = await(service.buildPAYEDashComponent(regId, payeEnrolment))
      result mustBe payeDash
    }

    "return an ineligible Status when nothing is fetched from paye-registration and the user already has a PAYE enrolment" in new Setup {
      val payeDash = ServiceDashboard(Statuses.NOT_ELIGIBLE, None, None, payeLinks, Some(payeThresholds))
      getStatusMock(regId)(NotStarted)
      when(mockThresholdService.fetchCurrentPayeThresholds()).thenReturn(Map("weekly" -> 120, "monthly" -> 520, "annually" -> 6240))

      val result = await(service.buildPAYEDashComponent(regId, payeEnrolment))
      result mustBe payeDash
    }

    "return a not started Status when nothing is fetched from paye-registration and the user does not have a PAYE enrolment" in new Setup {
      val payeDash = ServiceDashboard(Statuses.NOT_STARTED, None, None, payeLinks, Some(payeThresholds))
      getStatusMock(regId)(NotStarted)
      when(mockThresholdService.fetchCurrentPayeThresholds()).thenReturn(Map("weekly" -> 120, "monthly" -> 520, "annually" -> 6240))

      val result = await(service.buildPAYEDashComponent(regId, noEnrolments))
      result mustBe payeDash
    }

    "return a not started Status when nothing is fetched from paye-registration and the user does not have a PAYE enrolment but has a IR-CT enrolement" in new Setup {
      val payeDash = ServiceDashboard(Statuses.NOT_STARTED, None, None, payeLinks, Some(payeThresholds))
      getStatusMock(regId)(NotStarted)
      when(mockThresholdService.fetchCurrentPayeThresholds()).thenReturn(Map("weekly" -> 120, "monthly" -> 520, "annually" -> 6240))

      val result = await(service.buildPAYEDashComponent(regId, ctEnrolment("1234567890", true)))
      result mustBe payeDash
    }
  }

  "hasEnrollment" should {

    "return true when an enrolment already exists for the user" in new Setup {
      val result = await(service.hasEnrolment(payeEnrolment, List("IR-PAYE")))
      result mustBe true
    }

    "return false" when {

      "enrolments are fetched but does not contain one for PAYE" in new Setup {
        val result = await(service.hasEnrolment(noEnrolments, List("IR-PAYE")))
        result mustBe false
      }

      "there is an auth IR-CT enrolment is present when IR-PAYE is not allowsed" in new Setup {
        val result = await(service.hasEnrolment(ctEnrolment("1234567890", true), List("IR-PAYE")))
        result mustBe false
      }
      "there is an auth IR-CT and VAT enrolment and IR-PAYE is not allowsed" in new Setup {
        val result = await(service.hasEnrolment(ctAndVatEnrolment, List("IR-PAYE")))
        result mustBe false
      }

    }
  }

  "getCompanyName" should {
    val companyName = "testCompanyName"

    "return the company name returned from incorporation information" in new Setup {
      when(mockIncorpInfoConnector.getCompanyName(eqTo(transId))(any(), any())).thenReturn(Future.successful(companyName))

      val res = await(service.getCompanyName(transId))
      res mustBe companyName

    }

    "return an empty company name when no transId is given" in new Setup {
      when(mockIncorpInfoConnector.getCompanyName(eqTo(""))(any(), any())).thenReturn(Future.successful(""))
      await(service.getCompanyName("")) mustBe ""
    }
  }

  "buildHeld" should {
    "return a IncorpAndCTDashboard where submission date is returned from cr" in new Setup {
      val expected = IncorpAndCTDashboard("held", Some("10 October 2017"), Some(transId),
        Some(payRef), None, None, Some(ackRef), None, None)

      val date = LocalDate.parse("2017-10-10")
      val dateAsJson = Json.toJson(date)

      when(mockCompanyRegistrationConnector.fetchHeldSubmissionTime(eqTo(regId))(any(), any()))
        .thenReturn(Future.successful(Some(dateAsJson)))

      val result = await(service.buildHeld(regId, ctRegJson("held")))
      result mustBe expected
    }
    "return a IncorpAndCTDashboard where submission date is NOT returned from cr" in new Setup {
      val expected = IncorpAndCTDashboard("locked", None, Some(transId),
        Some(payRef), None, None, Some(ackRef), None, None)

      when(mockCompanyRegistrationConnector.fetchHeldSubmissionTime(eqTo(regId))(any(), any()))
        .thenReturn(Future.successful(None))

      val result = await(service.buildHeld(regId, ctRegJson("locked")))
      result mustBe expected
    }

  }

  "extractSubmissionDate" should {
    "convert a DateTime as Json and convert it into a dd MMMM yyyy format string" in new Setup {
      val date = LocalDate.parse("2017-10-10")
      val dateAsJson = Json.toJson(date)

      service.extractSubmissionDate(dateAsJson) mustBe "10 October 2017"
    }
  }

  val authDetails = AuthDetails(
    AffinityGroup.Organisation,
    Enrolments(Set()),
    "||||anyemail@fakemail.fake",
    "extID",
    "authProviderId"
  )

  implicit val req = FakeRequest("GET", "/test-path")

  val email = Email("testEmailAddress", "GG", true, true, true)

  "checkForEmailMismatch" should {
    "send an email mismatch audit event" when {
      "there has been no email mismatch audit event sent in the session, and the emails mismatch" in new Setup {
        when(mockKeystoreConnector.fetchAndGet(eqTo("emailMismatchAudit"))(any(), any()))
          .thenReturn(Future.successful(None))


        when(mockAuditService.emailMismatchEventDetail(ArgumentMatchers.any(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any(),ArgumentMatchers.any(),ArgumentMatchers.any()))
          .thenReturn(Future.successful(Success))

        when(mockCompanyRegistrationConnector.retrieveEmail(any())(any(), any()))
          .thenReturn(Future.successful(Some(email)))

        when(mockKeystoreConnector.cache(eqTo("emailMismatchAudit"), any())(any(), any()))
          .thenReturn(Future.successful(CacheMap("x", Map())))

        await(service.checkForEmailMismatch(regId, authDetails)) mustBe true
      }
    }

    "do not send an audit event" when {
      "there has been no email mismatch audit event sent in the session, and the emails do not mismatch" in new Setup {
        val matchingEmail = "matchingEmail"

        when(mockKeystoreConnector.fetchAndGet(eqTo("emailMismatchAudit"))(any(), any()))
          .thenReturn(Future.successful(None))

        when(mockCompanyRegistrationConnector.retrieveEmail(any())(any(), any()))
          .thenReturn(Future.successful(Some(email.copy(address = matchingEmail))))

        when(mockKeystoreConnector.cache(eqTo("emailMismatchAudit"), any())(any(), any()))
          .thenReturn(Future.successful(CacheMap("x", Map())))

        await(service.checkForEmailMismatch(regId, authDetails.copy(email = matchingEmail))) mustBe false

        verify(mockAuditConnector, times(0)).sendExtendedEvent(ArgumentMatchers.any())(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[ExecutionContext]())
      }

      "there has been no email mismatch audit event sent in the session, and there is no verified email in CR" in new Setup {
        when(mockKeystoreConnector.fetchAndGet(eqTo("emailMismatchAudit"))(any(), any()))
          .thenReturn(Future.successful(None))

        when(mockCompanyRegistrationConnector.retrieveEmail(any())(any(), any()))
          .thenReturn(Future.successful(None))

        await(service.checkForEmailMismatch(regId, authDetails)) mustBe false

        verify(mockAuditConnector, times(0)).sendExtendedEvent(ArgumentMatchers.any())(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[ExecutionContext]())
      }

      "there has been an email mismatch audit event sent in the session, and it mismatched" in new Setup {
        when(mockKeystoreConnector.fetchAndGet[Boolean](eqTo("emailMismatchAudit"))(any(), any()))
          .thenReturn(Future.successful(Some(true)))

        await(service.checkForEmailMismatch(regId, authDetails)) mustBe true
      }

      "there has been an email mismatch audit event sent in the session, and it did not mismatch" in new Setup {
        when(mockKeystoreConnector.fetchAndGet[Boolean](eqTo("emailMismatchAudit"))(any(), any()))
          .thenReturn(Future.successful(Some(false)))

        await(service.checkForEmailMismatch(regId, authDetails)) mustBe false
      }
    }
  }
}
