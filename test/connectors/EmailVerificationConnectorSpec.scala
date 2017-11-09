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

package connectors

import config.WSHttp
import helpers.SCRSSpec
import uk.gov.hmrc.play.http._
import models.EmailVerificationRequest
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.http.{CoreGet, CorePost}

import scala.concurrent.ExecutionContext
//import org.scalatestplus.play.OneServerPerSuite
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import play.api.http.Status._
import play.api.libs.json.JsValue

import scala.concurrent.Future
import uk.gov.hmrc.http.{ BadGatewayException, BadRequestException, HeaderCarrier, HttpResponse, InternalServerException, NotFoundException, Upstream4xxResponse }

class EmailVerificationConnectorSpec extends SCRSSpec with UnitSpec with WithFakeApplication with MockitoSugar with BeforeAndAfter {

  val mockEmailVerificationConnector = mock[EmailVerificationConnector]


  trait Setup {
    val connector = new EmailVerificationConnector {
      override val sendVerificationEmailURL = "test sendVerificationEmailURL"
      override val checkVerifiedEmailURL = "test checkVerifiedEmailURL"
      override val http = mockWSHttp
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

    "use the correct sendVerificationEmailURL" in {
      EmailVerificationConnector.sendVerificationEmailURL shouldBe "http://localhost:9891/email-verification/verification-requests"
    }
    "use the correct checkVerifiedEmailURL" in {
      EmailVerificationConnector.checkVerifiedEmailURL shouldBe "http://localhost:9891/email-verification/verified-email-addresses"
    }
  }


  "checkVerifiedEmail" should {

    "Return a true when passed an email that has been verified" in new Setup {
      mockHttpGet(connector.checkVerifiedEmailURL, HttpResponse(OK))

      await(connector.checkVerifiedEmail(verifiedEmail)) shouldBe true
    }

    "Return a false when passed an email that exists but has not been found or not been verified" in new Setup {
      when(mockWSHttp.GET[HttpResponse](Matchers.anyString())(Matchers.any(), Matchers.any[HeaderCarrier](), Matchers.any[ExecutionContext]))
        .thenReturn(Future.failed(new NotFoundException("error")))

      await(connector.checkVerifiedEmail(verifiedEmail)) shouldBe false
    }

    "Return a false when passed an email but met an unexpected error" in new Setup {
      when(mockWSHttp.GET[HttpResponse](Matchers.anyString())(Matchers.any(), Matchers.any[HeaderCarrier](), Matchers.any[ExecutionContext]))
        .thenReturn(Future.failed(new InternalServerException("error")))

      await(connector.checkVerifiedEmail(verifiedEmail)) shouldBe false
    }

    "Return a false when passed an email but encountered an upstream service error" in new Setup {
      when(mockWSHttp.GET[HttpResponse](Matchers.anyString())(Matchers.any(), Matchers.any[HeaderCarrier](), Matchers.any[ExecutionContext]))
        .thenReturn(Future.failed(new BadGatewayException("error")))

      await(connector.checkVerifiedEmail(verifiedEmail)) shouldBe false
    }

  }


  "requestVerificationEmail" should {

    "Return a true when a new email verification request is successful" in new Setup {
      mockHttpPOST(connector.sendVerificationEmailURL, HttpResponse(CREATED))

      await(connector.requestVerificationEmail(verificationRequest)) shouldBe true
    }


    "Return a false when a new email verification request has already been sent" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any[ExecutionContext]))
        .thenReturn(Future.successful(HttpResponse(409)))

      await(connector.requestVerificationEmail(verificationRequest)) shouldBe false
    }

    "Fail the future when the service cannot be found" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any[ExecutionContext]))
        .thenReturn(Future.failed(new NotFoundException("error")))

      intercept[EmailErrorResponse](await(connector.requestVerificationEmail(verificationRequest)))
    }

    "Fail the future when we send a bad request" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any[ExecutionContext]))
        .thenReturn(Future.failed(new BadRequestException("error")))

      intercept[EmailErrorResponse](await(connector.requestVerificationEmail(verificationRequest)))
    }

    "Fail the future when EVS returns an internal server error" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any[ExecutionContext]))
        .thenReturn(Future.failed(new InternalServerException("error")))

      intercept[EmailErrorResponse](await(connector.requestVerificationEmail(verificationRequest)))
    }

    "Fail the future when EVS returns an upstream error" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any[ExecutionContext]))
        .thenReturn(Future.failed(new BadGatewayException("error")))

      intercept[EmailErrorResponse](await(connector.requestVerificationEmail(verificationRequest)))
    }

  }

  "customRead" should{
    "return a 200" in new Setup {
      val expected = HttpResponse(OK)
      val result = connector.customRead("test","test", expected)
      result.status shouldBe expected.status
    }
    "return a 409" in new Setup {
      val expected = HttpResponse(CONFLICT)
      val result = connector.customRead("test","test", HttpResponse(CONFLICT))
      result.status shouldBe expected.status
    }
    "return a BadRequestException" in new Setup {
      val response = HttpResponse(BAD_REQUEST)
      intercept[BadRequestException](connector.customRead("test","test", response))
    }
    "return a NotFoundException" in new Setup {
      val response = HttpResponse(NOT_FOUND)
      intercept[NotFoundException](connector.customRead("test","test", response))
    }
    "return an InternalServerException" in new Setup {
      val response = HttpResponse(INTERNAL_SERVER_ERROR)
      intercept[InternalServerException](connector.customRead("test","test", response))
    }
    "return a BadGatewayException" in new Setup {
      val response = HttpResponse(BAD_GATEWAY)
      intercept[BadGatewayException](connector.customRead("test","test", response))
    }
    "return an upstream 4xx" in new Setup {
      val response = HttpResponse(UNAUTHORIZED)
      intercept[Upstream4xxResponse](connector.customRead("test","test", response))
    }
  }


}
