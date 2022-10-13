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

package connectors

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import helpers.{LogCapturing, SCRSSpec, UnitSpec}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatestplus.mockito.MockitoSugar
import play.api.Logger
import play.api.http.Status._
import play.api.libs.json.{JsNull, Json}
import uk.gov.hmrc.http._
import java.time.LocalDate

import scala.concurrent.{ExecutionContext, Future}

class VatThresholdConnectorSpec extends SCRSSpec with UnitSpec with LogCapturing with MockitoSugar with Eventually with BeforeAndAfter with IntegrationPatience {

  val baseUrl: String = "test vatBaseURL"
  val baseUri: String = "test vatserviceUri"

  trait Setup {
    object Connector extends VatThresholdConnector {
      override val serviceBaseUrl = baseUrl
      override val serviceUri = baseUri
      override val wSHttp = mockWSHttp
      val url = s"${serviceBaseUrl}/${serviceUri}/threshold"
    }
    val thresholdAmount = "150000"
    val returnJson = Json.parse(
      """{
        |"taxable-threshold": "150000",
        |"since": "2017-04-01"
        |}""".stripMargin)

    val taxableThresholdNotAStringJson = Json.parse(
      """{
        |"taxable-threshold": 150000,
        |"since": "2017-04-01"
        |}""".stripMargin)

    val noTaxableThresholdReturnJson = Json.parse(
      """{
        |"since": "2017-04-01"
        |}""".stripMargin)

    val todayDate = LocalDate.parse("2017-04-01")
  }

  "getVATThreshold" should {

    def found(logs: List[ILoggingEvent])(count: Int, msg: String, level: Level) = {
      logs.size mustBe count
      logs.head.getMessage mustBe msg
      logs.head.getLevel mustBe level
    }

    "use the correct url" in new Setup {
      Connector.serviceBaseUrl mustBe "test vatBaseURL"
      Connector.serviceUri mustBe "test vatserviceUri"
    }
    "returns a threshold amount with a successful call" in new Setup {

      mockHttpGet(Connector.url, HttpResponse(OK, ""))

      when(mockWSHttp.GET[HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[ExecutionContext]))
        .thenReturn(Future.successful(HttpResponse(200, json = returnJson, Map())))

      await(Connector.getVATThreshold(todayDate)) mustBe thresholdAmount
    }

    "returns a particular log entry when the taxable-threshold key is missing from the returned JSON from the VR Threshold service" in new Setup {

      mockHttpGet(Connector.url, HttpResponse(OK, ""))

      when(mockWSHttp.GET[HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[ExecutionContext]))
        .thenReturn(Future.successful(HttpResponse(200, json = noTaxableThresholdReturnJson, Map())))

      withCaptureOfLoggingFrom(Connector.logger) { logs =>
        Connector.getVATThreshold(todayDate)
        eventually {
          logs.size mustBe 1
        }
        found(logs)(1, "[Connector][getVATThreshold] taxable-threshold key not found", Level.ERROR)
      }
    }

    "returns a particular log entry when the taxable-threshold key is not a string in the returned JSON from the VR Threshold service" in new Setup {

      mockHttpGet(Connector.url, HttpResponse(OK, ""))

      when(mockWSHttp.GET[HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[ExecutionContext]))
        .thenReturn(Future.successful(HttpResponse(200, json = taxableThresholdNotAStringJson, Map())))

      withCaptureOfLoggingFrom(Connector.logger) { logs =>
        Connector.getVATThreshold(todayDate)
        eventually {
          logs.size mustBe 1
        }
        found(logs)(1, "[Connector][getVATThreshold] taxable-threshold is not a string", Level.ERROR)
      }
    }

    "returns a particular log entry when empty JSON s returnedfrom the VR Threshold service" in new Setup {

      mockHttpGet(Connector.url, HttpResponse(OK, ""))

      when(mockWSHttp.GET[HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[ExecutionContext]))
        .thenReturn(Future.successful(HttpResponse(200, json = JsNull, Map())))

      withCaptureOfLoggingFrom(Connector.logger) { logs =>
        Connector.getVATThreshold(todayDate)
        eventually {
          logs.size mustBe 1
        }
        found(logs)(1, s"[Connector][getVATThreshold] taxable-threshold for $todayDate not found", Level.ERROR)
      }
    }

    "returns a notfound error when 404 received from VAT REG Threshold service" in new Setup {
      when(mockWSHttp.GET[HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new NotFoundException("NOTFOUND")))

      withCaptureOfLoggingFrom(Connector.logger) { logs =>
        Connector.getVATThreshold(todayDate)
        eventually {
          logs.size mustBe 1
        }
        found(logs)(1, "[Connector][getVATThreshold] uk.gov.hmrc.http.NotFoundException: NOTFOUND", Level.ERROR)
      }
    }

    "returns an internal server error when 500 received from VAT REG Threshold service" in new Setup {
      when(mockWSHttp.GET[HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new InternalServerException("INTERNALSERVERERROR")))

      withCaptureOfLoggingFrom(Connector.logger) { logs =>
        Connector.getVATThreshold(todayDate)
        eventually {
          logs.size mustBe 1
        }
        found(logs)(1, "[Connector][getVATThreshold] uk.gov.hmrc.http.InternalServerException: INTERNALSERVERERROR", Level.ERROR)
      }
    }

    "returns a bad gateway error when 502 received from VAT REG Threshold service" in new Setup {
      when(mockWSHttp.GET[HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new BadGatewayException("BADGATEWAYERROR")))

      withCaptureOfLoggingFrom(Connector.logger) { logs =>
        Connector.getVATThreshold(todayDate)
        eventually {
          logs.size mustBe 1
        }
        found(logs)(1, "[Connector][getVATThreshold] uk.gov.hmrc.http.BadGatewayException: BADGATEWAYERROR", Level.ERROR)
      }
    }
  }
}
