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
import models.SendTemplatedEmailRequest
import org.scalatest.BeforeAndAfter
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status._
import uk.gov.hmrc.http._

class SendTemplatedEmailConnectorSpec extends SCRSSpec with UnitSpec with MockitoSugar with BeforeAndAfter {

  val mockSendTemplatedEmailConnector = mock[SendTemplatedEmailConnector]


  trait Setup {
    val connector = new SendTemplatedEmailConnector {
      override val sendTemplatedEmailURL = "http://sendTemplatedEmailURL"
      override val httpClientV2 = mockHttpClientV2

    }
  }

  val verifiedEmail = Array("foo@bar.com")
  val returnLinkURL = "http://registeryourcompanyurl"
  val emailRequest = SendTemplatedEmailRequest(
    verifiedEmail,
    "register_your_company_welcome_email",
    Map(
    "returnLink" -> returnLinkURL),
    false
  )

  "send Templated Email" should {

    "Return a true when a request to send a new templated email is successful" in new Setup {
      mockHttpPOST(url"${connector.sendTemplatedEmailURL}", HttpResponse(ACCEPTED, ""))

      await(connector.requestTemplatedEmail(emailRequest)) mustBe true
    }

    "Fail the future when the service cannot be found" in new Setup {
      mockHttpFailedPOST(new NotFoundException("error"))

      intercept[TemplateEmailErrorResponse](await(connector.requestTemplatedEmail(emailRequest)))
    }

    "Fail the future when we send a bad request" in new Setup {
      mockHttpFailedPOST(new BadRequestException("error"))
      intercept[TemplateEmailErrorResponse](await(connector.requestTemplatedEmail(emailRequest)))
    }

    "Fail the future when EVS returns an internal server error" in new Setup {
      mockHttpFailedPOST(new InternalServerException("error"))
      intercept[TemplateEmailErrorResponse](await(connector.requestTemplatedEmail(emailRequest)))
    }

    "Fail the future when EVS returns an upstream error" in new Setup {
      mockHttpFailedPOST(new BadGatewayException("error"))
      intercept[TemplateEmailErrorResponse](await(connector.requestTemplatedEmail(emailRequest)))
    }

  }

  "customRead" should{
    "return a 200" in new Setup {
      val expected = HttpResponse(OK, "")
      val result = connector.customRead("test","test", expected)
      result.status mustBe expected.status
    }
    "return a 409" in new Setup {
      val expected = HttpResponse(CONFLICT, "")
      val result = connector.customRead("test","test", HttpResponse(CONFLICT, ""))
      result.status mustBe expected.status
    }
    "return a BadRequestException" in new Setup {
      val response = HttpResponse(BAD_REQUEST, "")
      intercept[BadRequestException](connector.customRead("test","test", response))
    }
    "return a NotFoundException" in new Setup {
      val response = HttpResponse(NOT_FOUND, "")
      intercept[NotFoundException](connector.customRead("test","test", response))
    }
    "return an InternalServerException" in new Setup {
      val response = HttpResponse(INTERNAL_SERVER_ERROR, "")
      intercept[InternalServerException](connector.customRead("test","test", response))
    }
    "return a BadGatewayException" in new Setup {
      val response = HttpResponse(BAD_GATEWAY, "")
      intercept[BadGatewayException](connector.customRead("test","test", response))
    }
    "return an upstream 4xx" in new Setup {
      val response = HttpResponse(UNAUTHORIZED, "")
      intercept[Exception](connector.customRead("test","test", response))
    }
  }
}