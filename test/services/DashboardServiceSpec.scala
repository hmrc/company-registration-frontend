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

package services

import helpers.{AuthHelpers, SCRSSpec}
import models.auth.{Enrolment, EnrolmentIdentifier}
import connectors.{NotStarted, SuccessfulResponse}
import models._
import models.connectors.ConfirmationReferences
import models.external.{OtherRegStatus, Statuses}
import org.joda.time.DateTime
import play.api.libs.json.{JsObject, JsValue, Json}
import org.mockito.Mockito._
import org.mockito.Matchers.{any, eq => eqTo}
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpException}
import utils.{BooleanFeatureSwitch, SCRSFeatureSwitches}

import scala.concurrent.Future

class DashboardServiceSpec extends SCRSSpec with AuthHelpers {

  implicit val auth = buildAuthContext

  val payeTestBaseUrl = "test"
  val payeTestUri = "/paye-uri"
  val vatTestBaseUrl = "test"
  val vatTestUri = "/vat-uri"
  val payeUrl = s"$payeTestBaseUrl$payeTestUri"
  val vatUrl = s"$vatTestBaseUrl$vatTestUri"
  val testOtrsUrl = "OTRS url"

  val mockfeatureFlag = mock[SCRSFeatureSwitches]

  trait Setup {
    val service = new DashboardService {
      override val companyRegistrationConnector = mockCompanyRegistrationConnector
      override val keystoreConnector = mockKeystoreConnector
      override val incorpInfoConnector = mockIncorpInfoConnector
      override val authConnector = mockAuthConnector
      override val payeConnector = mockServiceConnector
      override val vatConnector = mockServiceConnector
      override val otrsUrl = testOtrsUrl
      override val payeBaseUrl = payeTestBaseUrl
      override val payeUri = payeTestUri
      override val vatBaseUrl = vatTestBaseUrl
      override val vatUri = vatTestUri
      override val featureFlag = mockfeatureFlag
    }
  }


  class SetupWithDash(dash: IncorpAndCTDashboard) {
    val service = new DashboardService {
      override val companyRegistrationConnector = mockCompanyRegistrationConnector
      override val keystoreConnector = mockKeystoreConnector
      override val incorpInfoConnector = mockIncorpInfoConnector
      override val authConnector = mockAuthConnector
      override val payeConnector = mockServiceConnector
      override val vatConnector = mockServiceConnector
      override val otrsUrl = testOtrsUrl
      override val payeBaseUrl = payeTestBaseUrl
      override val payeUri = payeTestUri
      override val vatBaseUrl = vatTestBaseUrl
      override val vatUri = vatTestUri
      override val featureFlag = mockfeatureFlag

      override def buildIncorpCTDashComponent(regId: String)(implicit hc: HeaderCarrier) = Future.successful(dash)
      override def getCompanyName(regId: String)(implicit hc: HeaderCarrier) = Future.successful("testCompanyName")
     }
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
        if(status != "draft"){
            Json.parse(s"""
        |{
        |  "confirmationReferences" : {
        |      "acknowledgement-reference":"$ackRef",
        |      "transaction-id" : "$transId",
        |      "payment-reference" : "$payRef",
        |      "payment-amount" : "12"
        |  }
        |}
      """.stripMargin)
          } else { Json.obj() }
      }

      def addAckRefs(status: String): JsValue = {
        if(status == "acknowledged") {
            Json.parse("""
        |{
        |  "acknowledgementReferences" : {
        |      "status" : "04"
        |  }
        |}
      """.stripMargin)
          } else { Json.obj() }
      }

  def mockPayeFeature(enable: Boolean) = when(mockfeatureFlag.paye).thenReturn(BooleanFeatureSwitch("paye", enabled = enable))
  def mockVatFeature(enable: Boolean) = when(mockfeatureFlag.vat).thenReturn(BooleanFeatureSwitch("vat", enabled = enable))

  val payeEnrolment = Enrolment("IR-PAYE", Seq(EnrolmentIdentifier("test-paye-identifier", "test-paye-value")), "testState")
  val vatEnrolment = Enrolment("HMCE-VATDEC-ORG", Seq(EnrolmentIdentifier("test-paye-identifier", "test-paye-value")), "testState")
  val vatVarEnrolment = Enrolment("HMCE-VATVAR-ORG", Seq(EnrolmentIdentifier("test-paye-identifier", "test-paye-value")), "testState")

  "buildDashboard" should {

    val draftDash = IncorpAndCTDashboard("draft", Some("10 October 2017"), Some(transId), Some(payRef), None, None, Some(ackRef), None)
    val rejectedDash = IncorpAndCTDashboard("rejected", Some("10 October 2017"), Some(transId), Some(payRef), None, None, Some(ackRef), None)
    val heldDash = IncorpAndCTDashboard("held", Some("10 October 2017"), Some(transId), Some(payRef), None, None, Some(ackRef), None)

    val payeDash = ServiceDashboard("", None, None, ServiceLinks(payeUrl, "OTRS url", None, Some("/register-your-company/cancel-paye")))
    val payeStatus = OtherRegStatus("", None, None, Some("foo"), None)

    "return a CouldNotBuild DashboardStatus when the status of the registration is draft" in new SetupWithDash(draftDash) {
      when(mockServiceConnector.getStatus(any())(any())).thenReturn(Future.successful(SuccessfulResponse(payeStatus)))
      when(mockAuthConnector.getEnrolments[Option[Seq[Enrolment]]](any())(any(), any()))
        .thenReturn(Future.successful(Some(Seq(vatEnrolment))))

      mockPayeFeature(true)
      mockVatFeature(false)
      val res = await(service.buildDashboard(regId))
      res shouldBe CouldNotBuild
    }

    "return a RejectedIncorp DashboardStatus when the status of the registration is rejected" in new SetupWithDash(rejectedDash) {
      when(mockServiceConnector.getStatus(any())(any())).thenReturn(Future.successful(SuccessfulResponse(payeStatus)))
      when(mockAuthConnector.getEnrolments[Option[Seq[Enrolment]]](any())(any(), any()))
        .thenReturn(Future.successful(Some(Seq(vatEnrolment))))

      mockPayeFeature(true)
      mockVatFeature(false)
      val res = await(service.buildDashboard(regId))
      res shouldBe RejectedIncorp
    }

    "return a DashboardBuilt DashboardStatus when the status of the registration is any other status" in new SetupWithDash(heldDash) {
      when(mockServiceConnector.getStatus(any())(any())).thenReturn(Future.successful(SuccessfulResponse(payeStatus)))
      when(mockAuthConnector.getEnrolments[Option[Seq[Enrolment]]](any())(any(), any()))
        .thenReturn(Future.successful(Some(Seq(vatEnrolment))))

      mockPayeFeature(true)
      mockVatFeature(false)
      val res = await(service.buildDashboard(regId))
//      res shouldBe DashboardBuilt(Dashboard(heldDash, "testCompanyName")) //todo: company name set as blank until story is played to put it back in
      res shouldBe DashboardBuilt(Dashboard("", heldDash, payeDash, None, hasVATCred = true))
    }
  }

  "buildIncorpCTDashComponent" should {

    val date = DateTime.parse("2017-10-10")
    val dateAsJson = Json.toJson(date)

    "return a correct IncorpAndCTDashboard when the status is held" in new Setup {
      when(mockCompanyRegistrationConnector.retrieveCorporationTaxRegistration(eqTo(regId))(any())).thenReturn(Future.successful(ctRegJson("held")))
      when(mockCompanyRegistrationConnector.fetchHeldSubmissionTime(eqTo(regId))(any())).thenReturn(Future.successful(Some(dateAsJson)))

      val res = await(service.buildIncorpCTDashComponent(regId))
      res shouldBe IncorpAndCTDashboard("held", Some("10 October 2017"), Some(transId), Some(payRef), None, None, Some(ackRef), None)
    }

    "return a correct IncorpAndCTDashboard when the status is submitted" in new Setup {
      when(mockCompanyRegistrationConnector.retrieveCorporationTaxRegistration(eqTo(regId))(any()))
        .thenReturn(Future.successful(ctRegJson("submitted")))

      val res = await(service.buildIncorpCTDashComponent(regId))
      res shouldBe IncorpAndCTDashboard("submitted", None, Some(transId), Some(payRef), None, None, Some(ackRef), None)
          }

    "return a correct IncorpAndCTDashboard when the status is acknowledged" in new Setup {
      when(mockCompanyRegistrationConnector.retrieveCorporationTaxRegistration(eqTo(regId))(any())).thenReturn(Future.successful(ctRegJson("acknowledged")))

      val res = await(service.buildIncorpCTDashComponent(regId))
      res shouldBe IncorpAndCTDashboard("acknowledged", None, Some(transId), Some(payRef), None, None, Some(ackRef), Some("04"))
    }
  }

  "buildPAYEDashComponent" should {

    val payeLinks = ServiceLinks(payeUrl, testOtrsUrl, None, None)

    "return a Status when one is fetched from paye-registration with cancelURL" in new Setup {
      val payeStatus = OtherRegStatus("held", None, None, Some("foo"), None)
      val payeDash = ServiceDashboard("held", None, None, ServiceLinks(payeUrl, testOtrsUrl, None, Some("/register-your-company/cancel-paye")))
      mockPayeFeature(true)
      when(mockServiceConnector.getStatus(any())(any())).thenReturn(Future.successful(SuccessfulResponse(payeStatus)))

      val result = await(service.buildPAYEDashComponent(regId))
      result shouldBe payeDash
    }

    "return a Status when one is fetched from paye-registration with restartURL" in new Setup {
      val payeStatus = OtherRegStatus("rejected", None, None, None, Some("bar"))
      val payeDash = ServiceDashboard("rejected", None, None, ServiceLinks(payeUrl, testOtrsUrl, Some("bar"), None))
      mockPayeFeature(true)
      when(mockServiceConnector.getStatus(any())(any())).thenReturn(Future.successful(SuccessfulResponse(payeStatus)))

      val result = await(service.buildPAYEDashComponent(regId))
      result shouldBe payeDash
    }

    "return an ineligible Status when nothing is fetched from paye-registration and the user already has a PAYE enrolment" in new Setup {
      val payeDash = ServiceDashboard(Statuses.NOT_ELIGIBLE, None, None, payeLinks)
      mockPayeFeature(true)
      when(mockServiceConnector.getStatus(any())(any())).thenReturn(Future.successful(NotStarted))
      when(mockAuthConnector.getEnrolments[Option[Seq[Enrolment]]](any())(any(), any()))
        .thenReturn(Future.successful(Some(Seq(payeEnrolment))))

      val result = await(service.buildPAYEDashComponent(regId))
      result shouldBe payeDash
    }

    "return a not started Status when nothing is fetched from paye-registration and the user does not have a PAYE enrolment" in new Setup {
      val payeDash = ServiceDashboard(Statuses.NOT_STARTED, None, None, payeLinks)
      mockPayeFeature(true)
      when(mockServiceConnector.getStatus(any())(any())).thenReturn(Future.successful(NotStarted))
      when(mockAuthConnector.getEnrolments[Option[Seq[Enrolment]]](any())(any(), any()))
        .thenReturn(Future.successful(Some(Seq())))

      val result = await(service.buildPAYEDashComponent(regId))
      result shouldBe payeDash
    }

    "return a not enabled Status when the paye feature is turned off" in new Setup {
      val payeDash = ServiceDashboard(Statuses.NOT_ENABLED, None, None, payeLinks)
      mockPayeFeature(false)

      val result = await(service.buildPAYEDashComponent(regId))
      result shouldBe payeDash
    }
  }

  "hasEnrollment" should {

    val otherEnrolment = Enrolment("OTHER", Seq(EnrolmentIdentifier("test-other-identifier", "test-other-value")), "testState")

    "return true when an enrolment already exists for the user" in new Setup {
      when(mockAuthConnector.getEnrolments[Option[Seq[Enrolment]]](any())(any(), any()))
        .thenReturn(Future.successful(Some(Seq(payeEnrolment))))

      val result = await(service.hasEnrolment(List("IR-PAYE")))
      result shouldBe true
    }

    "return false" when {

      "enrolments are fetched but does not contain one for PAYE" in new Setup {
        when(mockAuthConnector.getEnrolments[Option[Seq[Enrolment]]](any())(any(), any()))
          .thenReturn(Future.successful(Some(Seq(otherEnrolment))))

        val result = await(service.hasEnrolment(List("IR-PAYE")))
        result shouldBe false
      }

      "a HttpException is thrown" in new Setup {
        when(mockAuthConnector.getEnrolments[Option[Seq[Enrolment]]](any())(any(), any()))
          .thenReturn(Future.failed(new HttpException("Not Found", 404)))

        val result = await(service.hasEnrolment(List("IR-PAYE")))
        result shouldBe false
      }

      "a unknown Throwable is thrown" in new Setup {
        when(mockAuthConnector.getEnrolments[Option[Seq[Enrolment]]](any())(any(), any()))
          .thenReturn(Future.failed(new Exception("something went wrong")))

        val result = await(service.hasEnrolment(List("IR-PAYE")))
        result shouldBe false
      }
    }
  }

  "getCompanyName" should {

    val confRefs = ConfirmationReferences(transId, Some(payRef), Some("12"), ackRef)
    val companyName = "testCompanyName"

    "return the company name returned from incorporation information" in new Setup {
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(eqTo(regId))(any()))
        .thenReturn(Future.successful(ConfirmationReferencesSuccessResponse(confRefs)))
      when(mockIncorpInfoConnector.getCompanyName(eqTo(transId))(any())).thenReturn(Future.successful(companyName))

      val res = await(service.getCompanyName(regId))
      res shouldBe companyName

    }

    "throw a ComfirmationRefsNotFoundException when confirmation refs cannot be retrieved" in new Setup {
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(eqTo(regId))(any()))
        .thenReturn(Future.successful(ConfirmationReferencesErrorResponse))
          intercept[ComfirmationRefsNotFoundException](await(service.getCompanyName(regId)))
          }
      }

  "buildHeld" should {

    "return a IncorpAndCTDashboard" in new Setup {
      val expected = IncorpAndCTDashboard("held", Some("10 October 2017"), Some(transId),
        Some(payRef), None, None, Some(ackRef), None)

      val date = DateTime.parse("2017-10-10")
      val dateAsJson = Json.toJson(date)

      when(mockCompanyRegistrationConnector.fetchHeldSubmissionTime(eqTo(regId))(any()))
        .thenReturn(Future.successful(Some(dateAsJson)))

      val result = await(service.buildHeld(regId, ctRegJson("held")))
      result shouldBe expected
          }
      }

      "extractSubmissionDate" should {

          "convert a DateTime as Json and convert it into a dd MMMM yyyy format string" in new Setup {
            val date = DateTime.parse("2017-10-10")
            val dateAsJson = Json.toJson(date)

            service.extractSubmissionDate(dateAsJson) shouldBe "10 October 2017"
          }
      }
  }
