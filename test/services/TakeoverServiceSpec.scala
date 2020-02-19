/*
 * Copyright 2020 HM Revenue & Customs
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

import java.util.UUID

import mocks.TakeoverConnectorMock
import models.TakeoverDetails
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.http.{HeaderCarrier, InternalServerException}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TakeoverServiceSpec extends UnitSpec  {
  trait Setup extends TakeoverConnectorMock with MockitoSugar {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val testRegistrationId: String = UUID.randomUUID().toString
    val testBusinessName = "testBusinessName"

    object TestTakeoverService extends TakeoverService(mockTakeoverConnector)
  }

  "updateReplacingAnotherBusiness" when {
    "the user selects that they are replacing another business" when {
      "the user has not previously entered any takeover information" should {
        "store an empty takeover object with the replacingAnotherBusiness flag set to true" in new Setup {
          mockGetTakeoverDetails(testRegistrationId)(Future.successful(None))

          val expectedTakeoverDetails: TakeoverDetails = TakeoverDetails(replacingAnotherBusiness = true)
          mockUpdateTakeoverDetails(testRegistrationId, expectedTakeoverDetails)(Future.successful(expectedTakeoverDetails))

          await(TestTakeoverService.updateReplacingAnotherBusiness(testRegistrationId, replacingAnotherBusiness = true)) shouldBe expectedTakeoverDetails
        }
      }
      "the user has previously entered identical takeover information" should {
        "do not update the existing data as it has not changed" in new Setup {
          val existingTakeoverDetails: TakeoverDetails = TakeoverDetails(replacingAnotherBusiness = true, Some(testBusinessName))
          mockGetTakeoverDetails(testRegistrationId)(Future.successful(Some(existingTakeoverDetails)))

          await(TestTakeoverService.updateReplacingAnotherBusiness(testRegistrationId, replacingAnotherBusiness = true)) shouldBe existingTakeoverDetails
        }
      }
      "the user has previously entered different takeover information" should {
        "update the existing data" in new Setup {
          val existingTakeoverDetails: TakeoverDetails = TakeoverDetails(replacingAnotherBusiness = false, Some(testBusinessName))
          mockGetTakeoverDetails(testRegistrationId)(Future.successful(Some(existingTakeoverDetails)))

          val expectedTakeoverDetails: TakeoverDetails = TakeoverDetails(replacingAnotherBusiness = true, Some(testBusinessName))
          mockUpdateTakeoverDetails(testRegistrationId, expectedTakeoverDetails)(Future.successful(expectedTakeoverDetails))

          await(TestTakeoverService.updateReplacingAnotherBusiness(testRegistrationId, replacingAnotherBusiness = true)) shouldBe expectedTakeoverDetails
        }
      }
    }
    "the user selects that they are not replacing another business" should {
      "store an empty takeover object with the replacingAnotherBusiness flag set to false" in new Setup {
        val expectedTakeoverDetails: TakeoverDetails = TakeoverDetails(replacingAnotherBusiness = false)
        mockUpdateTakeoverDetails(testRegistrationId, expectedTakeoverDetails)(Future.successful(expectedTakeoverDetails))

        await(TestTakeoverService.updateReplacingAnotherBusiness(testRegistrationId, replacingAnotherBusiness = false)) shouldBe expectedTakeoverDetails
      }
    }
  }

  "updateBusinessName" when {
    "the database has takeoverDetails with replacingAnotherBusiness = true" should {
      "update the details with a businessName" in new Setup {
        val savedTakeoverDetails: TakeoverDetails = TakeoverDetails(replacingAnotherBusiness = true)
        mockGetTakeoverDetails(testRegistrationId)(Future.successful(Some(savedTakeoverDetails)))
        val expectedTakeoverDetails: TakeoverDetails = TakeoverDetails(replacingAnotherBusiness = true, businessName = Some(testBusinessName))
        mockUpdateTakeoverDetails(testRegistrationId, expectedTakeoverDetails)(Future.successful(expectedTakeoverDetails))

        await(TestTakeoverService.updateBusinessName(testRegistrationId, testBusinessName)) shouldBe expectedTakeoverDetails
      }

      "update the details with a new businessName if the form already had one" in new Setup {
        val testOldBusinessName: String = testBusinessName + "old"
        val savedTakeoverDetails: TakeoverDetails = TakeoverDetails(replacingAnotherBusiness = true, businessName = Some(testOldBusinessName))
        mockGetTakeoverDetails(testRegistrationId)(Future.successful(Some(savedTakeoverDetails)))
        val expectedTakeoverDetails: TakeoverDetails = TakeoverDetails(replacingAnotherBusiness = true, businessName = Some(testBusinessName))
        mockUpdateTakeoverDetails(testRegistrationId, expectedTakeoverDetails)(Future.successful(expectedTakeoverDetails))

        await(TestTakeoverService.updateBusinessName(testRegistrationId, testBusinessName)) shouldBe expectedTakeoverDetails
      }
    }

    "the database has takeoverDetails with replacingAnotherBusiness = false" should {
      "throw an internal server exception" in new Setup {
        val savedTakeoverDetails: TakeoverDetails = TakeoverDetails(replacingAnotherBusiness = false)
        mockGetTakeoverDetails(testRegistrationId)(Future.successful(Some(savedTakeoverDetails)))

        intercept[InternalServerException](await(TestTakeoverService.updateBusinessName(testRegistrationId, testBusinessName)))
      }
    }

    "the database has no takeoverDetails" should {
      "throw an internal server exception" in new Setup {
        mockGetTakeoverDetails(testRegistrationId)(Future.successful(None))

        intercept[InternalServerException](await(TestTakeoverService.updateBusinessName(testRegistrationId, testBusinessName)))
      }
    }
  }
}
