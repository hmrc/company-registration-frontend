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

import helpers.SCRSSpec
import models.PAYEStatus
import org.joda.time.DateTime
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.http.ws.WSHttp
import org.mockito.Mockito._
import org.mockito.Matchers.{any, eq => eqTo}
import play.api.http.Status._

import scala.concurrent.Future

class PAYEConnectorSpec extends SCRSSpec {

  val mockHttp = mock[WSHttp]

  val baseUrl = "test-paye-base-url"
  val regId = "test-regId"

  trait Setup {
    val connector = new PAYEConnector {
      override val payeBaseUrl = baseUrl
      override val http = mockHttp
    }
  }

  "getStatus" should {

    val url = s"$baseUrl/paye-registration/$regId/status"
    val localDate = DateTime.now()
    val ackRef = "testAckRef"
    val payeStatus = PAYEStatus("testStatus", Some(localDate), Some(ackRef), Some("foo"), Some("bar"))

    "return a PAYESuccessfulResponse when a 200 is received" in new Setup {
      when(mockHttp.GET[PAYEStatus](eqTo(url))(any(), any())).thenReturn(Future.successful(payeStatus))

      val result = await(connector.getStatus(regId))
      result shouldBe PAYESuccessfulResponse(payeStatus)
    }

    "return a PAYENotStarted when a 404 is received" in new Setup {
      when(mockHttp.GET[PAYEStatus](eqTo(url))(any(), any())).thenReturn(Future.failed(new NotFoundException("")))

      val result = await(connector.getStatus(regId))
      result shouldBe PAYENotStarted
    }

    "return a PAYEErrorResponse when any other http response code is returned" in new Setup {
      when(mockHttp.GET[PAYEStatus](eqTo(url))(any(), any())).thenReturn(Future.failed(new HttpException("", 500)))

      val result = await(connector.getStatus(regId))
      result shouldBe PAYEErrorResponse
    }

    "return a PAYEErrorResponse when a non-http exception is thrown" in new Setup {
      when(mockHttp.GET[PAYEStatus](eqTo(url))(any(), any())).thenReturn(Future.failed(new Throwable("")))

      val result = await(connector.getStatus(regId))
      result shouldBe PAYEErrorResponse
    }
  }

  "canStatusBeCancelled" should {
    val localDate = DateTime.now()
    val ackRef = "testAckRef"
    val payeStatus = PAYEStatus("testStatus", Some(localDate), Some(ackRef), Some("foo"), None)

      "return cancelURL if user has regID and paye getStatus returns a payeStatus containing the cancelURL" in new Setup {

      val result = connector.canStatusBeCancelled("test-regId")(f => Future.successful(
        PAYESuccessfulResponse(payeStatus)))
         val response = await(result)

        response shouldBe "foo"
      }

    "throw cantCancel if no response is returned from getStatus" in new Setup {
      val result = connector.canStatusBeCancelled("test-regId")(f => Future.successful(
        PAYEErrorResponse))
      val response = intercept[cantCancelT] {await(result)}
       response shouldBe cantCancel
    }
    "throw cantCancel if response  is returned from getStatus with NO cancelURL" in new Setup {
      val result = connector.canStatusBeCancelled("test-regId")(f => Future.successful(
        PAYESuccessfulResponse(PAYEStatus("test-regId", Some(localDate), Some(ackRef),None, Some("bar")))))
      val response = intercept[cantCancelT] {await(result)}
      response shouldBe cantCancel
    }
  }
  "cancelReg" should {

    val localDate = DateTime.now()
    val ackRef = "testAckRef"
    val payeStatus = PAYEStatus("testStatus", Some(localDate), Some(ackRef), Some("foo"), None)
    "return Cancelled if user has cancelURL in paye status and delete is successful" in new Setup {

      when(mockHttp.DELETE[HttpResponse](any[String])(any(),any())).thenReturn(Future.successful(HttpResponse(200)))
      val s = (t: String) => Future.successful(PAYESuccessfulResponse(payeStatus))
       val result = connector.cancelReg("test-regId")(s)

      val response = await(result)
      response shouldBe Cancelled
    }

    "return Cancelled if user has cancelURL in paye status and delete is successful with different parameters in HTTP response" in new Setup {

      when(mockHttp.DELETE[HttpResponse](any[String])(any(),any()))
        .thenReturn(Future.successful(HttpResponse(OK,None,Map("" -> Seq("")),None)))
      val s = (t: String) => Future.successful(PAYESuccessfulResponse(payeStatus))
      val result = connector.cancelReg("test-regId")(s)

      val response = await(result)
      response shouldBe Cancelled
    }
    "return NotCancelled if user has cancelURL in paye status and delete does not return 200" in new Setup {
      when(mockHttp.DELETE[HttpResponse](any[String])(any(),any())).thenReturn(Future.successful(HttpResponse(BAD_REQUEST,None,Map("" -> Seq("")),None)))

      val result = connector.cancelReg("test-regId")(f => Future.successful(
        PAYESuccessfulResponse(payeStatus)))

      val response = await(result)
      response shouldBe NotCancelled
    }
    "return notcancelled if user does not have cancelURL " in new Setup {
      when(mockHttp.DELETE[HttpResponse](any[String])(any(),any())).thenReturn(Future.successful(HttpResponse(BAD_REQUEST,None,Map("" -> Seq("")),None)))

      val result = connector.cancelReg("test-regId")(f => Future.successful(
        PAYESuccessfulResponse(PAYEStatus("", None, None, None, None))))

      val response = await(result)
      response shouldBe NotCancelled
    }

    "return notcancelled if PAYE registration returns a 400" in new Setup {
      when(mockHttp.DELETE[HttpResponse](any[String])(any(),any())).thenReturn(Future.failed(new BadRequestException("")))

      val result = connector.cancelReg("test-regId")(f => Future.successful(
        PAYESuccessfulResponse(PAYEStatus("", None, None, None, None))))

      val response = await(result)
      response shouldBe NotCancelled
    }
  }

}
