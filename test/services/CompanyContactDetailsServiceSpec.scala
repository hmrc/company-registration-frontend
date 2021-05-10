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

import _root_.connectors.BusinessRegistrationConnector
import builders.AuthBuilder
import fixtures.{CompanyContactDetailsFixture, UserDetailsFixture}
import helpers.SCRSSpec
import models._
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._

import scala.concurrent.Future

class CompanyContactDetailsServiceSpec extends SCRSSpec with CompanyContactDetailsFixture with UserDetailsFixture with AuthBuilder {

  val mockBusRegConnector = mock[BusinessRegistrationConnector]

  trait Setup {
    val service = new CompanyContactDetailsService {
      val businessRegConnector = mockBusRegConnector
      override val companyRegistrationConnector = mockCompanyRegistrationConnector
      override val keystoreConnector = mockKeystoreConnector
      val auditConnector = mockAuditConnector
    }
  }

  val companyContactAuthEmail = "testEmail"

  "fetchContactDetails" should {
    "return a company contact model if a record exists" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      CTRegistrationConnectorMocks.retrieveContactDetails(CompanyContactDetailsSuccessResponse(validCompanyContactDetailsResponse))
      when(mockCompanyRegistrationConnector.retrieveEmail(any())(any())).thenReturn(Future.successful(Some(Email("verified@email", "GG", true, true, true))))

      await(service.fetchContactDetails) shouldBe validCompanyContactDetailsModel
    }

    "return a generated company contact model when no contact details record exists but user details retrieves a record" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      CTRegistrationConnectorMocks.retrieveContactDetails(CompanyContactDetailsNotFoundResponse)
      when(mockCompanyRegistrationConnector.retrieveEmail(any())(any())).thenReturn(Future.successful(Some(Email("verified@email", "GG", true, true, true))))
      await(service.fetchContactDetails) shouldBe companyContactModelFromUserDetails
    }

    "return a company contact model with no email when a record is retrieved from user details with a DES invalid email" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      CTRegistrationConnectorMocks.retrieveContactDetails(CompanyContactDetailsNotFoundResponse)
      when(mockCompanyRegistrationConnector.retrieveEmail(any())(any())).thenReturn(Future.successful(Some(Email("invalid+des@email.com", "GG", true, true, true))))

      await(service.fetchContactDetails) shouldBe CompanyContactDetailsApi(None, None, None)
    }

    "return a company contact model when  email when a record is retrieved from user details with an email over 70 characters" in new Setup {

      val email71chars = randStr(60) + "@bar.wibble"

      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      CTRegistrationConnectorMocks.retrieveContactDetails(CompanyContactDetailsNotFoundResponse)
      when(mockCompanyRegistrationConnector.retrieveEmail(any())(any())).thenReturn(Future.successful(Some(Email(email71chars, "GG", true, true, true))))


      await(service.fetchContactDetails) shouldBe CompanyContactDetailsApi(None, None, None)
    }


    "return exception if email block is missing" in new Setup {
      CTRegistrationConnectorMocks.retrieveCTRegistration()

      when(mockCompanyRegistrationConnector.retrieveEmail(any())(any())).thenReturn(Future.successful(None))
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      CompanyContactDetailsServiceMocks.fetchContactDetails(validCompanyContactDetailsModel)

      intercept[NoSuchElementException](await(service.fetchContactDetails))

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
      Some("foo@bar.wibble"),
      Some("123456789"),
      Some("123456789"),
      Links(Some("testLink"))
    )

    "return true if email is different" in new Setup {
      val email = "test"
      service.isContactDetailsAmended(Some("foo"),"bar") shouldBe true
    }

    "return false if email is provided but not different" in new Setup {
      service.isContactDetailsAmended(Some("foo"), "foo") shouldBe false
    }
    "return true if email is NOT provided" in new Setup {
      service.isContactDetailsAmended(None, "foo") shouldBe true
    }
  }
}