/*
 * Copyright 2023 HM Revenue & Customs
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
import play.api.http.Status.OK
import play.api.libs.json.{JsObject, JsValue, Json}
import uk.gov.hmrc.http._
import utils.LogCapturingHelper

import scala.concurrent.{ExecutionContext, Future}

class CompanyRegistrationConnectorSpec extends SCRSSpec with CTDataFixture with CompanyContactDetailsFixture with CompanyDetailsFixture
  with AccountingDetailsFixture with TradingDetailsFixtures with CorporationTaxFixture with LogCapturingHelper {

  trait Setup {
    val testAddress = "http://testCompanyRegUrl"

    val connector = new CompanyRegistrationConnector {
      override val companyRegUrl = testAddress
      override val httpClientV2 = mockHttpClientV2
    }
  }

  val regID = UUID.randomUUID.toString

  "CompanyRegistrationConnector" should {
    "use the correct businessRegUrl" in new Setup {
      connector.companyRegUrl mustBe testAddress
    }

  }
  "fetchCompanyName" should {
    "return company name" in new Setup {
      val corporationTaxRegistration = buildCorporationTaxModel()
      mockHttpGET[JsValue](corporationTaxRegistration)
      val res = await(connector.fetchCompanyName("foo"))
      res mustBe "testCompanyname"
    }
    "throw exception" in new Setup {
      val corporationTaxRegistration = buildCorporationTaxModel().as[JsObject].-("companyDetails")
      mockHttpGET[JsValue](corporationTaxRegistration)
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

      mockHttpPOST[JsValue, HttpResponse](url"${connector.companyRegUrl}/company-registration/corporation-tax-registration/check-ro-address", HttpResponse(200, json = roWithCountry, Map()))
      await(connector.checkROValidPPOB("12345", CHROAddress("38", "line 1", None, "Telford", "UK", None, None, None))) mustBe
        Some(NewAddress("10 Test Street", "Testtown", None, None, None, Some("United Kingdom"), None))
    }
    "return false if an RO address cannot be normalised" in new Setup {
      mockHttpPOST[JsValue, HttpResponse](url"${connector.companyRegUrl}/company-registration/corporation-tax-registration/check-ro-address", HttpResponse(400, ""))
      await(connector.checkROValidPPOB("12345", CHROAddress("38", "line 1<", None, "Telford", "UK", None, None, None))) mustBe None
    }
    "throw an Exception if any other response is received" in new Setup {
      mockHttpPOST[JsValue, HttpResponse](url"${connector.companyRegUrl}/company-registration/corporation-tax-registration/check-ro-address", HttpResponse(500, ""))
      intercept[Exception](await(connector.checkROValidPPOB("12334", CHROAddress("38", "line 1", None, "Telford", "UK", None, None, None))))
    }
  }

  "retrieveCorporationTaxRegistration" should {
    val corporationTaxRegistration = buildCorporationTaxModel()
    val registrationID = "testRegID"

    "return a valid corporation tax registration" in new Setup {
      mockHttpGET[JsValue](corporationTaxRegistration)
      val result = connector.retrieveCorporationTaxRegistration(registrationID)
      await(result) mustBe corporationTaxRegistration
    }

    "return a 400" in new Setup {
      mockHttpFailedGET(new BadRequestException("400"))
      val result = connector.retrieveCorporationTaxRegistration(registrationID)
      intercept[BadRequestException](await(result))
    }

    "return a 404" in new Setup {
      mockHttpFailedGET(new NotFoundException("404"))
      val result = connector.retrieveCorporationTaxRegistration(registrationID)
      intercept[NotFoundException](await(result))
    }

    "return a 4xx" in new Setup {
      mockHttpFailedGET(UpstreamErrorResponse("427", 427, 427))
      val result = connector.retrieveCorporationTaxRegistration(registrationID)
      intercept[UpstreamErrorResponse](await(result))
    }

    "return a 5xx" in new Setup {
      mockHttpFailedGET(UpstreamErrorResponse("500", 500, 500))
      val result = connector.retrieveCorporationTaxRegistration(registrationID)
      intercept[UpstreamErrorResponse](await(result))
    }

    "return any other exception" in new Setup {
      mockHttpFailedGET(new NullPointerException)
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
      mockHttpGET[ThrottleResponse](ThrottleResponse("12345", true, false, false))
      val expected = ThrottleResponse("12345", true, false, false)
      await(connector.retrieveOrCreateFootprint()) mustBe FootprintFound(expected)
    }
    "return an existing footprint" in new Setup {
      mockHttpGET[ThrottleResponse](ThrottleResponse("12345", false, false, false))
      val expected = ThrottleResponse("12345", false, false, false)
      await(connector.retrieveOrCreateFootprint()) mustBe FootprintFound(expected)
    }
    "return an FootprintForbiddenResponse" in new Setup {
      mockHttpFailedGET(new ForbiddenException("not found"))

      await(connector.retrieveOrCreateFootprint()) mustBe FootprintForbiddenResponse
    }

    "return a FootprintTooManyRequestsResponse" in new Setup {
      mockHttpFailedGET(UpstreamErrorResponse("", 429, 429))
      await(connector.retrieveOrCreateFootprint()) mustBe FootprintTooManyRequestsResponse
    }

    "return a FootprintErrorResponse" in new Setup {
      mockHttpFailedGET(UpstreamErrorResponse("", 400, 400))
      await(connector.retrieveOrCreateFootprint()) mustBe FootprintErrorResponse(UpstreamErrorResponse("", 400, 400))
    }

    "return an CompanyContactDetailsBadRequestResponse when encountering any other exception" in new Setup {
      val ex = new Exception("bad request")
      mockHttpFailedGET(ex)
      await(connector.retrieveOrCreateFootprint()) mustBe FootprintErrorResponse(ex)
    }
  }

  "createCorporationTaxRegistrationDetails" should {
    "make a http PUT request to company registration micro-service to create a metada entry" in new Setup {
      mockHttpPUT[JsValue, CorporationTaxRegistrationResponse](validCTDataResponse)

      await(connector.createCorporationTaxRegistrationDetails("123")) mustBe validCTDataResponse
    }
    "return a 400" in new Setup {
      mockHttpFailedPUT[JsValue, CorporationTaxRegistrationResponse](new BadRequestException("400"))
      intercept[BadRequestException](await(connector.createCorporationTaxRegistrationDetails("123")))
    }
    "return a 404" in new Setup {
      mockHttpFailedPUT[JsValue, CorporationTaxRegistrationResponse](new NotFoundException("404"))
      intercept[NotFoundException](await(connector.createCorporationTaxRegistrationDetails("123")))
    }
    "return a 4xx" in new Setup {
      mockHttpFailedPUT[JsValue, CorporationTaxRegistrationResponse](UpstreamErrorResponse("429", 429, 429))
      intercept[UpstreamErrorResponse](await(connector.createCorporationTaxRegistrationDetails("123")))
    }
    "return a 5xx" in new Setup {
      mockHttpFailedPUT[JsValue, CorporationTaxRegistrationResponse](UpstreamErrorResponse("500", 500, 500))
      intercept[UpstreamErrorResponse](await(connector.createCorporationTaxRegistrationDetails("123")))
    }
    "return any other exception" in new Setup {
      mockHttpFailedPUT[JsValue, CorporationTaxRegistrationResponse](new NullPointerException)
      intercept[NullPointerException](await(connector.createCorporationTaxRegistrationDetails("123")))
    }
  }

  "retrieveCorporationTaxRegistrationDetails" should {
    "return a CTData response if one is found in company registration micro-service" in new Setup {
      mockHttpGET[Option[CorporationTaxRegistrationResponse]](Some(validCTDataResponse))

      await(connector.retrieveCorporationTaxRegistrationDetails("123")) mustBe Some(validCTDataResponse)
    }
    "return a 404" in new Setup {
      mockHttpGET[Option[CorporationTaxRegistrationResponse]](None)

      await(connector.retrieveCorporationTaxRegistrationDetails("123")) mustBe None
    }
    "return any other exception" in new Setup {
      mockHttpFailedGET[Option[CorporationTaxRegistrationResponse]](new NullPointerException)
      intercept[Exception](await(connector.retrieveCorporationTaxRegistrationDetails("testRegId")))
    }
  }

  "retrieveTradingDetails" should {
    "return an optional trading details model" in new Setup {
      mockHttpGET[Option[TradingDetails]](Some(tradingDetailsTrue))

      await(connector.retrieveTradingDetails(regID)) mustBe Some(tradingDetailsTrue)
    }
    "return a 404" in new Setup {
      mockHttpGET[Option[TradingDetails]](None)

      await(connector.retrieveTradingDetails("123")) mustBe None
    }

    "return any other exception" in new Setup {
      mockHttpFailedGET[Option[TradingDetails]](new NullPointerException)
      intercept[Exception](await(connector.retrieveTradingDetails("testRegId")))
    }
  }

  "updateTradingDetails" should {
    "return a TradingDetailsSuccessResponse" in new Setup {
      mockHttpPUT[JsValue, TradingDetails](TradingDetails("true"))

      await(connector.updateTradingDetails(regID, TradingDetails("true"))) mustBe TradingDetailsSuccessResponse(TradingDetails("true"))
    }
    "return any other exception" in new Setup {
      mockHttpFailedPUT[JsValue, TradingDetails](new NullPointerException)
      intercept[NullPointerException](await(connector.updateTradingDetails("123", TradingDetails("true"))))
    }
  }

  "updateCompanyDetails" should {
    "update details on mongoDB and return a unit" in new Setup {
      mockHttpPUT[JsValue, CompanyDetails](validCompanyDetailsRequest)
      await(connector.updateCompanyDetails("12345", validCompanyDetailsRequest)) mustBe validCompanyDetailsRequest
    }
    "return a 400" in new Setup {
      mockHttpFailedPUT[JsValue, CompanyDetails](new BadRequestException("400"))
      intercept[BadRequestException](await(connector.updateCompanyDetails("123", validCompanyDetailsRequest)))
    }
    "return a 404" in new Setup {
      mockHttpFailedPUT[JsValue, CompanyDetails](new NotFoundException("404"))
      intercept[NotFoundException](await(connector.updateCompanyDetails("123", validCompanyDetailsRequest)))
    }
    "return a 4xx" in new Setup {
      mockHttpFailedPUT[JsValue, CompanyDetails](UpstreamErrorResponse("429", 429, 429))
      intercept[UpstreamErrorResponse](await(connector.updateCompanyDetails("123", validCompanyDetailsRequest)))
    }
    "return a 5xx" in new Setup {
      mockHttpFailedPUT[JsValue, CompanyDetails](UpstreamErrorResponse("500", 500, 500))
      intercept[UpstreamErrorResponse](await(connector.updateCompanyDetails("123", validCompanyDetailsRequest)))
    }
    "return any other exception" in new Setup {
      mockHttpFailedPUT[JsValue, CompanyDetails](new NullPointerException)
      intercept[NullPointerException](await(connector.updateCompanyDetails("123", validCompanyDetailsRequest)))
    }
  }

  "retrieveCompanyDetails" should {
    "retrieve details from mongoDB and return an optional CompanyDetailsResponse" in new Setup {
      mockHttpGET[Option[CompanyDetails]](Some(validCompanyDetailsResponse))
      await(connector.retrieveCompanyDetails("12345")) mustBe Some(validCompanyDetailsResponse)
    }

    "return a 404" in new Setup {
      mockHttpGET[Option[CompanyDetails]](None)
      await(connector.retrieveCompanyDetails("12345")) mustBe None
    }

    "return any other exception" in new Setup {
      mockHttpFailedGET[JsObject](new Exception("foo"))
      intercept[Exception](await(connector.retrieveCompanyDetails("testRegId")))
    }
  }

  "updateReferences" should {

    val validConfirmationReferences = ConfirmationReferences("transID", Some("paymentRef"), Some("paymentAmount"), "ackRef")

    "update references on mongoDB and return a unit" in new Setup {
      mockHttpPUT[JsValue, ConfirmationReferences](validConfirmationReferences)
      await(connector.updateReferences("12345", validConfirmationReferences)) mustBe ConfirmationReferencesSuccessResponse(validConfirmationReferences)
    }
    "return any other exception" in new Setup {
      mockHttpFailedGET[JsObject](new Exception("foo"))
      intercept[Exception](await(connector.updateReferences("testRegId", validConfirmationReferences)))
    }
  }

  "updateRegistrationProgress" should {
    "update registration progress" in new Setup {
      mockHttpPUT[JsValue, HttpResponse](HttpResponse(OK, ""))
      await(connector.updateRegistrationProgress("12345", "")).status mustBe OK
    }
    "return any other exception" in new Setup {
      mockHttpFailedGET[JsObject](new Exception("foo"))
      intercept[Exception](await(connector.updateRegistrationProgress("testRegId", "")))
    }
  }

  "retrieveContactDetails" should {
    "return a CompanyContactDetails response if one is found in company registration micro-service" in new Setup {
      mockHttpGET[CompanyContactDetails](validCompanyContactDetailsResponse)
      await(connector.retrieveContactDetails("12345")) mustBe validCompanyContactDetailsResponse
    }

    "return any other exception" in new Setup {
      mockHttpFailedGET[JsObject](new Exception("foo"))
      intercept[Exception](await(connector.retrieveContactDetails("testRegId")))
    }
  }

  "updateContactDetails" should {
    "update the Contact Details in company registration micro-service" in new Setup {
      mockHttpPUT[JsValue, CompanyContactDetails](validCompanyContactDetailsResponse)

      await(connector.updateContactDetails("12345", validCompanyContactDetailsModel)) mustBe CompanyContactDetailsSuccessResponse(validCompanyContactDetailsResponse)
    }
    "return any other exception" in new Setup {
      mockHttpFailedGET[JsObject](new Exception("foo"))
      intercept[Exception](await(connector.updateContactDetails("testRegId", validCompanyContactDetailsModel)))
    }
  }


  "retrieveAccountingDetails" should {
    "return an Accounting Details response if one is found in company registration micro-service" in new Setup {
      mockHttpGET[AccountingDetails](validAccountingDetailsResponse)
      await(connector.retrieveAccountingDetails("12345")) mustBe validAccountingDetailsResponse
    }

    "return any other exception" in new Setup {
      mockHttpFailedGET[JsObject](new Exception("foo"))
      intercept[Exception](await(connector.retrieveAccountingDetails("testRegId")))
    }
  }

  "updateAccountingDetails" should {
    "update the Accounting Details in company registration micro-service" in new Setup {
      mockHttpPUT[JsValue, AccountingDetails](validAccountingDetailsResponse2)
      await(connector.updateAccountingDetails("12345", accountingDetailsRequest)) mustBe AccountingDetailsSuccessResponse(validAccountingDetailsResponse2)
    }

    "return any other exception" in new Setup {
      mockHttpFailedGET[JsObject](new Exception("foo"))
      intercept[Exception](await(connector.updateAccountingDetails("testRegId", accountingDetailsRequest)))
    }

  }

  "fetchAcknowledgementReference" should {

    "return a succcess response when an Ack ref is found" in new Setup {
      mockHttpGET[ConfirmationReferences](ConfirmationReferences("a", Some("b"), Some("c"), "BRCT00000000123"))
      await(connector.fetchConfirmationReferences("testRegID")) mustBe ConfirmationReferencesSuccessResponse(ConfirmationReferences("a", Some("b"), Some("c"), "BRCT00000000123"))
    }

    "return a bad request response if a there is a bad request to company registration" in new Setup {
      mockHttpFailedGET[ConfirmationReferences](new BadRequestException("bad request"))
      await(connector.fetchConfirmationReferences("testRegID")) mustBe ConfirmationReferencesBadRequestResponse
    }

    "return a not found response if a record cannot be found" in new Setup {
      mockHttpFailedGET[ConfirmationReferences](new NotFoundException("not found"))
      await(connector.fetchConfirmationReferences("testRegID")) mustBe ConfirmationReferencesNotFoundResponse
    }

    "return an error response when the error is not captured by the other responses" in new Setup {
      mockHttpFailedGET[ConfirmationReferences](new Exception())
      await(connector.fetchConfirmationReferences("testRegID")) mustBe ConfirmationReferencesErrorResponse
    }
  }


  "updateEmail" should {

    val registrationId = "12345"
    val email = Email("testEmailAddress", "GG", linkSent = true, verified = true, returnLinkEmailSent = true)

    "return an email" in new Setup {
      mockHttpPUT[JsValue, Email](email)

      await(connector.updateEmail(registrationId, email)) mustBe Some(email)
    }

    "return None" in new Setup {
      mockHttpFailedPUT[JsValue, Email](new BadRequestException("400"))
      await(connector.updateEmail(registrationId, email)) mustBe None
    }
  }


  "verifyEmail" should {

    val email = Email("testEmailAddress", "GG", true, true, true)
    val registrationId = "12345"

    val json = Json.obj("test" -> "ing")

    "return successful json" in new Setup {
      mockHttpPUT[JsValue, JsValue](json)

      await(connector.verifyEmail(registrationId, email)) mustBe json
    }

    "return an unsuccessful message as json if an exception is caught" in new Setup {
      mockHttpFailedPUT[JsValue, JsValue](new Exception("exception"))
      await(connector.verifyEmail(registrationId, email)) mustBe Json.toJson("exception")
    }
  }

  "retrieveEmail" should {

    val email = Email("testEmailAddress", "GG", true, true, true)
    val registrationId = "12345"

    "return successful json" in new Setup {
      mockHttpGET[Email](email)

      await(connector.retrieveEmail(registrationId)) mustBe email
    }

    "return exception" in new Setup {
      mockHttpFailedGET[HttpResponse](new Exception(""))
      intercept[Exception](await(connector.retrieveEmail(registrationId)))
    }
  }

  "fetchRegistrationStatus" should {

    val testStatus = "testStatus"
    val registration = buildCorporationTaxModel(status = testStatus)
    val registrationNoStatus = buildCorporationTaxModel().as[JsObject] - "status"

    "return the status from the fetched registration" in new Setup {
      mockHttpGET[JsValue](registration)
      val result: Option[String] = await(connector.fetchRegistrationStatus(regID))
      result mustBe Some(testStatus)
    }

    "return None when a registration document doesn't exist" in new Setup {
      mockHttpFailedGET[JsValue](new NotFoundException(""))
      val result: Option[String] = await(connector.fetchRegistrationStatus(regID))
      result mustBe None
    }

    "return None when a registration document exists but doesn't contain a status" in new Setup {
      mockHttpGET[JsValue](registrationNoStatus)
      val result: Option[String] = await(connector.fetchRegistrationStatus(regID))
      result mustBe None
    }
  }
  "saveTXIDAfterHO2" should {

    val registrationId = "12345"
    val txId = "123abc"
    val successfulResponse = HttpResponse(200, "")

    "return a 200 response if CR call returns 200" in new Setup {
      mockHttpPUT[JsValue, HttpResponse](successfulResponse)

      val result = await(connector.saveTXIDAfterHO2(registrationId, txId))

      result mustBe Some(successfulResponse)
    }

    "return a None if CR call returns a Bad request" in new Setup {
      mockHttpFailedPUT[JsValue, HttpResponse](new BadRequestException("400"))
      val result = await(connector.saveTXIDAfterHO2(registrationId, txId))
      result mustBe None
    }

    "return a None if CR call returns a NotFoundException" in new Setup {
      mockHttpFailedPUT[JsValue, HttpResponse](new NotFoundException("404"))
      val result = await(connector.saveTXIDAfterHO2(registrationId, txId))
      result mustBe None
    }

    "return a None if CR call returns a UpstreamErrorResponse 4xx" in new Setup {
      mockHttpFailedPUT[JsValue, HttpResponse](UpstreamErrorResponse("429", 429, 429))
      val result = await(connector.saveTXIDAfterHO2(registrationId, txId))

      result mustBe None
    }

    "return a None if CR call returns a UpstreamErrorResponse 5xx" in new Setup {
      mockHttpFailedPUT[JsValue, HttpResponse](UpstreamErrorResponse("503", 503, 503))
      val result = await(connector.saveTXIDAfterHO2(registrationId, txId))
      result mustBe None
    }

    "return a None if CR call returns a Exception" in new Setup {
      mockHttpFailedPUT[JsValue, HttpResponse](new Exception("500"))
      val result = await(connector.saveTXIDAfterHO2(registrationId, txId))

      result mustBe None
    }
  }

  "getGroups" should {
    "return Some(groups)" in new Setup {
      val response = HttpResponse(200, json = Json.toJson(Groups(true, None, None, None)), Map())
      mockHttpGET[HttpResponse](response)

      val res = await(connector.getGroups(""))
      res mustBe Some(Groups(true, None, None, None))
    }
    "return None when status 204" in new Setup {
      val response = HttpResponse(204, json = Json.toJson(Groups(true, None, None, None)), Map())
      mockHttpGET[HttpResponse](response)
      val res = await(connector.getGroups(""))
      res mustBe None
    }
    "return exception" in new Setup {
      mockHttpFailedGET[HttpResponse](new Exception(""))
      intercept[Exception](await(connector.getGroups("")))
    }

  }
  "updateGroups" should {
    "return groups when groups json is valid" in new Setup {
      mockHttpPUT[Groups, HttpResponse](HttpResponse(200, json = Json.toJson(Groups(true, None, None, None)), Map()))
      val res = await(connector.updateGroups("", Groups(true, None, None, None)))
    }
    "return exception if json is valid on return of update" in new Setup {
      mockHttpPUT[Groups, HttpResponse](HttpResponse(200, json = Json.obj(), Map()))
      intercept[Exception](await(connector.updateGroups("", Groups(true, None, None, None))))
    }
  }
  "delete groups" should {
    "return true" in new Setup {
      mockHttpDELETE(HttpResponse(200, json = Json.obj(), Map()))
      val res = await(connector.deleteGroups(""))
      res mustBe true
    }
    "return exception" in new Setup {
      mockHttpFailedDELETE(new Exception(""))
      intercept[Exception](await(connector.deleteGroups("")))
    }
  }

  "shareholderListValidationEndpoint" should {
    "return list of desable shareholder names if status 200" in new Setup {
      mockHttpPOST(HttpResponse(200, json = Json.toJson(List("foo", "bar")), Map()))
      val res = await(connector.shareholderListValidationEndpoint(List.empty))
      res mustBe List("foo", "bar")
    }
    "return empty list if status 204" in new Setup {
      mockHttpPOST(HttpResponse(204, json = Json.toJson(List("foo", "bar")), Map()))
      val res = await(connector.shareholderListValidationEndpoint(List.empty))
      res mustBe List.empty
    }
    "return empty list if non 2xx status is returned from CR" in new Setup {
      mockHttpFailedPOST(new Exception(""))
      val res = await(connector.shareholderListValidationEndpoint(List.empty))
      res mustBe List.empty
    }
  }

  "calling .updateLanguage(regId: String, language: Language)" should {

    val registrationId = "12345"
    val lang = Language(LangConstants.english)

    "return response from the HttpParser on success" in new Setup {

      mockHttpPUT[Language, Boolean](true)

      await(connector.updateLanguage(registrationId, lang)) mustBe true
    }

    "return false if future unexpectedly fails and log error message" in new Setup {

      mockHttpFailedPUT[Language, Boolean](new RuntimeException("bang"))

      withCaptureOfLoggingFrom(connector.logger) { logs =>
        await(connector.updateLanguage(registrationId, lang)) mustBe false

        logs.containsMsg(Level.ERROR, s"[updateLanguage] An unexpected Exception of type 'RuntimeException' occurred when trying to update language to: '${lang.code}' for regId: '$registrationId'")
      }
    }
  }
}