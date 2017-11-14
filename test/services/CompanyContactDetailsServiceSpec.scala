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

import _root_.connectors.{BusinessRegistrationConnector, PlatformAnalyticsConnector}
import builders.AuthBuilder
import fixtures.{CompanyContactDetailsFixture, UserDetailsFixture}
import helpers.SCRSSpec
import models._
import org.mockito.Matchers
import org.mockito.Mockito._

import scala.concurrent.{ExecutionContext, Future}

class CompanyContactDetailsServiceSpec extends SCRSSpec with CompanyContactDetailsFixture with UserDetailsFixture {

  val mockPlatformAnalyticsConn = mock[PlatformAnalyticsConnector]
  val mockBusRegConnector = mock[BusinessRegistrationConnector]

  trait Setup {
    val service = new CompanyContactDetailsService {
      val businessRegConnector = mockBusRegConnector
      override val companyRegistrationConnector = mockCompanyRegistrationConnector
      override val keystoreConnector = mockKeystoreConnector
      override val authConnector = mockAuthConnector
      val auditConnector = mockAuditConnector
      val platformAnalyticsConnector = mockPlatformAnalyticsConn
    }
  }

  implicit val user = AuthBuilder.createTestUser

  "fetchContactDetails" should {
    "return a company contact model if a record exists" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      CTRegistrationConnectorMocks.retrieveContactDetails(CompanyContactDetailsSuccessResponse(validCompanyContactDetailsResponse))

      await(service.fetchContactDetails) shouldBe validCompanyContactDetailsModel
    }

    "return a generated company contact model when no contact details record exists but user details retrieves a record" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      CTRegistrationConnectorMocks.retrieveContactDetails(CompanyContactDetailsNotFoundResponse)
      when(mockAuthConnector.getUserDetails[UserDetailsModel](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any[ExecutionContext]()))
        .thenReturn(Future.successful(userDetailsModel))

      await(service.fetchContactDetails) shouldBe companyContactModelFromUserDetails
    }

    "return a company contact model with no email when a record is retrieved from user details with a DES invalid email" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      CTRegistrationConnectorMocks.retrieveContactDetails(CompanyContactDetailsNotFoundResponse)
      when(mockAuthConnector.getUserDetails[UserDetailsModel](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any[ExecutionContext]()))
        .thenReturn(Future.successful(userDetailsModel.copy(email = "invalid+des@email.com")))
      when(mockPlatformAnalyticsConn.sendEvents(Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(()))

      val expected = CompanyContactViewModel("testFirstName testMiddleName testLastName", None, None, None)

      await(service.fetchContactDetails) shouldBe expected
    }

    "return a company contact model with no email when a record is retrieved from user details with an email over 70 characters" in new Setup {

      val email71chars = randStr(60) + "@bar.wibble"

      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      CTRegistrationConnectorMocks.retrieveContactDetails(CompanyContactDetailsNotFoundResponse)
      when(mockAuthConnector.getUserDetails[UserDetailsModel](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any[ExecutionContext]()))
        .thenReturn(Future.successful(userDetailsModel.copy(email = email71chars)))

      val expected = CompanyContactViewModel("testFirstName testMiddleName testLastName", None, None, None)

      await(service.fetchContactDetails) shouldBe expected
    }
  }

  "updateContactDetails" should {
    "return a company contact details response" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      CTRegistrationConnectorMocks.updateContactDetails(CompanyContactDetailsSuccessResponse(validCompanyContactDetailsResponse))

      await(service.updateContactDetails(validCompanyContactDetailsModel)) shouldBe CompanyContactDetailsSuccessResponse(validCompanyContactDetailsResponse)
    }
  }

  "isContactDetailsAmended" should {

    val details = CompanyContactDetails(
      Some("testFirstName"),
      Some("testMiddleName"),
      Some("testLastName"),
      Some("123456789"),
      Some("123456789"),
      Some("foo@bar.wibble"),
      Links(Some("testLink"))
    )

    "return true if first names match" in new Setup {
      val firstName = "test"
      service.isContactDetailsAmended(details, details.copy(contactFirstName = Some(firstName))) shouldBe true
    }

    "return true if last names match" in new Setup {
      val lastName = "test"
      service.isContactDetailsAmended(details, details.copy(contactSurname = Some(lastName))) shouldBe true
    }

    "return true if emails match" in new Setup {
      val email = "test"
      service.isContactDetailsAmended(details, details.copy(contactEmail = Some(email))) shouldBe true
    }

    "return false if details match" in new Setup {
      service.isContactDetailsAmended(details, details) shouldBe false
    }
  }
}
