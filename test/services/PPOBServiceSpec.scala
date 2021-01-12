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

package services

import fixtures.{AddressFixture, CompanyDetailsFixture, PPOBFixture, UserDetailsFixture}
import helpers.SCRSSpec
import models._
import org.mockito.Matchers
import org.mockito.Matchers.{any, eq => eqTo}
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.mvc.RequestHeader
import play.api.test.FakeRequest
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.audit.http.connector.AuditResult.Success
import utils.{SCRSException, SCRSExceptions}

import scala.concurrent.Future

class PPOBServiceSpec extends SCRSSpec with CompanyDetailsFixture with SCRSExceptions with PPOBFixture
  with UserDetailsFixture with AddressFixture {

  trait Setup {
    val service = new PPOBService {
      override val compRegConnector = mockCompanyRegistrationConnector
      override val keystoreConnector = mockKeystoreConnector
      override val s4LConnector = mockS4LConnector
      val auditConnector = mockAuditConnector
    }
  }

  implicit val userIds = UserIDs("testInternal","testExternal")
  implicit val rh = mock[RequestHeader]

  val request = FakeRequest("GET", "/test-path")
  val optNewAddress = Some(NewAddress(
    validNewAddress.addressLine1,
    validNewAddress.addressLine2,
    validNewAddress.addressLine3,
    validNewAddress.addressLine4,
    validNewAddress.postcode,
    validNewAddress.country,
    None
  ))

  val registrationID = "12345"

  val returnCacheMap = CacheMap("", Map("" -> Json.toJson("")))
  def setupMocks(existingDetails: CompanyDetails, ppobModel: PPOBModel, expectedDetails: CompanyDetails): Unit = {

    when(mockCompanyRegistrationConnector.retrieveCompanyDetails(Matchers.eq(registrationID))(Matchers.any[HeaderCarrier]()))
      .thenReturn(Future.successful(Some(existingDetails)))

    when(mockCompanyRegistrationConnector.updateCompanyDetails(
      Matchers.eq(registrationID), Matchers.any()
    )(Matchers.any[HeaderCarrier]()))
      .thenReturn(Future.successful(expectedDetails))

    when(mockAuditConnector.sendExtendedEvent(Matchers.any())(Matchers.any(), Matchers.any()))
      .thenReturn(Future.successful(Success))

    mockS4LClear(mockS4LConnector)
  }

  "retrieveCompanyDetails" should {
    "return a CompanyDetailsResponse if there is data" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      CTRegistrationConnectorMocks.retrieveCompanyDetails(Some(validCompanyDetailsResponse))

      await(service.retrieveCompanyDetails("12345")) shouldBe validCompanyDetailsResponse
    }

    "throw a NotFound exception if there is no data" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      CTRegistrationConnectorMocks.retrieveCompanyDetails(None)

      intercept[SCRSException](await(service.retrieveCompanyDetails("12345")))
    }
  }

  "addressChoice" should {

    val ppobDefined = Some("thing")
    val ppobUndefined = None

    val ctRegWithRO = buildCorporationTaxModel()
    val ctRegWithPPOB = buildCorporationTaxModel(addressType = "PPOB")
    val ctReg = buildCorporationTaxModel(addressType = "")

    "return a PPOBChoice with a value of RO if the ctReg supplied ppob option is defined with RO" in new Setup {
      val result = service.addressChoice(ppobDefined, ctRegWithRO)

      result shouldBe PPOBChoice("RO")
    }

    "return a PPOBChoice with a value of PPOB if the ctReg supplied ppob option is defined with PPOB" in new Setup {
      val result = service.addressChoice(ppobDefined, ctRegWithPPOB)

      result shouldBe PPOBChoice("PPOB")
    }

    "return a PPOBChoice with a value of RO if the supplied ppob option is undefined and the addressType on the ctReg is RO" in new Setup {
      val result = service.addressChoice(ppobUndefined, ctRegWithRO)

      result shouldBe PPOBChoice("RO")
    }

    "return a PPOBChoice with a blank value if the supplied ppob option is undefined and the addressType on the ctReg is not RO" in new Setup {
      val result = service.addressChoice(ppobUndefined, ctReg)

      result shouldBe PPOBChoice("")
    }
  }

  "fetchAddressesAndChoice" should {
    "return an RO address, PPOB address and an AddressChoice of RO when given a regId and the address can be normalised" in new Setup {
      when(mockCompanyRegistrationConnector.retrieveCorporationTaxRegistration(Matchers.any())(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(buildCorporationTaxModel(addressType = "RO")))

      when(mockCompanyRegistrationConnector.checkROValidPPOB(Matchers.any(), Matchers.any())(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(optNewAddress))

      val result = await(service.fetchAddressesAndChoice("123456789"))

      result shouldBe (Some(CHROAddress("14","test road",Some("test town"),"Foo","UK",None,Some("FX1 1ZZ"),None)),
        Some(NewAddress("10 Test Street","Testtown",None,None,Some("FX1 1ZZ"),Some("United Kingdom"),None)),
        PPOBChoice("RO"))

    }

    "return no RO address, PPOB address and an AddressChoice when given a regId and the address cannot be normalised" in new Setup {
      when(mockCompanyRegistrationConnector.retrieveCorporationTaxRegistration(Matchers.any())(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(buildCorporationTaxModel(addressType = "PPOB")))

      when(mockCompanyRegistrationConnector.checkROValidPPOB(Matchers.any(), Matchers.any())(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      val result = await(service.fetchAddressesAndChoice("123456789"))

      result shouldBe (None,
        Some(NewAddress("10 Test Street","Testtown",None,None,Some("FX1 1ZZ"),Some("United Kingdom"),None)),
        PPOBChoice("PPOB"))
    }
  }

  "buildAddress" should {

    "return a Company details case class with a blank PPOB address if the supplied address type is RO" in new Setup {
      val optNewAddress = Some(NewAddress("10 Test Street", "Testtown", None, None, None, Some("United Kingdom"), None))

      when(mockCompanyRegistrationConnector.checkROValidPPOB(eqTo(registrationID), any())(any()))
        .thenReturn(Future.successful(optNewAddress))

      val result = await(service.buildAddress(registrationID, validCompanyDetailsRequest, "RO", None))

      val expected = CompanyDetails(
        validCompanyDetailsRequest.companyName,
        validCompanyDetailsRequest.cHROAddress,
        PPOB("RO", Some(Address(None, "10 Test Street", "Testtown", None, None, None, Some("United Kingdom")))),
        validCompanyDetailsRequest.jurisdiction
      )

      result.toString shouldBe expected.toString
    }

    "return a Company details case class with a PPOB address from the supplied Address if the supplied address type is PPOB" in new Setup {

      val result = await(service.buildAddress(registrationID, validCompanyDetailsRequest, "PPOB", Some(validNewAddress)))
      val address = Address(
        None,
        validNewAddress.addressLine1,
        validNewAddress.addressLine2,
        validNewAddress.addressLine3,
        validNewAddress.addressLine4,
        validNewAddress.postcode,
        validNewAddress.country,
        None
      )

      val expected = CompanyDetails(
        validCompanyDetailsRequest.companyName,
        validCompanyDetailsRequest.cHROAddress,
        PPOB("PPOB", Some(address)),
        validCompanyDetailsRequest.jurisdiction
      )

      result.toString shouldBe expected.toString
    }
  }

  "saveAddress" should {

    "be able to build a Company details case class when the supplied address type is RO and save it" in new Setup {

      val Some(newAddress) = optNewAddress

      val companyDetails = CompanyDetails(
        validCompanyDetailsResponse.companyName,
        validCompanyDetailsResponse.cHROAddress,
        PPOB("RO", Some(Address(None, newAddress.addressLine1, newAddress.addressLine2, None, None, newAddress.postcode))),
        validCompanyDetailsResponse.jurisdiction
      )

      when(mockCompanyRegistrationConnector.checkROValidPPOB(eqTo(registrationID), any())(any()))
        .thenReturn(Future.successful(optNewAddress))

      when(mockCompanyRegistrationConnector.retrieveCompanyDetails(eqTo(registrationID))(any()))
        .thenReturn(Future.successful(Some(validCompanyDetailsResponse)))

      when(mockCompanyRegistrationConnector.updateCompanyDetails(eqTo(registrationID), any())(any()))
        .thenReturn(Future.successful(companyDetails))

      val result = await(service.saveAddress(registrationID, "RO", None))

      result.toString shouldBe companyDetails.toString
    }

    "be able to build a Company details case class when the supplied address type is PPOB and save it" in new Setup {

      val address = Address(
        None,
        validNewAddress.addressLine1,
        validNewAddress.addressLine2,
        validNewAddress.addressLine3,
        validNewAddress.addressLine4,
        validNewAddress.postcode,
        validNewAddress.country,
        None
      )

      val companyDetails = CompanyDetails(
        validCompanyDetailsResponse.companyName,
        validCompanyDetailsResponse.cHROAddress,
        PPOB("PPOB", Some(address)),
        validCompanyDetailsResponse.jurisdiction
      )

      when(mockCompanyRegistrationConnector.retrieveCompanyDetails(eqTo(registrationID))(any()))
        .thenReturn(Future.successful(Some(validCompanyDetailsResponse)))

      when(mockCompanyRegistrationConnector.updateCompanyDetails(eqTo(registrationID), any())(any()))
        .thenReturn(Future.successful(companyDetails))

      val result = await(service.saveAddress(registrationID, "PPOB", Some(validNewAddress)))

      result.toString shouldBe companyDetails.toString
    }
  }
}
