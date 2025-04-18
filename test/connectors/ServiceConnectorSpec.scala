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

import helpers.SCRSSpec
import models.external.OtherRegStatus
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import play.api.http.Status._
import uk.gov.hmrc.http._
import java.time._

import scala.concurrent.Future

class ServiceConnectorSpec extends SCRSSpec {

  val baseUrl = "http://test-service-base-url"
  val baseUri = "/test-service-base-uri"
  val regId = "test-regId"

  trait Setup {
    val connector = new ServiceConnector {
      override val serviceBaseUrl = baseUrl
      override val serviceUri = baseUri
      override val httpClientV2 = mockHttpClientV2
    }
  }

  "getStatus" should {

    val url = s"$baseUrl$baseUri/$regId/status"
    val localDate = LocalDateTime.now()
    val ackRef = "testAckRef"
    val status = OtherRegStatus("testStatus", Some(localDate), Some(ackRef), Some("foo"), Some("bar"))

    "return a SuccessfulResponse when a 200 is received" in new Setup {
      mockHttpGET[OtherRegStatus](url"$url", Future.successful(status))
      val result = await(connector.getStatus(regId))
      result mustBe SuccessfulResponse(status)
    }

    "return a NotStarted when a 404 is received" in new Setup {
      mockHttpFailedGET(new NotFoundException(""))
      val result = await(connector.getStatus(regId))
      result mustBe NotStarted
    }

    "return a ErrorResponse when ArgumentMatchers.any other http response code is returned" in new Setup {
      mockHttpFailedGET(new HttpException("", 500))
      val result = await(connector.getStatus(regId))
      result mustBe ErrorResponse
    }

    "return a ErrorResponse when a non-http exception is thrown" in new Setup {
      when(mockHttpClientV2.get(ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.execute[OtherRegStatus](ArgumentMatchers.any(),ArgumentMatchers.any()))
        .thenReturn(Future.failed(new Throwable("")))
      val result = await(connector.getStatus(regId))
      result mustBe ErrorResponse
    }
  }

  "canStatusBeCancelled" should {
    val localDate = LocalDateTime.now()
    val ackRef = "testAckRef"
    val status = OtherRegStatus("testStatus", Some(localDate), Some(ackRef), Some("foo"), None)

      "return cancelURL if user has regID and paye getStatus returns a status containing the cancelURL" in new Setup {

      val result = connector.canStatusBeCancelled("test-regId")(f => Future.successful(
        SuccessfulResponse(status)))
         val response = await(result)

        response mustBe "foo"
      }

    "throw cantCancel if no response is returned from getStatus" in new Setup {
      val result = connector.canStatusBeCancelled("test-regId")(f => Future.successful(
        ErrorResponse))
      val response = intercept[cantCancelT] {await(result)}
       response mustBe cantCancel
    }
    "throw cantCancel if response  is returned from getStatus with NO cancelURL" in new Setup {
      val result = connector.canStatusBeCancelled("test-regId")(f => Future.successful(
        SuccessfulResponse(OtherRegStatus("test-regId", Some(localDate), Some(ackRef),None, Some("bar")))))
      val response = intercept[cantCancelT] {await(result)}
      response mustBe cantCancel
    }
  }
  "cancelReg" should {

    val localDate = LocalDateTime.now()
    val ackRef = "testAckRef"
    val status = OtherRegStatus("testStatus", Some(localDate), Some(ackRef), Some("http://foo"), None)
    "return Cancelled if user has cancelURL in paye status and delete is successful" in new Setup {

      mockHttpDELETE(HttpResponse(200, ""))
      val s = (t: String) => Future.successful(SuccessfulResponse(status))
       val result = connector.cancelReg("test-regId")(s)

      val response = await(result)
      response mustBe Cancelled
    }
    "return NotCancelled if getStatus returns ArgumentMatchers.anything but success" in new Setup {
      mockHttpDELETE(HttpResponse(200, ""))
      val s = (t: String) => Future.successful(ErrorResponse)
      val result = connector.cancelReg("test-regId")(s)

      val response = await(result)
      response mustBe NotCancelled
    }

    "return Cancelled if user has cancelURL in paye status and delete is successful with different parameters in HTTP response" in new Setup {
      mockHttpDELETE(HttpResponse(OK,"",Map("" -> Seq(""))))
      val s = (t: String) => Future.successful(SuccessfulResponse(status))
      val result = connector.cancelReg("test-regId")(s)

      val response = await(result)
      response mustBe Cancelled
    }
    "return NotCancelled if user has cancelURL in paye status and delete does not return 200" in new Setup {
      mockHttpDELETE(HttpResponse(BAD_REQUEST,"",Map("" -> Seq(""))))
      val result = connector.cancelReg("test-regId")(f => Future.successful(
        SuccessfulResponse(status)))

      val response = await(result)
      response mustBe NotCancelled
    }
    "return notcancelled if user does not have cancelURL " in new Setup {
      mockHttpDELETE(HttpResponse(BAD_REQUEST,"",Map("" -> Seq(""))))

      val result = connector.cancelReg("test-regId")(f => Future.successful(
        SuccessfulResponse(OtherRegStatus("", None, None, None, None))))

      val response = await(result)
      response mustBe NotCancelled
    }

    "return notcancelled if PAYE registration returns a 400" in new Setup {
      mockHttpDELETE(new BadRequestException(""))
      val result = connector.cancelReg("test-regId")(f => Future.successful(
        SuccessfulResponse(OtherRegStatus("", None, None, None, None))))

      val response = await(result)
      response mustBe NotCancelled
    }
  }
}