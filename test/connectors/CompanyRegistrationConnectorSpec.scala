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

package connectors

import ch.qos.logback.classic.Level
import config.LangConstants

import java.util.UUID
import fixtures._
import helpers.SCRSSpec
import models._
import models.connectors.ConfirmationReferences
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.{any, anyString}
import org.mockito.Mockito._
import play.api.libs.json.{JsObject, JsValue, Json}
import uk.gov.hmrc.http._
import utils.LogCapturingHelper

import scala.concurrent.{ExecutionContext, Future}

class CompanyRegistrationConnectorSpec extends SCRSSpec with CTDataFixture with CompanyContactDetailsFixture with CompanyDetailsFixture
  with AccountingDetailsFixture with TradingDetailsFixtures with CorporationTaxFixture with LogCapturingHelper {

  trait Setup {
    val connector = new CompanyRegistrationConnector {
      override val companyRegUrl = "testCompanyRegUrl"
      override val wsHttp = mockWSHttp
    }
  }

  implicit val ec = scala.concurrent.ExecutionContext.Implicits.global
  val regID = UUID.randomUUID.toString

  "CompanyRegistrationConnector" should {
    "use the correct businessRegUrl" in new Setup {
      connector.companyRegUrl mustBe "testCompanyRegUrl"
    }

  }
  "fetchCompanyName" should {
    "return company name" in new Setup {
      val corporationTaxRegistration = buildCorporationTaxModel()
      mockHttpGet[JsValue]("testUrl", corporationTaxRegistration)
      val res = await(connector.fetchCompanyName("foo"))
      res mustBe "testCompanyname"
    }
    "throw exception" in new Setup {
      val corporationTaxRegistration = buildCorporationTaxModel().as[JsObject].-("companyDetails")
      mockHttpGet[JsValue]("testUrl", corporationTaxRegistration)
      intercept[Exception](await(connector.fetchCompanyName("foo")))
    }
  }

  "checkROValidPPOB" should {
    "return true if an RO address can be normalised" in new Setup {
      val roWithCountry = Json.parse(
        """
          |{
          |        "addressLine1":"10 Test Street",
          |        "addressLine2":"Testtown",
          |        "country":"United Kingdom"
          |}
        """.stripMargin)

      mockHttpPOST[JsValue, HttpResponse](s"${connector.companyRegUrl}/company-registration/corporation-tax-registration/check-ro-address", HttpResponse(200, json = roWithCountry, Map()))
      await(connector.checkROValidPPOB("12345", CHROAddress("38", "line 1", None, "Telford", "UK", None, None, None))) mustBe
        Some(NewAddress("10 Test Street", "Testtown", None, None, None, Some("United Kingdom"), None))
    }
    "return false if an RO address cannot be normalised" in new Setup {
      mockHttpPOST[JsValue, HttpResponse](s"${connector.companyRegUrl}/company-registration/corporation-tax-registration/check-ro-address", HttpResponse(400, ""))
      await(connector.checkROValidPPOB("12345", CHROAddress("38", "line 1<", None, "Telford", "UK", None, None, None))) mustBe None
    }
    "throw an Exception if any other response is received" in new Setup {
      mockHttpPOST[JsValue, HttpResponse](s"${connector.companyRegUrl}/company-registration/corporation-tax-registration/check-ro-address", HttpResponse(500, ""))
      intercept[Exception](await(connector.checkROValidPPOB("12334", CHROAddress("38", "line 1", None, "Telford", "UK", None, None, None))))
    }
  }

  "retrieveCorporationTaxRegistration" should {
    val corporationTaxRegistration = buildCorporationTaxModel()
    val registrationID = "testRegID"

    "return a valid corporation tax registration" in new Setup {
      mockHttpGet[JsValue]("testUrl", corporationTaxRegistration)
      val result = connector.retrieveCorporationTaxRegistration(registrationID)
      await(result) mustBe corporationTaxRegistration
    }

    "return a 400" in new Setup {
      when(mockWSHttp.GET[JsValue](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any[HttpReads[JsValue]](), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new BadRequestException("400")))

      val result = connector.retrieveCorporationTaxRegistration(registrationID)
      intercept[BadRequestException](await(result))
    }

    "return a 404" in new Setup {
      when(mockWSHttp.GET[JsValue](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any[HttpReads[JsValue]](), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new NotFoundException("404")))
      val result = connector.retrieveCorporationTaxRegistration(registrationID)
      intercept[NotFoundException](await(result))
    }

    "return a 4xx" in new Setup {
      when(mockWSHttp.GET[JsValue](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any[HttpReads[JsValue]](), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(UpstreamErrorResponse("427", 427, 427)))
      val result = connector.retrieveCorporationTaxRegistration(registrationID)
      intercept[UpstreamErrorResponse](await(result))
    }

    "return a 5xx" in new Setup {
      when(mockWSHttp.GET[JsValue](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any[HttpReads[JsValue]](), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(UpstreamErrorResponse("500", 500, 500)))
      val result = connector.retrieveCorporationTaxRegistration(registrationID)
      intercept[UpstreamErrorResponse](await(result))
    }

    "return any other exception" in new Setup {
      when(mockWSHttp.GET[JsValue](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any[HttpReads[JsValue]](), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new NullPointerException))
      val result = connector.retrieveCorporationTaxRegistration(registrationID)
      intercept[NullPointerException](await(result))
    }
  }

  "retrieveOrCreateFootprint" should {
    def json(regId: String, created: Boolean) =
      s"""
         |{
         |  "registration-id":"$regId",
         |  "created":$created,
         |  "confirmation-references":false
         |  }""".stripMargin

    "return a new footprint" in new Setup {
      mockHttpGet[ThrottleResponse]("testUrl", ThrottleResponse("12345", true, false, false))
      val expected = ThrottleResponse("12345", true, false, false)
      await(connector.retrieveOrCreateFootprint()) mustBe FootprintFound(expected)
    }
    "return an existing footprint" in new Setup {
      mockHttpGet[ThrottleResponse]("testUrl", ThrottleResponse("12345", false, false, false))
      val expected = ThrottleResponse("12345", false, false, false)
      await(connector.retrieveOrCreateFootprint()) mustBe FootprintFound(expected)
    }
    "return an FootprintForbiddenResponse" in new Setup {
      when(mockWSHttp.GET[ThrottleResponse](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new ForbiddenException("not found")))

      await(connector.retrieveOrCreateFootprint()) mustBe FootprintForbiddenResponse
    }

    "return a FootprintTooManyRequestsResponse" in new Setup {
      when(mockWSHttp.GET[ThrottleResponse](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(UpstreamErrorResponse("", 429, 429)))

      await(connector.retrieveOrCreateFootprint()) mustBe FootprintTooManyRequestsResponse
    }

    "return a FootprintErrorResponse" in new Setup {
      when(mockWSHttp.GET[ThrottleResponse](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(UpstreamErrorResponse("", 400, 400)))

      await(connector.retrieveOrCreateFootprint()) mustBe FootprintErrorResponse(UpstreamErrorResponse("", 400, 400))
    }

    "return an CompanyContactDetailsBadRequestResponse when encountering any other exception" in new Setup {
      val ex = new Exception("bad request")

      when(mockWSHttp.GET[JsValue](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(ex))

      await(connector.retrieveOrCreateFootprint()) mustBe FootprintErrorResponse(ex)
    }
  }

  "createCorporationTaxRegistrationDetails" should {
    "make a http PUT request to company registration micro-service to create a metada entry" in new Setup {
      mockHttpPUT[JsValue, CorporationTaxRegistrationResponse](connector.companyRegUrl, validCTDataResponse)

      await(connector.createCorporationTaxRegistrationDetails("123")) mustBe validCTDataResponse
    }
    "return a 400" in new Setup {
      when(mockWSHttp.PUT[JsValue, CorporationTaxRegistrationResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any[HttpReads[CorporationTaxRegistrationResponse]](), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new BadRequestException("400")))

      intercept[BadRequestException](await(connector.createCorporationTaxRegistrationDetails("123")))
    }
    "return a 404" in new Setup {
      when(mockWSHttp.PUT[JsValue, CorporationTaxRegistrationResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any[HttpReads[CorporationTaxRegistrationResponse]](), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new NotFoundException("404")))

      intercept[NotFoundException](await(connector.createCorporationTaxRegistrationDetails("123")))
    }
    "return a 4xx" in new Setup {
      when(mockWSHttp.PUT[JsValue, CorporationTaxRegistrationResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any[HttpReads[CorporationTaxRegistrationResponse]](), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(UpstreamErrorResponse("429", 429, 429)))

      intercept[UpstreamErrorResponse](await(connector.createCorporationTaxRegistrationDetails("123")))
    }
    "return a 5xx" in new Setup {
      when(mockWSHttp.PUT[JsValue, CorporationTaxRegistrationResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any[HttpReads[CorporationTaxRegistrationResponse]](), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(UpstreamErrorResponse("500", 500, 500)))

      intercept[UpstreamErrorResponse](await(connector.createCorporationTaxRegistrationDetails("123")))
    }
    "return any other exception" in new Setup {
      when(mockWSHttp.PUT[JsValue, CorporationTaxRegistrationResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any[HttpReads[CorporationTaxRegistrationResponse]](), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new NullPointerException))

      intercept[NullPointerException](await(connector.createCorporationTaxRegistrationDetails("123")))
    }
  }

  "retrieveCorporationTaxRegistrationDetails" should {
    "return a CTData response if one is found in company registration micro-service" in new Setup {
      mockHttpGet[Option[CorporationTaxRegistrationResponse]]("testUrl", Some(validCTDataResponse))

      await(connector.retrieveCorporationTaxRegistrationDetails("123")) mustBe Some(validCTDataResponse)
    }
    "return any other exception" in new Setup {
      when(mockWSHttp.GET[Option[CorporationTaxRegistrationResponse]](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new NullPointerException))

      intercept[Exception](await(connector.retrieveCorporationTaxRegistrationDetails("testRegId")))
    }
  }

  "retrieveTradingDetails" should {
    "return an optional trading details model" in new Setup {
      mockHttpGet[Option[TradingDetails]]("testUrl", Some(tradingDetailsTrue))

      await(connector.retrieveTradingDetails(regID)) mustBe Some(tradingDetailsTrue)
    }
    "return any other exception" in new Setup {
      when(mockWSHttp.GET[Option[TradingDetails]](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new NullPointerException))

      intercept[Exception](await(connector.retrieveTradingDetails("testRegId")))
    }
  }

  "updateTradingDetails" should {
    "return a TradingDetailsSuccessResponse" in new Setup {
      mockHttpPUT[JsValue, TradingDetails]("TestUrl", TradingDetails("true"))

      await(connector.updateTradingDetails(regID, TradingDetails("true"))) mustBe TradingDetailsSuccessResponse(TradingDetails("true"))
    }
    "return any other exception" in new Setup {
      when(mockWSHttp.PUT[JsValue, TradingDetails](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any()
      )(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new NullPointerException))

      intercept[NullPointerException](await(connector.updateTradingDetails("123", TradingDetails("true"))))
    }
  }

  "updateCompanyDetails" should {
    "update details on mongoDB and return a unit" in new Setup {
      mockHttpPUT[JsValue, CompanyDetails]("testUrl", validCompanyDetailsRequest)
      await(connector.updateCompanyDetails("12345", validCompanyDetailsRequest)) mustBe validCompanyDetailsRequest
    }
    "return a 400" in new Setup {
      when(mockWSHttp.PUT[JsValue, CompanyDetails](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new BadRequestException("400")))

      intercept[BadRequestException](await(connector.updateCompanyDetails("123", validCompanyDetailsRequest)))
    }
    "return a 404" in new Setup {
      when(mockWSHttp.PUT[JsValue, CompanyDetails](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new NotFoundException("404")))

      intercept[NotFoundException](await(connector.updateCompanyDetails("123", validCompanyDetailsRequest)))
    }
    "return a 4xx" in new Setup {
      when(mockWSHttp.PUT[JsValue, CompanyDetails](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(UpstreamErrorResponse("429", 429, 429)))

      intercept[UpstreamErrorResponse](await(connector.updateCompanyDetails("123", validCompanyDetailsRequest)))
    }
    "return a 5xx" in new Setup {
      when(mockWSHttp.PUT[JsValue, CompanyDetails](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(UpstreamErrorResponse("500", 500, 500)))

      intercept[UpstreamErrorResponse](await(connector.updateCompanyDetails("123", validCompanyDetailsRequest)))
    }
    "return any other exception" in new Setup {
      when(mockWSHttp.PUT[JsValue, CompanyDetails](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new NullPointerException))

      intercept[NullPointerException](await(connector.updateCompanyDetails("123", validCompanyDetailsRequest)))
    }
  }

  "retrieveCompanyDetails" should {
    "retrieve details from mongoDB and return an optional CompanyDetailsResponse" in new Setup {
      mockHttpGet[Option[CompanyDetails]]("testUrl", Some(validCompanyDetailsResponse))

      await(connector.retrieveCompanyDetails("12345")) mustBe Some(validCompanyDetailsResponse)
    }
    "return any other exception" in new Setup {
      when(mockWSHttp.GET[JsObject](any(), any(), any())(any(), any[HeaderCarrier](), any()))
        .thenReturn(Future.failed(new Exception("foo")))

      intercept[Exception](await(connector.retrieveCompanyDetails("testRegId")))
    }
  }

  "updateReferences" should {

    val validConfirmationReferences = ConfirmationReferences("transID", Some("paymentRef"), Some("paymentAmount"), "ackRef")

    "update references on mongoDB and return a unit" in new Setup {
      mockHttpPUT[JsValue, ConfirmationReferences]("testUrl", validConfirmationReferences)
      await(connector.updateReferences("12345", validConfirmationReferences)) mustBe ConfirmationReferencesSuccessResponse(validConfirmationReferences)
    }
    "return any other exception" in new Setup {
      when(mockWSHttp.GET[JsObject](any(), any(), any())(any(), any[HeaderCarrier](), any()))
        .thenReturn(Future.failed(new Exception("foo")))

      intercept[Exception](await(connector.updateReferences("testRegId", validConfirmationReferences)))
    }
  }

  "retrieveContactDetails" should {
    "return a CompanyContactDetails response if one is found in company registration micro-service" in new Setup {
      mockHttpGet[CompanyContactDetails]("testUrl", validCompanyContactDetailsResponse)

      await(connector.retrieveContactDetails("12345")) mustBe validCompanyContactDetailsResponse
    }

    "return any other exception" in new Setup {
      when(mockWSHttp.GET[JsObject](any(), any(), any())(any(), any[HeaderCarrier](), any()))
        .thenReturn(Future.failed(new Exception("foo")))

      intercept[Exception](await(connector.retrieveContactDetails("testRegId")))
    }
  }

  "updateContactDetails" should {
    "update the Contact Details in company registration micro-service" in new Setup {
      mockHttpPUT[JsValue, CompanyContactDetails]("testUrl", validCompanyContactDetailsResponse)

      await(connector.updateContactDetails("12345", validCompanyContactDetailsModel)) mustBe CompanyContactDetailsSuccessResponse(validCompanyContactDetailsResponse)
    }
    "return any other exception" in new Setup {
      when(mockWSHttp.GET[JsObject](any(), any(), any())(any(), any[HeaderCarrier](), any()))
        .thenReturn(Future.failed(new Exception("foo")))

      intercept[Exception](await(connector.updateContactDetails("testRegId", validCompanyContactDetailsModel)))
    }
  }


  "retrieveAccountingDetails" should {
    "return an Accounting Details response if one is found in company registration micro-service" in new Setup {
      mockHttpGet[AccountingDetails]("testUrl", validAccountingDetailsResponse)

      await(connector.retrieveAccountingDetails("12345")) mustBe validAccountingDetailsResponse
    }

    "return any other exception" in new Setup {
      when(mockWSHttp.GET[JsObject](any(), any(), any())(any(), any[HeaderCarrier](), any()))
        .thenReturn(Future.failed(new Exception("foo")))

      intercept[Exception](await(connector.retrieveAccountingDetails("testRegId")))
    }
  }

  "updateAccountingDetails" should {
    "update the Accounting Details in company registration micro-service" in new Setup {
      mockHttpPUT[JsValue, AccountingDetails]("testUrl", validAccountingDetailsResponse2)

      await(connector.updateAccountingDetails("12345", accountingDetailsRequest)) mustBe AccountingDetailsSuccessResponse(validAccountingDetailsResponse2)
    }

    "return any other exception" in new Setup {
      when(mockWSHttp.GET[JsObject](any(), any(), any())(any(), any[HeaderCarrier](), any()))
        .thenReturn(Future.failed(new Exception("foo")))

      intercept[Exception](await(connector.updateAccountingDetails("testRegId", accountingDetailsRequest)))
    }

  }

  "fetchAcknowledgementReference" should {

    "return a succcess response when an Ack ref is found" in new Setup {
      when(mockWSHttp.GET(ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
      (ArgumentMatchers.any[HttpReads[ConfirmationReferences]](), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.successful(ConfirmationReferences("a", Some("b"), Some("c"), "BRCT00000000123")))

      await(connector.fetchConfirmationReferences("testRegID")) mustBe ConfirmationReferencesSuccessResponse(ConfirmationReferences("a", Some("b"), Some("c"), "BRCT00000000123"))
    }

    "return a bad request response if a there is a bad request to company registration" in new Setup {
      when(mockWSHttp.GET(ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
      (ArgumentMatchers.any[HttpReads[ConfirmationReferences]](), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new BadRequestException("bad request")))

      await(connector.fetchConfirmationReferences("testRegID")) mustBe ConfirmationReferencesBadRequestResponse
    }

    "return a not found response if a record cannot be found" in new Setup {
      when(mockWSHttp.GET(ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
      (ArgumentMatchers.any[HttpReads[ConfirmationReferences]](), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new NotFoundException("not found")))

      await(connector.fetchConfirmationReferences("testRegID")) mustBe ConfirmationReferencesNotFoundResponse
    }

    "return an error response when the error is not captured by the other responses" in new Setup {
      when(mockWSHttp.GET(ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
      (ArgumentMatchers.any[HttpReads[ConfirmationReferences]](), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new Exception()))

      await(connector.fetchConfirmationReferences("testRegID")) mustBe ConfirmationReferencesErrorResponse
    }
  }


  "updateEmail" should {

    val registrationId = "12345"
    val email = Email("testEmailAddress", "GG", linkSent = true, verified = true, returnLinkEmailSent = true)

    "return an email" in new Setup {
      mockHttpPUT[JsValue, Email]("testUrl", email)

      await(connector.updateEmail(registrationId, email)) mustBe Some(email)
    }

    "return None" in new Setup {
      when(mockWSHttp.PUT[JsValue, Email](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new BadRequestException("400")))

      await(connector.updateEmail(registrationId, email)) mustBe None
    }
  }


  "verifyEmail" should {

    val email = Email("testEmailAddress", "GG", true, true, true)
    val registrationId = "12345"

    val json = Json.obj("test" -> "ing")

    "return successful json" in new Setup {
      mockHttpPUT[JsValue, JsValue]("testUrl", json)

      await(connector.verifyEmail(registrationId, email)) mustBe json
    }

    "return an unsuccessful message as json if an exception is caught" in new Setup {
      when(mockWSHttp.PUT[JsValue, JsValue](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(),ArgumentMatchers.any(), ArgumentMatchers.any[ExecutionContext]))
        .thenReturn(Future.failed(new Exception("exception")))

      await(connector.verifyEmail(registrationId, email)) mustBe Json.toJson("exception")
    }
  }

  "retrieveEmail" should {

    val email = Email("testEmailAddress", "GG", true, true, true)
    val registrationId = "12345"

    "return successful json" in new Setup {
      mockHttpGet[Email]("testUrl", email)

      await(connector.retrieveEmail(registrationId)) mustBe email
    }

    "return exception" in new Setup {
      when(mockWSHttp.GET[HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(
        Future.failed(new Exception(""))
      )
      intercept[Exception](await(connector.retrieveEmail(registrationId)))
    }
  }

  "fetchRegistrationStatus" should {

    val testStatus = "testStatus"
    val registration = buildCorporationTaxModel(status = testStatus)
    val registrationNoStatus = buildCorporationTaxModel().as[JsObject] - "status"

    "return the status from the fetched registration" in new Setup {
      when(mockWSHttp.GET[JsValue](anyString(),any(),any())(any(), any(), any()))
        .thenReturn(Future.successful(registration))

      val result: Option[String] = await(connector.fetchRegistrationStatus(regID))

      result mustBe Some(testStatus)
    }

    "return None when a registration document doesn't exist" in new Setup {
      when(mockWSHttp.GET[JsValue](anyString(),any(),any())(any(), any(), any()))
        .thenReturn(Future.failed(new NotFoundException("")))

      val result: Option[String] = await(connector.fetchRegistrationStatus(regID))

      result mustBe None
    }

    "return None when a registration document exists but doesn't contain a status" in new Setup {
      when(mockWSHttp.GET[JsValue](anyString(),any(),any())(any(), any(), any()))
        .thenReturn(Future.successful(registrationNoStatus))

      val result: Option[String] = await(connector.fetchRegistrationStatus(regID))

      result mustBe None
    }
  }
  "saveTXIDAfterHO2" should {

    val registrationId = "12345"
    val txId = "123abc"
    val successfulResponse = HttpResponse(200, "")

    "return a 200 response if CR call returns 200" in new Setup {
      mockHttpPUT[JsValue, HttpResponse]("testUrl", successfulResponse)

      val result = await(connector.saveTXIDAfterHO2(registrationId, txId))

      result mustBe Some(successfulResponse)
    }

    "return a None if CR call returns a Bad request" in new Setup {
      when(mockWSHttp.PUT[JsValue, HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new BadRequestException("400")))

      val result = await(connector.saveTXIDAfterHO2(registrationId, txId))

      result mustBe None
    }

    "return a None if CR call returns a NotFoundException" in new Setup {
      when(mockWSHttp.PUT[JsValue, HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new NotFoundException("404")))

      val result = await(connector.saveTXIDAfterHO2(registrationId, txId))

      result mustBe None
    }

    "return a None if CR call returns a UpstreamErrorResponse 4xx" in new Setup {
      when(mockWSHttp.PUT[JsValue, HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(UpstreamErrorResponse("429", 429, 429)))

      val result = await(connector.saveTXIDAfterHO2(registrationId, txId))

      result mustBe None
    }

    "return a None if CR call returns a UpstreamErrorResponse 5xx" in new Setup {
      when(mockWSHttp.PUT[JsValue, HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(UpstreamErrorResponse("503", 503, 503)))

      val result = await(connector.saveTXIDAfterHO2(registrationId, txId))

      result mustBe None
    }

    "return a None if CR call returns a Exception" in new Setup {
      when(mockWSHttp.PUT[JsValue, HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new Exception("500")))

      val result = await(connector.saveTXIDAfterHO2(registrationId, txId))

      result mustBe None
    }
  }

  "getGroups" should {
    "return Some(groups)" in new Setup {
      when(mockWSHttp.GET[HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(),ArgumentMatchers.any())).thenReturn(
        Future.successful(HttpResponse(200, json = Json.toJson(Groups(true, None, None, None)), Map()))
      )
      val res = await(connector.getGroups(""))
      res mustBe Some(Groups(true, None, None, None))
    }
    "return None when status 204" in new Setup {
      when(mockWSHttp.GET[HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(
        Future.successful(HttpResponse(204, json = Json.toJson(Groups(true, None, None, None)), Map()))
      )
      val res = await(connector.getGroups(""))
      res mustBe None
    }
    "return exception" in new Setup {
      when(mockWSHttp.GET[HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(
        Future.failed(new Exception(""))
      )
      intercept[Exception](await(connector.getGroups("")))
    }

  }
  "updateGroups" should {
    "return groups when groups json is valid" in new Setup {
      mockHttpPUT[Groups, HttpResponse]("testUrl", HttpResponse(200, json = Json.toJson(Groups(true, None, None, None)), Map()))
      val res = await(connector.updateGroups("", Groups(true, None, None, None)))
    }
    "return exception if json is valid on return of update" in new Setup {
      mockHttpPUT[Groups, HttpResponse]("testUrl", HttpResponse(200, json = Json.obj(), Map()))
      intercept[Exception](await(connector.updateGroups("", Groups(true, None, None, None))))
    }
  }
  "delete groups" should {
    "return true" in new Setup {
      when(mockWSHttp.DELETE[HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(
        Future.successful(HttpResponse(200, json = Json.obj(), Map()))
      )
      val res = await(connector.deleteGroups(""))
      res mustBe true
    }
    "return exception" in new Setup {
      when(mockWSHttp.DELETE[HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(
        Future.failed(new Exception(""))
      )
      intercept[Exception](await(connector.deleteGroups("")))
    }
  }

  "shareholderListValidationEndpoint" should {
    "return list of desable shareholder names if status 200" in new Setup {
      mockHttpPOST("", HttpResponse(200, json = Json.toJson(List("foo", "bar")), Map()))
      val res = await(connector.shareholderListValidationEndpoint(List.empty))
      res mustBe List("foo", "bar")
    }
    "return empty list if status 204" in new Setup {
      mockHttpPOST("", HttpResponse(204, json = Json.toJson(List("foo", "bar")), Map()))
      val res = await(connector.shareholderListValidationEndpoint(List.empty))
      res mustBe List.empty
    }
    "return empty list if non 2xx status is returned from CR" in new Setup {
      mockHttpFailedPOST("", new Exception(""))
      val res = await(connector.shareholderListValidationEndpoint(List.empty))
      res mustBe List.empty
    }
  }

  "calling .updateLanguage(regId: String, language: Language)" should {

    val registrationId = "12345"
    val lang = Language(LangConstants.english)

    "return response from the HttpParser on success" in new Setup {

      mockHttpPUT[Language, Boolean]("testUrl", true)

      await(connector.updateLanguage(registrationId, lang)) mustBe true
    }

    "return false if future unexpectedly fails and log error message" in new Setup {

      mockHttpFailedPUT[Language, Boolean]("testUrl", new RuntimeException("bang"))

      withCaptureOfLoggingFrom(connector.logger) { logs =>
        await(connector.updateLanguage(registrationId, lang)) mustBe false

        logs.containsMsg(Level.ERROR, s"[updateLanguage] An unexpected Exception of type 'RuntimeException' occurred when trying to update language to: '${lang.code}' for regId: '$registrationId'")
      }
    }
  }
}