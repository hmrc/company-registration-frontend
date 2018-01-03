/*
 * Copyright 2018 HM Revenue & Customs
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


import builders.AuthBuilder
import fixtures.{AddressFixture, CompanyDetailsFixture, PPOBFixture, UserDetailsFixture}
import helpers.SCRSSpec
import models._
import play.api.test.FakeRequest
import uk.gov.hmrc.play.audit.http.connector.AuditResult.Success
import uk.gov.hmrc.play.frontend.auth.AuthContext
import utils.{SCRSException, SCRSExceptions}
import org.mockito.Mockito._
import org.mockito.Matchers
import org.mockito.Matchers.{any, eq => eqTo}
import play.api.libs.json.Json
import play.api.mvc.RequestHeader
import uk.gov.hmrc.http.cache.client.CacheMap

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http.HeaderCarrier

class PPOBServiceSpec extends SCRSSpec with CompanyDetailsFixture with SCRSExceptions with PPOBFixture
  with UserDetailsFixture with AddressFixture {

  trait Setup {
    val service = new PPOBService {
      override val compRegConnector = mockCompanyRegistrationConnector
      override val keystoreConnector = mockKeystoreConnector
      override val s4LConnector = mockS4LConnector
      override val authConnector = mockAuthConnector
      val auditConnector = mockAuditConnector
      val addressLookupService = mockAddressLookupService
    }
  }

  implicit val user = AuthBuilder.createTestUser
  implicit val userIds = UserIDs("testInternal","testExternal")
  implicit val rh = mock[RequestHeader]

  val request = FakeRequest("GET", "/test-path")

  val registrationID = "12345"

  val returnCacheMap = CacheMap("", Map("" -> Json.toJson("")))
  def setupMocks(existingDetails: CompanyDetails, ppobModel: PPOBModel, expectedDetails: CompanyDetails): Unit = {

    when(mockCompanyRegistrationConnector.retrieveCompanyDetails(Matchers.eq(registrationID))(Matchers.any[HeaderCarrier]()))
      .thenReturn(Future.successful(Some(existingDetails)))

    when(mockCompanyRegistrationConnector.updateCompanyDetails(
      Matchers.eq(registrationID), Matchers.any()
    )(Matchers.any[HeaderCarrier]()))
      .thenReturn(Future.successful(expectedDetails))

    when(mockAuthConnector.getUserDetails[UserDetailsModel](Matchers.any[AuthContext]())(Matchers.any(), Matchers.any(), Matchers.any[ExecutionContext]()))
      .thenReturn(Future.successful(userDetailsModel))

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

  "buildAddress" should {

    "return a Company details case class with a blank PPOB address if the supplied address type is RO" in new Setup {

      val result = service.buildAddress(validCompanyDetailsRequest, "RO", None)

      val expected = CompanyDetails(
        validCompanyDetailsRequest.companyName,
        validCompanyDetailsRequest.cHROAddress,
        PPOB("RO", None),
        validCompanyDetailsRequest.jurisdiction
      )

      result shouldBe expected
    }

    "return a Company details case class with a PPOB address from the supplied Address if the supplied address type is PPOB" in new Setup {

      val result = service.buildAddress(validCompanyDetailsRequest, "PPOB", Some(validNewAddress))
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

      val companyDetails = CompanyDetails(
        validCompanyDetailsRequest.companyName,
        validCompanyDetailsRequest.cHROAddress,
        PPOB("RO", None),
        validCompanyDetailsRequest.jurisdiction
      )

      when(mockCompanyRegistrationConnector.retrieveCompanyDetails(eqTo(registrationID))(any()))
        .thenReturn(Future.successful(Some(validCompanyDetailsRequest)))

      when(mockCompanyRegistrationConnector.updateCompanyDetails(eqTo(registrationID), any())(any()))
        .thenReturn(Future.successful(companyDetails))

      val result = await(service.saveAddress(registrationID, "RO", None))

      result shouldBe companyDetails
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
        validCompanyDetailsRequest.companyName,
        validCompanyDetailsRequest.cHROAddress,
        PPOB("PPOB", Some(address)),
        validCompanyDetailsRequest.jurisdiction
      )

      when(mockCompanyRegistrationConnector.retrieveCompanyDetails(eqTo(registrationID))(any()))
        .thenReturn(Future.successful(Some(validCompanyDetailsRequest)))

      when(mockCompanyRegistrationConnector.updateCompanyDetails(eqTo(registrationID), any())(any()))
        .thenReturn(Future.successful(companyDetails))

      val result = await(service.saveAddress(registrationID, "PPOB", Some(validNewAddress)))

      result shouldBe companyDetails
    }
  }

  "auditROAddress" should {

    "" in new Setup {

    }
  }
}
