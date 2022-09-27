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

import fixtures.{AddressFixture, CompanyDetailsFixture}
import helpers.SCRSSpec
import models.{CompanyDetails, NewAddress}
import uk.gov.hmrc.http.InternalServerException

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AddressPrepopulationServiceSpec extends SCRSSpec with AddressFixture with CompanyDetailsFixture {

  trait Setup {
    val testRegistrationId = "testRegistrationId"

    object AddressPrepopulationService extends AddressPrepopulationService(mockCompanyRegistrationConnector, mockPrepopAddressConnector)
  }

  "retrieveAddresses" when {
    "the user has provided a registered office address" when {
      "the user has provided other addresses" should {
        "return all addresses provided by the user" in new Setup {
          val companyDetails: CompanyDetails = validCompanyDetailsResponse
          CTRegistrationConnectorMocks.retrieveCompanyDetails(Some(companyDetails))
          CTRegistrationConnectorMocks.validateRegisteredOfficeAddress(testRegistrationId, companyDetails.cHROAddress)(Future.successful(Some(validNewAddress3)))

          mockGetPrepopAddresses(testRegistrationId)(Future.successful(Seq(validNewAddress, validNewAddress2)))

          val res: Seq[NewAddress] = await(AddressPrepopulationService.retrieveAddresses(testRegistrationId))

          res must contain(validNewAddress)
          res must contain(validNewAddress2)
          res must contain(validNewAddress3)
        }
      }
      "the user has provided no other addresses" should {
        "only return the registered office address" in new Setup {
          val companyDetails: CompanyDetails = validCompanyDetailsResponse
          CTRegistrationConnectorMocks.retrieveCompanyDetails(Some(companyDetails))
          CTRegistrationConnectorMocks.validateRegisteredOfficeAddress(testRegistrationId, companyDetails.cHROAddress)(Future.successful(Some(validNewAddress)))

          mockGetPrepopAddresses(testRegistrationId)(Future.successful(Nil))

          val res: Seq[NewAddress] = await(AddressPrepopulationService.retrieveAddresses(testRegistrationId))

          res must contain(validNewAddress)
        }
      }
    }
    "there is no registered office address" should {
      "throw an InternalServerException" in new Setup {
        CTRegistrationConnectorMocks.retrieveCompanyDetails(None)

        intercept[InternalServerException](await(AddressPrepopulationService.retrieveAddresses(testRegistrationId)))
      }
    }
  }
}
