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

import fixtures.BusinessRegistrationFixture
import helpers.SCRSSpec
import models.{Address, BusinessRegistration, CompanyContactDetailsApi}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import play.api.libs.json.{JsObject, JsValue, Json}
import uk.gov.hmrc.http._

import scala.concurrent.{ExecutionContext, Future}

class BusinessRegistrationConnectorSpec extends SCRSSpec with BusinessRegistrationFixture {

  val mockBusRegConnector = mock[BusinessRegistrationConnectorImpl]

  class Setup {
    val connector = new BusinessRegistrationConnector {
      override val businessRegUrl = "http://testBusinessRegUrl"
      override val httpClientV2 = mockHttpClientV2
      override implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    }
  }

  val registrationId = "reg-12345"

  "BusinessRegistrationConnector" should {
    "use the correct businessRegUrl" in new Setup {
      connector.businessRegUrl mustBe "http://testBusinessRegUrl"
    }
  }

  "createMetadataEntry" should {
    "make a http POST request to business registration micro-service to create a metadata entry" in new Setup {
      mockHttpPOST(validBusinessRegistrationResponse)
      await(connector.createMetadataEntry) mustBe validBusinessRegistrationResponse
    }
  }

  "retrieveMetadata with RegId" should {
    "return a a metadata response if one is found in business registration micro-service" in new Setup {
      mockHttpGET(validBusinessRegistrationResponse)
      await(connector.retrieveMetadata(registrationId)) mustBe BusinessRegistrationSuccessResponse(validBusinessRegistrationResponse)
    }

    "return a Not Found response when a metadata record can not be found" in new Setup {
      mockHttpFailedGET(new NotFoundException("Bad request"))
      await(connector.retrieveMetadata(registrationId)) mustBe BusinessRegistrationNotFoundResponse
    }

    "return a Forbidden response when a metadata record can not be accessed by the user" in new Setup {
      mockHttpFailedGET(new ForbiddenException("Forbidden"))
      await(connector.retrieveMetadata(registrationId)) mustBe BusinessRegistrationForbiddenResponse
    }

    "return an Exception response when an unspecified error has occurred" in new Setup {
      mockHttpFailedGET(new Exception("exception"))
      await(connector.retrieveMetadata(registrationId)).getClass mustBe BusinessRegistrationErrorResponse(new Exception).getClass
    }
  }

  "retrieveMetadata without RegId" should {
    "return a a metadata response if one is found in business registration micro-service" in new Setup {
      mockHttpGET(validBusinessRegistrationResponse)
      await(connector.retrieveMetadata) mustBe BusinessRegistrationSuccessResponse(validBusinessRegistrationResponse)
    }

    "return a Not Found response when a metadata record can not be found" in new Setup {
      mockHttpFailedGET(new NotFoundException("Bad request"))
      await(connector.retrieveMetadata) mustBe BusinessRegistrationNotFoundResponse
    }

    "return a Forbidden response when a metadata record can not be accessed by the user" in new Setup {
      mockHttpFailedGET(new ForbiddenException("Forbidden"))
      await(connector.retrieveMetadata) mustBe BusinessRegistrationForbiddenResponse
    }

    "return an Exception response when an unspecified error has occurred" in new Setup {
      mockHttpFailedGET(new Exception("exception"))
      await(connector.retrieveMetadata).getClass mustBe BusinessRegistrationErrorResponse(new Exception).getClass
    }
  }

  "retrieveAndUpdateCompletionCapacity" should {
    "return a business registration model" in new Setup {
      mockHttpGET(validBusinessRegistrationResponse)
      when(mockBusRegConnector.retrieveMetadata(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(BusinessRegistrationSuccessResponse(validBusinessRegistrationResponse))
      mockHttpPOST(validBusinessRegistrationResponse)
      await(connector.retrieveAndUpdateCompletionCapacity("","")) mustBe validBusinessRegistrationResponse
    }
  }

  "updatePrePopContactDetails" should {

    val companyDetails = CompanyContactDetailsApi(
      Some("telNo"),
      Some("mobNo"),
      Some("email")
    )

    "return true when a 200 is returned from BR" in new Setup {
      mockHttpPOST(HttpResponse(200, ""))
      val result = await(connector.updatePrePopContactDetails(registrationId, companyDetails))

      result mustBe true
    }

    "return false when any 4xx response is returned from BR" in new Setup {
      mockHttpFailedPOST(new BadRequestException("test"))
      val result = await(connector.updatePrePopContactDetails(registrationId, companyDetails))

      result mustBe false
    }
  }

  "updatePrePopAddress" should {

    val address = Address(
      Some("15"),
      "line1",
      "line2",
      Some("line3"),
      Some("line4"),
      Some("FX1 1ZZ"),
      Some("UK")
    )

    "return true when a 200 is returned from BR" in new Setup {
      mockHttpPOST(HttpResponse(200, ""))
      val result = await(connector.updatePrePopAddress(registrationId, address))

      result mustBe true
    }

    "return false when any 4xx response is returned from BR" in new Setup {
      mockHttpFailedPOST(new BadRequestException("test"))
      val result = await(connector.updatePrePopAddress(registrationId, address))

      result mustBe false
    }
  }
}