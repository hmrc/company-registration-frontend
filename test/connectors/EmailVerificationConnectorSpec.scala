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

import helpers.{SCRSSpec, UnitSpec}
import models.EmailVerificationRequest
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status._
import play.api.libs.json.{JsObject, JsValue}
import uk.gov.hmrc.http._

import scala.concurrent.{ExecutionContext, Future}

class EmailVerificationConnectorSpec extends SCRSSpec with UnitSpec with MockitoSugar with BeforeAndAfter {


  trait Setup {
    val connector = new EmailVerificationConnector {
      override val sendVerificationEmailURL = "test sendVerificationEmailURL"
      override val checkVerifiedEmailURL = "test checkVerifiedEmailURL"
      override val wSHttp = mockWSHttp
    }
  }

  val verifiedEmail = "foo@bar.com"

  val verificationRequest = EmailVerificationRequest(
    "testEmail",
    "register_your_company_verification_email",
    Map(),
    "linkExpiry",
    "aContinueURL"
  )

  "Email Verification Connector" should {

    "use the correct sendVerificationEmailURL" in new Setup {
      connector.sendVerificationEmailURL mustBe "test sendVerificationEmailURL"
    }
    "use the correct checkVerifiedEmailURL" in new Setup {
      connector.checkVerifiedEmailURL mustBe "test checkVerifiedEmailURL"
    }
  }

  "checkVerifiedEmail" should {

    "Return a true when passed an email that has been verified" in new Setup {
      mockHttpPOST(connector.checkVerifiedEmailURL, HttpResponse(OK, ""))

      await(connector.checkVerifiedEmail(verifiedEmail)) mustBe true
    }

    "Return a false when passed an email that exists but has not been found or not been verified" in new Setup {
      when(mockWSHttp.POST[JsObject, HttpResponse](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any(),ArgumentMatchers.any(),ArgumentMatchers.any(),ArgumentMatchers.any[ExecutionContext]()))
        .thenReturn(Future.failed(new NotFoundException("error")))

      await(connector.checkVerifiedEmail(verifiedEmail)) mustBe false
    }

    "Return a false when passed an email but met an unexpected error" in new Setup {
      when(mockWSHttp.POST[JsObject, HttpResponse](ArgumentMatchers.anyString(),ArgumentMatchers.any(),ArgumentMatchers.any())
        (ArgumentMatchers.any(),ArgumentMatchers.any(),ArgumentMatchers.any(),ArgumentMatchers.any[ExecutionContext]()))
        .thenReturn(Future.failed(new InternalServerException("error")))

      await(connector.checkVerifiedEmail(verifiedEmail)) mustBe false
    }

    "Return a false when passed an email but encountered an upstream service error" in new Setup {
      when(mockWSHttp.POST[JsObject, HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(),ArgumentMatchers.any(),ArgumentMatchers.any(),ArgumentMatchers.any[ExecutionContext]()))
        .thenReturn(Future.failed(new BadGatewayException("error")))

      await(connector.checkVerifiedEmail(verifiedEmail)) mustBe false
    }

  }


  "requestVerificationEmail" should {

    "Return a false when a new email verification request is successful to indicate the email was NOT verified before" in new Setup {
      mockHttpPOST(connector.sendVerificationEmailURL, HttpResponse(CREATED, ""))

      await(connector.requestVerificationEmailReturnVerifiedEmailStatus(verificationRequest)) mustBe false
    }


    "Return a true when a new email verification request has been sent because the email is already verified" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[ExecutionContext]))
        .thenReturn(Future.successful(HttpResponse(409, "")))

      await(connector.requestVerificationEmailReturnVerifiedEmailStatus(verificationRequest)) mustBe true
    }

    "Fail the future when the service cannot be found" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[ExecutionContext]))
        .thenReturn(Future.failed(new NotFoundException("error")))

      intercept[EmailErrorResponse](await(connector.requestVerificationEmailReturnVerifiedEmailStatus(verificationRequest)))
    }

    "Fail the future when we send a bad request" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[ExecutionContext]))
        .thenReturn(Future.failed(new BadRequestException("error")))

      intercept[EmailErrorResponse](await(connector.requestVerificationEmailReturnVerifiedEmailStatus(verificationRequest)))
    }

    "Fail the future when EVS returns an internal server error" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[ExecutionContext]))
        .thenReturn(Future.failed(new InternalServerException("error")))

      intercept[EmailErrorResponse](await(connector.requestVerificationEmailReturnVerifiedEmailStatus(verificationRequest)))
    }

    "Fail the future when EVS returns an upstream error" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[ExecutionContext]))
        .thenReturn(Future.failed(new BadGatewayException("error")))

      intercept[EmailErrorResponse](await(connector.requestVerificationEmailReturnVerifiedEmailStatus(verificationRequest)))
    }

  }

  "customRead" should {
    "return a 200" in new Setup {
      val expected = HttpResponse(OK, "")
      val result = connector.customRead("test", "test", expected)
      result.status mustBe expected.status
    }
    "return a 409" in new Setup {
      val expected = HttpResponse(CONFLICT, "")
      val result = connector.customRead("test", "test", HttpResponse(CONFLICT, ""))
      result.status mustBe expected.status
    }
    "return a BadRequestException" in new Setup {
      val response = HttpResponse(BAD_REQUEST, "")
      intercept[BadRequestException](connector.customRead("test", "test", response))
    }
    "return a NotFoundException" in new Setup {
      val response = HttpResponse(NOT_FOUND, "")
      intercept[NotFoundException](connector.customRead("test", "test", response))
    }
    "return an InternalServerException" in new Setup {
      val response = HttpResponse(INTERNAL_SERVER_ERROR, "")
      intercept[InternalServerException](connector.customRead("test", "test", response))
    }
    "return a BadGatewayException" in new Setup {
      val response = HttpResponse(BAD_GATEWAY, "")
      intercept[BadGatewayException](connector.customRead("test", "test", response))
    }
    "return an upstream 4xx" in new Setup {
      val response = HttpResponse(UNAUTHORIZED, "")
      intercept[Exception](connector.customRead("test", "test", response))
    }
  }


}
