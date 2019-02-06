/*
 * Copyright 2019 HM Revenue & Customs
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
import org.mockito.Matchers
import org.mockito.Mockito._
import play.api.libs.json.JsValue
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.test.WithFakeApplication

import scala.concurrent.{ExecutionContext, Future}

class BusinessRegistrationConnectorSpec extends SCRSSpec with BusinessRegistrationFixture with WithFakeApplication {

  val mockBusRegConnector = mock[BusinessRegistrationConnector]

  trait Setup {
    val connector = new BusinessRegistrationConnector {
      override val businessRegUrl = "testBusinessRegUrl"
      override val wsHttp = mockWSHttp
    }
  }

  val registrationId = "reg-12345"

  "BusinessRegistrationConnector" should {
    "use the correct businessRegUrl" in new Setup {
      connector.businessRegUrl shouldBe "testBusinessRegUrl"
    }
  }

  "createMetadataEntry" should {
    "make a http POST request to business registration micro-service to create a metadata entry" in new Setup {
      mockHttpPOST[JsValue, BusinessRegistration](connector.businessRegUrl, validBusinessRegistrationResponse)

      await(connector.createMetadataEntry) shouldBe validBusinessRegistrationResponse
    }
  }

  "retrieveMetadata with RegId" should {
    "return a a metadata response if one is found in business registration micro-service" in new Setup {
      mockHttpGet[BusinessRegistration]("testUrl", validBusinessRegistrationResponse)

      await(connector.retrieveMetadata(registrationId)) shouldBe BusinessRegistrationSuccessResponse(validBusinessRegistrationResponse)
    }

    "return a Not Found response when a metadata record can not be found" in new Setup {
      when(mockWSHttp.GET[BusinessRegistration](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any[ExecutionContext]))
        .thenReturn(Future.failed(new NotFoundException("Bad request")))

      await(connector.retrieveMetadata(registrationId)) shouldBe BusinessRegistrationNotFoundResponse
    }

    "return a Forbidden response when a metadata record can not be accessed by the user" in new Setup {
      when(mockWSHttp.GET[BusinessRegistration](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any[ExecutionContext]))
        .thenReturn(Future.failed(new ForbiddenException("Forbidden")))

      await(connector.retrieveMetadata(registrationId)) shouldBe BusinessRegistrationForbiddenResponse
    }

    "return an Exception response when an unspecified error has occurred" in new Setup {
      when(mockWSHttp.GET[BusinessRegistration](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any[ExecutionContext]))
        .thenReturn(Future.failed(new Exception("exception")))

      await(connector.retrieveMetadata(registrationId)).getClass shouldBe BusinessRegistrationErrorResponse(new Exception).getClass
    }
  }

  "retrieveMetadata without RegId" should {
    "return a a metadata response if one is found in business registration micro-service" in new Setup {
      mockHttpGet[BusinessRegistration]("testUrl", validBusinessRegistrationResponse)

      await(connector.retrieveMetadata) shouldBe BusinessRegistrationSuccessResponse(validBusinessRegistrationResponse)
    }

    "return a Not Found response when a metadata record can not be found" in new Setup {
      when(mockWSHttp.GET[BusinessRegistration](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any[ExecutionContext]))
        .thenReturn(Future.failed(new NotFoundException("Bad request")))

      await(connector.retrieveMetadata) shouldBe BusinessRegistrationNotFoundResponse
    }

    "return a Forbidden response when a metadata record can not be accessed by the user" in new Setup {
      when(mockWSHttp.GET[BusinessRegistration](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any[ExecutionContext]))
        .thenReturn(Future.failed(new ForbiddenException("Forbidden")))

      await(connector.retrieveMetadata) shouldBe BusinessRegistrationForbiddenResponse
    }

    "return an Exception response when an unspecified error has occurred" in new Setup {
      when(mockWSHttp.GET[BusinessRegistration](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any[ExecutionContext]))
        .thenReturn(Future.failed(new Exception("exception")))

      await(connector.retrieveMetadata).getClass shouldBe BusinessRegistrationErrorResponse(new Exception).getClass
    }
  }

  "retrieveAndUpdateCompletionCapacity" should {
    "return a business registration model" in new Setup {
      mockHttpGet[BusinessRegistration]("testUrl", validBusinessRegistrationResponse)

      when(mockBusRegConnector.retrieveMetadata(Matchers.any[HeaderCarrier](), Matchers.any()))
        .thenReturn(BusinessRegistrationSuccessResponse(validBusinessRegistrationResponse))

      mockHttpPOST[JsValue, BusinessRegistration](connector.businessRegUrl, validBusinessRegistrationResponse)

      await(connector.retrieveAndUpdateCompletionCapacity("","")) shouldBe validBusinessRegistrationResponse
    }
  }

  "updatePrePopContactDetails" should {

    val companyDetails = CompanyContactDetailsApi(
      Some("telNo"),
      Some("mobNo"),
      Some("email")
    )

    "return true when a 200 is returned from BR" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any[ExecutionContext]))
        .thenReturn(Future.successful(HttpResponse(200)))

      val result = await(connector.updatePrePopContactDetails(registrationId, companyDetails))

      result shouldBe true
    }

    "return false when any 4xx response is returned from BR" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any[ExecutionContext]))
        .thenReturn(Future.failed(new BadRequestException("test")))

      val result = await(connector.updatePrePopContactDetails(registrationId, companyDetails))

      result shouldBe false
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
      when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any[ExecutionContext]))
        .thenReturn(Future.successful(HttpResponse(200)))

      val result = await(connector.updatePrePopAddress(registrationId, address))

      result shouldBe true
    }

    "return false when any 4xx response is returned from BR" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any[ExecutionContext]))
        .thenReturn(Future.failed(new BadRequestException("test")))

      val result = await(connector.updatePrePopAddress(registrationId, address))

      result shouldBe false
    }
  }
}