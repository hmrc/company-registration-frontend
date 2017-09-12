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

import _root_.connectors.{PAYENotStarted, PAYESuccessfulResponse, PAYEConnector}
import helpers.{AuthHelpers, SCRSSpec}
import models.auth.{EnrolmentIdentifier, Enrolment}
import models.connectors.ConfirmationReferences
import models._
import org.joda.time.DateTime
import play.api.libs.json.{JsValue, JsObject, Json}
import org.mockito.Mockito._
import org.mockito.Matchers.{eq => eqTo, any}
import uk.gov.hmrc.play.http.{HttpException, HeaderCarrier}
import utils.{BooleanFeatureSwitch, SCRSFeatureSwitches}

import scala.concurrent.Future

class DashboardServiceSpec extends SCRSSpec with AuthHelpers {

  val mockPayeConnector = mock[PAYEConnector]

  implicit val auth = buildAuthContext

  val payeTestBaseUrl = "test"
  val payeTestUri = "/paye-uri"
  val payeUrl = s"$payeTestBaseUrl$payeTestUri"
  val otrsUrl = "OTRS url"

  val mockfeatureFlag = mock[SCRSFeatureSwitches]

  trait Setup {
    val service = new DashboardService {
      override val companyRegistrationConnector = mockCompanyRegistrationConnector
      override val keystoreConnector = mockKeystoreConnector
      override val incorpInfoConnector = mockIncorpInfoConnector
      override val authConnector = mockAuthConnector
      override val payeConnector = mockPayeConnector
      override val otrsPAYEUrl = otrsUrl
      override val payeBaseUrl = payeTestBaseUrl
      override val featureFlag = mockfeatureFlag
      override val payeUri = payeTestUri
    }
  }


  class SetupWithDash(dash: IncorpAndCTDashboard) {
    val service = new DashboardService {
      override val companyRegistrationConnector = mockCompanyRegistrationConnector
      override val keystoreConnector = mockKeystoreConnector
      override val incorpInfoConnector = mockIncorpInfoConnector
      override val authConnector = mockAuthConnector
      override val payeConnector = mockPayeConnector
      override val otrsPAYEUrl = otrsUrl
      override val payeBaseUrl = payeTestBaseUrl
      override val featureFlag = mockfeatureFlag
      override val payeUri = payeTestUri

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

  val payeEnrolment = Enrolment("IR-PAYE", Seq(EnrolmentIdentifier("test-paye-identifier", "test-paye-value")), "testState")
  val vatEnrolment = Enrolment("HMCE-VATDEC-ORG", Seq(EnrolmentIdentifier("test-paye-identifier", "test-paye-value")), "testState")
  val vatVarEnrolment = Enrolment("HMCE-VATVAR-ORG", Seq(EnrolmentIdentifier("test-paye-identifier", "test-paye-value")), "testState")

  "buildDashboard" should {

    val draftDash = IncorpAndCTDashboard("draft", Some("10 October 2017"), Some(transId), Some(payRef), None, None, Some(ackRef), None)
    val rejectedDash = IncorpAndCTDashboard("rejected", Some("10 October 2017"), Some(transId), Some(payRef), None, None, Some(ackRef), None)
    val heldDash = IncorpAndCTDashboard("held", Some("10 October 2017"), Some(transId), Some(payRef), None, None, Some(ackRef), None)

    val payeDash = PAYEDashboard("", None, None, PAYELinks(payeUrl, "OTRS url", None, Some("foo")))
    val payeStatus = PAYEStatus("", None, None, Some("foo"), None)

    "return a CouldNotBuild DashboardStatus when the status of the registration is draft" in new SetupWithDash(draftDash) {
      when(mockPayeConnector.getStatus(any())(any())).thenReturn(Future.successful(PAYESuccessfulResponse(payeStatus)))
      when(mockAuthConnector.getEnrolments[Option[Seq[Enrolment]]](any())(any(), any()))
        .thenReturn(Future.successful(Some(Seq(vatEnrolment))))

      mockPayeFeature(true)
      val res = await(service.buildDashboard(regId))
      res shouldBe CouldNotBuild
    }

    "return a RejectedIncorp DashboardStatus when the status of the registration is rejected" in new SetupWithDash(rejectedDash) {
      when(mockPayeConnector.getStatus(any())(any())).thenReturn(Future.successful(PAYESuccessfulResponse(payeStatus)))
      when(mockAuthConnector.getEnrolments[Option[Seq[Enrolment]]](any())(any(), any()))
        .thenReturn(Future.successful(Some(Seq(vatEnrolment))))

      mockPayeFeature(true)
      val res = await(service.buildDashboard(regId))
      res shouldBe RejectedIncorp
    }

    "return a DashboardBuilt DashboardStatus when the status of the registration is any other status" in new SetupWithDash(heldDash) {
      when(mockPayeConnector.getStatus(any())(any())).thenReturn(Future.successful(PAYESuccessfulResponse(payeStatus)))
      when(mockAuthConnector.getEnrolments[Option[Seq[Enrolment]]](any())(any(), any()))
        .thenReturn(Future.successful(Some(Seq(vatEnrolment))))

      mockPayeFeature(true)
      val res = await(service.buildDashboard(regId))
//      res shouldBe DashboardBuilt(Dashboard(heldDash, "testCompanyName")) //todo: company name set as blank until story is played to put it back in
      res shouldBe DashboardBuilt(Dashboard(heldDash, payeDash, "", hasVATCred = true))
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

    val payeLinks = PAYELinks(payeUrl, otrsUrl, None, None)

    "return a PAYEStatus when one is fetched from paye-registration with cancelURL" in new Setup {
      val payeStatus = PAYEStatus("held", None, None, Some("foo"), None)
      val payeDash = PAYEDashboard("held", None, None, PAYELinks(payeUrl, otrsUrl, None, Some("foo")))
      mockPayeFeature(true)
      when(mockPayeConnector.getStatus(any())(any())).thenReturn(Future.successful(PAYESuccessfulResponse(payeStatus)))

      val result = await(service.buildPAYEDashComponent(regId))
      result shouldBe payeDash
    }

    "return a PAYEStatus when one is fetched from paye-registration with restartURL" in new Setup {
      val payeStatus = PAYEStatus("rejected", None, None, None, Some("bar"))
      val payeDash = PAYEDashboard("rejected", None, None, PAYELinks(payeUrl, otrsUrl, Some("bar"), None))
      mockPayeFeature(true)
      when(mockPayeConnector.getStatus(any())(any())).thenReturn(Future.successful(PAYESuccessfulResponse(payeStatus)))

      val result = await(service.buildPAYEDashComponent(regId))
      result shouldBe payeDash
    }

    "return an ineligible PAYEStatus when nothing is fetched from paye-registration and the user already has a PAYE enrolment" in new Setup {
      val payeDash = PAYEDashboard(PAYEStatuses.NOT_ELIGIBLE, None, None, payeLinks)
      mockPayeFeature(true)
      when(mockPayeConnector.getStatus(any())(any())).thenReturn(Future.successful(PAYENotStarted))
      when(mockAuthConnector.getEnrolments[Option[Seq[Enrolment]]](any())(any(), any()))
        .thenReturn(Future.successful(Some(Seq(payeEnrolment))))

      val result = await(service.buildPAYEDashComponent(regId))
      result shouldBe payeDash
    }

    "return a not started PAYEStatus when nothing is fetched from paye-registration and the user does not have a PAYE enrolment" in new Setup {
      val payeDash = PAYEDashboard(PAYEStatuses.NOT_STARTED, None, None, payeLinks)
      mockPayeFeature(true)
      when(mockPayeConnector.getStatus(any())(any())).thenReturn(Future.successful(PAYENotStarted))
      when(mockAuthConnector.getEnrolments[Option[Seq[Enrolment]]](any())(any(), any()))
        .thenReturn(Future.successful(Some(Seq())))

      val result = await(service.buildPAYEDashComponent(regId))
      result shouldBe payeDash
    }

    "return a not enabled PAYEStatus when the paye feature is turned off" in new Setup {
      val payeDash = PAYEDashboard(PAYEStatuses.NOT_ENABLED, None, None, payeLinks)
      mockPayeFeature(false)

      val result = await(service.buildPAYEDashComponent(regId))
      result shouldBe payeDash
    }
  }

  "hasPAYEEnrollment" should {

    val otherEnrolment = Enrolment("OTHER", Seq(EnrolmentIdentifier("test-other-identifier", "test-other-value")), "testState")

    "return true when a PAYE enrolment already exists for the user" in new Setup {
      when(mockAuthConnector.getEnrolments[Option[Seq[Enrolment]]](any())(any(), any()))
        .thenReturn(Future.successful(Some(Seq(payeEnrolment))))

      val result = await(service.hasPAYEEnrollment)
      result shouldBe true
    }

    "return false" when {

      "enrolments are fetched but does not contain one for PAYE" in new Setup {
        when(mockAuthConnector.getEnrolments[Option[Seq[Enrolment]]](any())(any(), any()))
          .thenReturn(Future.successful(Some(Seq(otherEnrolment))))

        val result = await(service.hasPAYEEnrollment)
        result shouldBe false
      }

      "a HttpException is thrown" in new Setup {
        when(mockAuthConnector.getEnrolments[Option[Seq[Enrolment]]](any())(any(), any()))
          .thenReturn(Future.failed(new HttpException("Not Found", 404)))

        val result = await(service.hasPAYEEnrollment)
        result shouldBe false
      }

      "a unknown Throwable is thrown" in new Setup {
        when(mockAuthConnector.getEnrolments[Option[Seq[Enrolment]]](any())(any(), any()))
          .thenReturn(Future.failed(new Exception("something went wrong")))

        val result = await(service.hasPAYEEnrollment)
        result shouldBe false
      }
    }
  }

  "hasVATEnrollment" should {

    val otherEnrolment = Enrolment("OTHER", Seq(EnrolmentIdentifier("test-other-identifier", "test-other-value")), "testState")

    "return true" when {

      "a VATDEC enrolment already exists for the user" in new Setup {
        when(mockAuthConnector.getEnrolments[Option[Seq[Enrolment]]](any())(any(), any()))
          .thenReturn(Future.successful(Some(Seq(vatEnrolment))))

        val result = await(service.hasVATEnrollment)
        result shouldBe true
      }

      "a VATVAR enrolment already exists for the user" in new Setup {
        when(mockAuthConnector.getEnrolments[Option[Seq[Enrolment]]](any())(any(), any()))
          .thenReturn(Future.successful(Some(Seq(vatVarEnrolment))))

        val result = await(service.hasVATEnrollment)
        result shouldBe true
      }
    }

    "return false" when {

      "enrolments are fetched but does not contain one for VAT" in new Setup {
        when(mockAuthConnector.getEnrolments[Option[Seq[Enrolment]]](any())(any(), any()))
          .thenReturn(Future.successful(Some(Seq(otherEnrolment))))

        val result = await(service.hasVATEnrollment)
        result shouldBe false
      }

      "a HttpException is thrown" in new Setup {
        when(mockAuthConnector.getEnrolments[Option[Seq[Enrolment]]](any())(any(), any()))
          .thenReturn(Future.failed(new HttpException("Not Found", 404)))

        val result = await(service.hasVATEnrollment)
        result shouldBe false
      }

      "a unknown Throwable is thrown" in new Setup {
        when(mockAuthConnector.getEnrolments[Option[Seq[Enrolment]]](any())(any(), any()))
          .thenReturn(Future.failed(new Exception("something went wrong")))

        val result = await(service.hasVATEnrollment)
        result shouldBe false
      }
    }
  }

  "getCompanyName" should {

    val confRefs = ConfirmationReferences(transId, payRef, "12", ackRef)
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
