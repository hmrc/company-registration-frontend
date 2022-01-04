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

import scala.concurrent.{ExecutionContext, Future}

class CompanyRegistrationConnectorSpec extends SCRSSpec with CTDataFixture with CompanyContactDetailsFixture with CompanyDetailsFixture
  with AccountingDetailsFixture with TradingDetailsFixtures with CorporationTaxFixture {

  trait Setup {
    val connector = new CompanyRegistrationConnector {
      override val companyRegUrl = "testCompanyRegUrl"
      override val wsHttp = mockWSHttp
    }
  }

  val regID = UUID.randomUUID.toString

  "CompanyRegistrationConnector" should {
    "use the correct businessRegUrl" in new Setup {
      connector.companyRegUrl shouldBe "testCompanyRegUrl"
    }

  }
  "fetchCompanyName" should {
    "return company name" in new Setup {
      val corporationTaxRegistration = buildCorporationTaxModel()
      mockHttpGet[JsValue]("testUrl", corporationTaxRegistration)
      val res = await(connector.fetchCompanyName("foo"))
      res shouldBe "testCompanyname"
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

      mockHttpPOST[JsValue, HttpResponse](s"${connector.companyRegUrl}/company-registration/corporation-tax-registration/check-ro-address", HttpResponse(200, Some(roWithCountry)))
      await(connector.checkROValidPPOB("12345", CHROAddress("38", "line 1", None, "Telford", "UK", None, None, None))) shouldBe
        Some(NewAddress("10 Test Street", "Testtown", None, None, None, Some("United Kingdom"), None))
    }
    "return false if an RO address cannot be normalised" in new Setup {
      mockHttpPOST[JsValue, HttpResponse](s"${connector.companyRegUrl}/company-registration/corporation-tax-registration/check-ro-address", HttpResponse(400))
      await(connector.checkROValidPPOB("12345", CHROAddress("38", "line 1<", None, "Telford", "UK", None, None, None))) shouldBe None
    }
    "throw an Exception if any other response is received" in new Setup {
      mockHttpPOST[JsValue, HttpResponse](s"${connector.companyRegUrl}/company-registration/corporation-tax-registration/check-ro-address", HttpResponse(500))
      intercept[Exception](await(connector.checkROValidPPOB("12334", CHROAddress("38", "line 1", None, "Telford", "UK", None, None, None))))
    }
  }

  "retrieveCorporationTaxRegistration" should {
    val corporationTaxRegistration = buildCorporationTaxModel()
    val registrationID = "testRegID"

    "return a valid corporation tax registration" in new Setup {
      mockHttpGet[JsValue]("testUrl", corporationTaxRegistration)
      val result = connector.retrieveCorporationTaxRegistration(registrationID)
      await(result) shouldBe corporationTaxRegistration
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
        .thenReturn(Future.failed(Upstream4xxResponse("427", 427, 427)))
      val result = connector.retrieveCorporationTaxRegistration(registrationID)
      intercept[Upstream4xxResponse](await(result))
    }

    "return a 5xx" in new Setup {
      when(mockWSHttp.GET[JsValue](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any[HttpReads[JsValue]](), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(Upstream5xxResponse("427", 427, 427)))
      val result = connector.retrieveCorporationTaxRegistration(registrationID)
      intercept[Upstream5xxResponse](await(result))
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
      await(connector.retrieveOrCreateFootprint()) shouldBe FootprintFound(expected)
    }
    "return an existing footprint" in new Setup {
      mockHttpGet[ThrottleResponse]("testUrl", ThrottleResponse("12345", false, false, false))
      val expected = ThrottleResponse("12345", false, false, false)
      await(connector.retrieveOrCreateFootprint()) shouldBe FootprintFound(expected)
    }
    "return an FootprintForbiddenResponse" in new Setup {
      when(mockWSHttp.GET[ThrottleResponse](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new ForbiddenException("not found")))

      await(connector.retrieveOrCreateFootprint()) shouldBe FootprintForbiddenResponse
    }

    "return a FootprintTooManyRequestsResponse" in new Setup {
      when(mockWSHttp.GET[ThrottleResponse](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(Upstream4xxResponse("", 429, 429)))

      await(connector.retrieveOrCreateFootprint()) shouldBe FootprintTooManyRequestsResponse
    }

    "return a FootprintErrorResponse" in new Setup {
      when(mockWSHttp.GET[ThrottleResponse](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(Upstream4xxResponse("", 400, 400)))

      await(connector.retrieveOrCreateFootprint()) shouldBe FootprintErrorResponse(Upstream4xxResponse("", 400, 400))
    }

    "return an CompanyContactDetailsBadRequestResponse when encountering any other exception" in new Setup {
      val ex = new Exception("bad request")

      when(mockWSHttp.GET[JsValue](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(ex))

      await(connector.retrieveOrCreateFootprint()) shouldBe FootprintErrorResponse(ex)
    }
  }

  "createCorporationTaxRegistrationDetails" should {
    "make a http PUT request to company registration micro-service to create a metada entry" in new Setup {
      mockHttpPUT[JsValue, CorporationTaxRegistrationResponse](connector.companyRegUrl, validCTDataResponse)

      await(connector.createCorporationTaxRegistrationDetails("123")) shouldBe validCTDataResponse
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
        .thenReturn(Future.failed(Upstream4xxResponse("429", 429, 429)))

      intercept[Upstream4xxResponse](await(connector.createCorporationTaxRegistrationDetails("123")))
    }
    "return a 5xx" in new Setup {
      when(mockWSHttp.PUT[JsValue, CorporationTaxRegistrationResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any[HttpReads[CorporationTaxRegistrationResponse]](), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(Upstream5xxResponse("500", 500, 500)))

      intercept[Upstream5xxResponse](await(connector.createCorporationTaxRegistrationDetails("123")))
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

      await(connector.retrieveCorporationTaxRegistrationDetails("123")) shouldBe Some(validCTDataResponse)
    }
    "return a 400" in new Setup {
      when(mockWSHttp.GET[Option[CorporationTaxRegistrationResponse]](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new BadRequestException("400")))

      await(connector.retrieveCorporationTaxRegistrationDetails("123")) shouldBe None
    }
    "return a 404" in new Setup {
      when(mockWSHttp.GET[Option[CorporationTaxRegistrationResponse]](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new NotFoundException("404")))

      await(connector.retrieveCorporationTaxRegistrationDetails("123")) shouldBe None
    }
    "return a 4xx" in new Setup {
      when(mockWSHttp.GET[Option[CorporationTaxRegistrationResponse]](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(Upstream4xxResponse("429", 429, 429)))

      await(connector.retrieveCorporationTaxRegistrationDetails("123")) shouldBe None
    }
    "return a 5xx" in new Setup {
      when(mockWSHttp.GET[Option[CorporationTaxRegistrationResponse]](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(Upstream5xxResponse("500", 500, 500)))

      await(connector.retrieveCorporationTaxRegistrationDetails("123")) shouldBe None
    }
    "return any other exception" in new Setup {
      when(mockWSHttp.GET[Option[CorporationTaxRegistrationResponse]](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new NullPointerException))

      await(connector.retrieveCorporationTaxRegistrationDetails("123")) shouldBe None
    }
  }

  "retrieveTradingDetails" should {
    "return an optional trading details model" in new Setup {
      mockHttpGet[Option[TradingDetails]]("testUrl", Some(tradingDetailsTrue))

      await(connector.retrieveTradingDetails(regID)) shouldBe Some(tradingDetailsTrue)
    }
    "return a 400" in new Setup {
      when(mockWSHttp.GET[Option[TradingDetails]](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new BadRequestException("400")))

      await(connector.retrieveTradingDetails("123")) shouldBe None
    }
    "return a 404" in new Setup {
      when(mockWSHttp.GET[Option[TradingDetails]](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new NotFoundException("404")))

      await(connector.retrieveTradingDetails("123")) shouldBe None
    }
    "return a 4xx" in new Setup {
      when(mockWSHttp.GET[Option[TradingDetails]](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(Upstream4xxResponse("429", 429, 429)))

      await(connector.retrieveTradingDetails("123")) shouldBe None
    }
    "return a 5xx" in new Setup {
      when(mockWSHttp.GET[Option[TradingDetails]](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(Upstream5xxResponse("500", 500, 500)))

      await(connector.retrieveTradingDetails("123")) shouldBe None
    }
    "return any other exception" in new Setup {
      when(mockWSHttp.GET[Option[TradingDetails]](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new NullPointerException))

      await(connector.retrieveTradingDetails("123")) shouldBe None
    }
  }

  "updateTradingDetails" should {
    "return a TradingDetailsSuccessResponse" in new Setup {
      mockHttpPUT[JsValue, TradingDetails]("TestUrl", TradingDetails("true"))

      await(connector.updateTradingDetails(regID, TradingDetails("true"))) shouldBe TradingDetailsSuccessResponse(TradingDetails("true"))
    }
    "return a 400" in new Setup {
      when(mockWSHttp.PUT[JsValue, TradingDetails](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new BadRequestException("400")))

      await(connector.updateTradingDetails("123", TradingDetails("true"))) shouldBe TradingDetailsNotFoundResponse
    }
    "return a 404" in new Setup {
      when(mockWSHttp.PUT[JsValue, TradingDetails](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new NotFoundException("404")))

      await(connector.updateTradingDetails("123", TradingDetails("true"))) shouldBe TradingDetailsNotFoundResponse
    }
    "return a 4xx" in new Setup {
      when(mockWSHttp.PUT[JsValue, TradingDetails](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(Upstream4xxResponse("429", 429, 429)))

      await(connector.updateTradingDetails("123", TradingDetails("true"))) shouldBe TradingDetailsNotFoundResponse
    }
    "return a 5xx" in new Setup {
      when(mockWSHttp.PUT[JsValue, TradingDetails](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(Upstream5xxResponse("500", 500, 500)))

      await(connector.updateTradingDetails("123", TradingDetails("true"))) shouldBe TradingDetailsNotFoundResponse
    }
    "return any other exception" in new Setup {
      when(mockWSHttp.PUT[JsValue, TradingDetails](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new NullPointerException))

      await(connector.updateTradingDetails("123", TradingDetails("true"))) shouldBe TradingDetailsNotFoundResponse
    }
  }

  "updateCompanyDetails" should {
    "update details on mongoDB and return a unit" in new Setup {
      mockHttpPUT[JsValue, CompanyDetails]("testUrl", validCompanyDetailsRequest)
      await(connector.updateCompanyDetails("12345", validCompanyDetailsRequest)) shouldBe validCompanyDetailsRequest
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
        .thenReturn(Future.failed(Upstream4xxResponse("429", 429, 429)))

      intercept[Upstream4xxResponse](await(connector.updateCompanyDetails("123", validCompanyDetailsRequest)))
    }
    "return a 5xx" in new Setup {
      when(mockWSHttp.PUT[JsValue, CompanyDetails](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(Upstream5xxResponse("500", 500, 500)))

      intercept[Upstream5xxResponse](await(connector.updateCompanyDetails("123", validCompanyDetailsRequest)))
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

      await(connector.retrieveCompanyDetails("12345")) shouldBe Some(validCompanyDetailsResponse)
    }
    "return a 400" in new Setup {
      when(mockWSHttp.GET[Option[CompanyDetails]](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new BadRequestException("400")))

      await(connector.retrieveCompanyDetails("12345")) shouldBe None
    }
    "return a 404" in new Setup {
      when(mockWSHttp.GET[Option[CompanyDetails]](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new NotFoundException("404")))

      await(connector.retrieveCompanyDetails("12345")) shouldBe None
    }
    "return a 4xx" in new Setup {
      when(mockWSHttp.GET[Option[CompanyDetails]](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(Upstream4xxResponse("429", 429, 429)))

      await(connector.retrieveCompanyDetails("12345")) shouldBe None
    }
    "return a 5xx" in new Setup {
      when(mockWSHttp.GET[Option[CompanyDetails]](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(Upstream5xxResponse("500", 500, 500)))

      await(connector.retrieveCompanyDetails("12345")) shouldBe None
    }
    "return any other exception" in new Setup {
      when(mockWSHttp.GET[Option[CompanyDetails]](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new NullPointerException))

      await(connector.retrieveCompanyDetails("12345")) shouldBe None
    }
  }

  "updateReferences" should {

    val validConfirmationReferences = ConfirmationReferences("transID", Some("paymentRef"), Some("paymentAmount"), "ackRef")

    "update references on mongoDB and return a unit" in new Setup {
      mockHttpPUT[JsValue, ConfirmationReferences]("testUrl", validConfirmationReferences)
      await(connector.updateReferences("12345", validConfirmationReferences)) shouldBe ConfirmationReferencesSuccessResponse(validConfirmationReferences)
    }
    "return a 4xx as a DESFailureDeskpro response" in new Setup {
      when(mockWSHttp.PUT[JsValue, ConfirmationReferences](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(Upstream4xxResponse("429", 429, 429)))

      await(connector.updateReferences("12345", validConfirmationReferences)) shouldBe DESFailureDeskpro
    }
    "return a 5xx as retriable DESFailure response" in new Setup {
      when(mockWSHttp.PUT[JsValue, ConfirmationReferences](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(Upstream5xxResponse("500", 500, 500)))

      await(connector.updateReferences("12345", validConfirmationReferences)) shouldBe DESFailureRetriable
    }
    "return any other exception as DESFailureDeskpro response" in new Setup {
      when(mockWSHttp.PUT[JsValue, ConfirmationReferences](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new NullPointerException))

      await(connector.updateReferences("12345", validConfirmationReferences)) shouldBe DESFailureDeskpro
    }
  }

  "retrieveContactDetails" should {
    "return a CompanyContactDetails response if one is found in company registration micro-service" in new Setup {
      mockHttpGet[CompanyContactDetails]("testUrl", validCompanyContactDetailsResponse)

      await(connector.retrieveContactDetails("12345")) shouldBe CompanyContactDetailsSuccessResponse(validCompanyContactDetailsResponse)
    }
    "return an CompanyContactDetailsBadRequestResponse" in new Setup {
      when(mockWSHttp.GET[CompanyContactDetails](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new BadRequestException("bad request")))

      await(connector.retrieveContactDetails("12345")) shouldBe CompanyContactDetailsBadRequestResponse
    }
    "return an CompanyContactDetailsNotFoundResponse" in new Setup {
      when(mockWSHttp.GET[CompanyContactDetails](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new NotFoundException("not found")))

      await(connector.retrieveContactDetails("12345")) shouldBe CompanyContactDetailsNotFoundResponse
    }
    "return an CompanyContactDetailsForbiddenResponse" in new Setup {
      when(mockWSHttp.GET[CompanyContactDetails](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new ForbiddenException("not found")))

      await(connector.retrieveContactDetails("12345")) shouldBe CompanyContactDetailsForbiddenResponse
    }
    "return an CompanyContactDetailsBadRequestResponse when encountering any other exception" in new Setup {
      when(mockWSHttp.GET[CompanyContactDetails](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new Exception("bad request")))

      await(connector.retrieveContactDetails("12345")) shouldBe CompanyContactDetailsBadRequestResponse
    }
  }

  "updateContactDetails" should {
    "update the Contact Details in company registration micro-service" in new Setup {
      mockHttpPUT[JsValue, CompanyContactDetails]("testUrl", validCompanyContactDetailsResponse)

      await(connector.updateContactDetails("12345", validCompanyContactDetailsModel)) shouldBe CompanyContactDetailsSuccessResponse(validCompanyContactDetailsResponse)
    }
    "return an CompanyContactDetailsBadRequestResponse" in new Setup {
      when(mockWSHttp.PUT[JsValue, CompanyContactDetails](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new BadRequestException("bad request")))

      await(connector.updateContactDetails("12345", validCompanyContactDetailsModel)) shouldBe CompanyContactDetailsBadRequestResponse
    }
    "return an CompanyContactDetailsNotFoundResponse" in new Setup {
      when(mockWSHttp.PUT[JsValue, CompanyContactDetails](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new NotFoundException("not found")))

      await(connector.updateContactDetails("12345", validCompanyContactDetailsModel)) shouldBe CompanyContactDetailsNotFoundResponse
    }
    "return an CompanyContactDetailsForbiddenResponse" in new Setup {
      when(mockWSHttp.PUT[JsValue, CompanyContactDetails](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new ForbiddenException("not found")))

      await(connector.updateContactDetails("12345", validCompanyContactDetailsModel)) shouldBe CompanyContactDetailsForbiddenResponse
    }
    "return an CompanyContactDetailsBadRequestResponse when encountering any other exception" in new Setup {
      when(mockWSHttp.PUT[JsValue, CompanyContactDetails](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new Exception("bad request")))

      await(connector.updateContactDetails("12345", validCompanyContactDetailsModel)) shouldBe CompanyContactDetailsBadRequestResponse
    }
  }


  "retrieveAccountingDetails" should {
    "return an Accounting Details response if one is found in company registration micro-service" in new Setup {
      mockHttpGet[AccountingDetails]("testUrl", validAccountingDetailsResponse)

      await(connector.retrieveAccountingDetails("12345")) shouldBe AccountingDetailsSuccessResponse(validAccountingDetailsResponse)
    }
    "return an AccountingDetailsBadRequestResponse" in new Setup {
      when(mockWSHttp.GET[AccountingDetails](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new BadRequestException("bad request")))

      await(connector.retrieveAccountingDetails("12345")) shouldBe AccountingDetailsBadRequestResponse
    }
    "return an AccountingDetailsNotFoundResponse" in new Setup {
      when(mockWSHttp.GET[AccountingDetails](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new NotFoundException("not found")))

      await(connector.retrieveAccountingDetails("12345")) shouldBe AccountingDetailsNotFoundResponse
    }
    "return an AccountingDetailsBadRequestResponse when encountering any other exception" in new Setup {
      when(mockWSHttp.GET[AccountingDetails](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new Exception("bad request")))

      await(connector.retrieveAccountingDetails("12345")) shouldBe AccountingDetailsBadRequestResponse
    }
  }

  "updateAccountingDetails" should {
    "update the Accounting Details in company registration micro-service" in new Setup {
      mockHttpPUT[JsValue, AccountingDetails]("testUrl", validAccountingDetailsResponse2)

      await(connector.updateAccountingDetails("12345", accountingDetailsRequest)) shouldBe AccountingDetailsSuccessResponse(validAccountingDetailsResponse2)
    }
    "return an AccountingDetailsBadRequestResponse" in new Setup {
      when(mockWSHttp.PUT[JsValue, AccountingDetails](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new BadRequestException("bad request")))

      await(connector.updateAccountingDetails("12345", accountingDetailsRequest)) shouldBe AccountingDetailsBadRequestResponse
    }
    "return an AccountingDetailsNotFoundResponse" in new Setup {
      when(mockWSHttp.PUT[JsValue, AccountingDetails](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new NotFoundException("not found")))

      await(connector.updateAccountingDetails("12345", accountingDetailsRequest)) shouldBe AccountingDetailsNotFoundResponse
    }
    "return an AccountingDetailsBadRequestResponse when encountering any other exception" in new Setup {
      when(mockWSHttp.PUT[JsValue, AccountingDetails](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new Exception("bad request")))

      await(connector.updateAccountingDetails("12345", accountingDetailsRequest)) shouldBe AccountingDetailsBadRequestResponse
    }
  }

  "fetchAcknowledgementReference" should {

    "return a succcess response when an Ack ref is found" in new Setup {
      when(mockWSHttp.GET(ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
      (ArgumentMatchers.any[HttpReads[ConfirmationReferences]](), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.successful(ConfirmationReferences("a", Some("b"), Some("c"), "BRCT00000000123")))

      await(connector.fetchConfirmationReferences("testRegID")) shouldBe ConfirmationReferencesSuccessResponse(ConfirmationReferences("a", Some("b"), Some("c"), "BRCT00000000123"))
    }

    "return a bad request response if a there is a bad request to company registration" in new Setup {
      when(mockWSHttp.GET(ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
      (ArgumentMatchers.any[HttpReads[ConfirmationReferences]](), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new BadRequestException("bad request")))

      await(connector.fetchConfirmationReferences("testRegID")) shouldBe ConfirmationReferencesBadRequestResponse
    }

    "return a not found response if a record cannot be found" in new Setup {
      when(mockWSHttp.GET(ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
      (ArgumentMatchers.any[HttpReads[ConfirmationReferences]](), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new NotFoundException("not found")))

      await(connector.fetchConfirmationReferences("testRegID")) shouldBe ConfirmationReferencesNotFoundResponse
    }

    "return an error response when the error is not captured by the other responses" in new Setup {
      when(mockWSHttp.GET(ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
      (ArgumentMatchers.any[HttpReads[ConfirmationReferences]](), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new Exception()))

      await(connector.fetchConfirmationReferences("testRegID")) shouldBe ConfirmationReferencesErrorResponse
    }
  }


  "updateEmail" should {

    val registrationId = "12345"
    val email = Email("testEmailAddress", "GG", linkSent = true, verified = true, returnLinkEmailSent = true)

    "return an email" in new Setup {
      mockHttpPUT[JsValue, Email]("testUrl", email)

      await(connector.updateEmail(registrationId, email)) shouldBe Some(email)
    }

    "return None" in new Setup {
      when(mockWSHttp.PUT[JsValue, Email](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new BadRequestException("400")))

      await(connector.updateEmail(registrationId, email)) shouldBe None
    }
  }


  "verifyEmail" should {

    val email = Email("testEmailAddress", "GG", true, true, true)
    val registrationId = "12345"

    val json = Json.obj("test" -> "ing")

    "return successful json" in new Setup {
      mockHttpPUT[JsValue, JsValue]("testUrl", json)

      await(connector.verifyEmail(registrationId, email)) shouldBe json
    }

    "return an unsuccessful message as json if an exception is caught" in new Setup {
      when(mockWSHttp.PUT[JsValue, JsValue](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(),ArgumentMatchers.any(), ArgumentMatchers.any[ExecutionContext]))
        .thenReturn(Future.failed(new Exception("exception")))

      await(connector.verifyEmail(registrationId, email)) shouldBe Json.toJson("exception")
    }
  }

  "retrieveEmail" should {

    val email = Email("testEmailAddress", "GG", true, true, true)
    val registrationId = "12345"

    "return successful json" in new Setup {
      mockHttpGet[Email]("testUrl", email)

      await(connector.retrieveEmail(registrationId)) shouldBe Some(email)
    }

    "return None if encountering an exception" in new Setup {
      when(mockWSHttp.GET[Email](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new BadRequestException("400")))

      await(connector.retrieveEmail(registrationId)) shouldBe None
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

      result shouldBe Some(testStatus)
    }

    "return None when a registration document doesn't exist" in new Setup {
      when(mockWSHttp.GET[JsValue](anyString(),any(),any())(any(), any(), any()))
        .thenReturn(Future.failed(new NotFoundException("")))

      val result: Option[String] = await(connector.fetchRegistrationStatus(regID))

      result shouldBe None
    }

    "return None when a registration document exists but doesn't contain a status" in new Setup {
      when(mockWSHttp.GET[JsValue](anyString(),any(),any())(any(), any(), any()))
        .thenReturn(Future.successful(registrationNoStatus))

      val result: Option[String] = await(connector.fetchRegistrationStatus(regID))

      result shouldBe None
    }
  }
  "saveTXIDAfterHO2" should {

    val registrationId = "12345"
    val txId = "123abc"
    val successfulResponse = HttpResponse(200)

    "return a 200 response if CR call returns 200" in new Setup {
      mockHttpPUT[JsValue, HttpResponse]("testUrl", successfulResponse)

      val result = await(connector.saveTXIDAfterHO2(registrationId, txId))

      result shouldBe Some(successfulResponse)
    }

    "return a None if CR call returns a Bad request" in new Setup {
      when(mockWSHttp.PUT[JsValue, HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new BadRequestException("400")))

      val result = await(connector.saveTXIDAfterHO2(registrationId, txId))

      result shouldBe None
    }

    "return a None if CR call returns a NotFoundException" in new Setup {
      when(mockWSHttp.PUT[JsValue, HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new NotFoundException("404")))

      val result = await(connector.saveTXIDAfterHO2(registrationId, txId))

      result shouldBe None
    }

    "return a None if CR call returns a Upstream4xxResponse" in new Setup {
      when(mockWSHttp.PUT[JsValue, HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new Upstream4xxResponse("429", 429, 429)))

      val result = await(connector.saveTXIDAfterHO2(registrationId, txId))

      result shouldBe None
    }

    "return a None if CR call returns a Upstream5xxResponse" in new Setup {
      when(mockWSHttp.PUT[JsValue, HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new Upstream5xxResponse("503", 503, 503)))

      val result = await(connector.saveTXIDAfterHO2(registrationId, txId))

      result shouldBe None
    }

    "return a None if CR call returns a Exception" in new Setup {
      when(mockWSHttp.PUT[JsValue, HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new Exception("500")))

      val result = await(connector.saveTXIDAfterHO2(registrationId, txId))

      result shouldBe None
    }
  }

  "getGroups" should {
    "return Some(groups)" in new Setup {
      when(mockWSHttp.GET[HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(),ArgumentMatchers.any())).thenReturn(
        Future.successful(HttpResponse(200, Some(Json.toJson(Groups(true, None, None, None)))))
      )
      val res = await(connector.getGroups(""))
      res shouldBe Some(Groups(true, None, None, None))
    }
    "return None when status 204" in new Setup {
      when(mockWSHttp.GET[HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(
        Future.successful(HttpResponse(204, Some(Json.toJson(Groups(true, None, None, None)))))
      )
      val res = await(connector.getGroups(""))
      res shouldBe None
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
      mockHttpPUT[Groups, HttpResponse]("testUrl", HttpResponse(200, Some(Json.toJson(Groups(true, None, None, None)))))
      val res = await(connector.updateGroups("", Groups(true, None, None, None)))
    }
    "return exception if json is valid on return of update" in new Setup {
      mockHttpPUT[Groups, HttpResponse]("testUrl", HttpResponse(200, Some(Json.obj())))
      intercept[Exception](await(connector.updateGroups("", Groups(true, None, None, None))))
    }
  }
  "delete groups" should {
    "return true" in new Setup {
      when(mockWSHttp.DELETE[HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(
        Future.successful(HttpResponse(200, None))
      )
      val res = await(connector.deleteGroups(""))
      res shouldBe true
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
      mockHttpPOST("", HttpResponse(200, Some(Json.toJson(List("foo", "bar")))
      ))
      val res = await(connector.shareholderListValidationEndpoint(List.empty))
      res shouldBe List("foo", "bar")
    }
    "return empty list if status 204" in new Setup {
      mockHttpPOST("", HttpResponse(204, Some(Json.toJson(List("foo", "bar")))
      ))
      val res = await(connector.shareholderListValidationEndpoint(List.empty))
      res shouldBe List.empty
    }
    "return empty list if non 2xx status is returned from CR" in new Setup {
      mockHttpFailedPOST("", new Exception(""))
      val res = await(connector.shareholderListValidationEndpoint(List.empty))
      res shouldBe List.empty
    }
  }
}