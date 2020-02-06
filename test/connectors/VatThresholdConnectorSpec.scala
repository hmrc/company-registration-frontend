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

package connectors

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import helpers.SCRSSpec
import org.joda.time.LocalDate
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatest.concurrent.Eventually
import org.scalatestplus.mockito.MockitoSugar
import play.api.Logger
import play.api.http.Status._
import play.api.libs.json.{JsNull, Json}
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.test.{LogCapturing, UnitSpec, WithFakeApplication}

import scala.concurrent.{ExecutionContext, Future}

class VatThresholdConnectorSpec extends SCRSSpec with UnitSpec with WithFakeApplication with LogCapturing with MockitoSugar with Eventually with BeforeAndAfter {

  val baseUrl: String = "test vatBaseURL"
  val baseUri: String = "test vatserviceUri"

  trait Setup {
    val connector = new VatThresholdConnector {
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
      logs.size shouldBe count
      logs.head.getMessage shouldBe msg
      logs.head.getLevel shouldBe level
    }
    "use the correct url" in new Setup {
      connector.serviceBaseUrl shouldBe "test vatBaseURL"
      connector.serviceUri shouldBe "test vatserviceUri"
    }
    "returns a threshold amount with a successful call" in new Setup {

      mockHttpGet(connector.url, HttpResponse(OK))

      when(mockWSHttp.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any[ExecutionContext]))
        .thenReturn(Future.successful(HttpResponse(200, Some(returnJson))))

      await(connector.getVATThreshold(todayDate)) shouldBe thresholdAmount
    }

    "returns a particular log entry when the taxable-threshold key is missing from the returned JSON from the VR Threshold service" in new Setup {

      mockHttpGet(connector.url, HttpResponse(OK))

      when(mockWSHttp.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any[ExecutionContext]))
        .thenReturn(Future.successful(HttpResponse(200, Some(noTaxableThresholdReturnJson))))

      withCaptureOfLoggingFrom(Logger) { logs =>
        connector.getVATThreshold(todayDate)
        eventually {
          logs.size shouldBe 1
        }
        found(logs)(1, "[getVATThreshold] taxable-threshold key not found", Level.ERROR)
      }
    }

    "returns a particular log entry when the taxable-threshold key is not a string in the returned JSON from the VR Threshold service" in new Setup {

      mockHttpGet(connector.url, HttpResponse(OK))

      when(mockWSHttp.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any[ExecutionContext]))
        .thenReturn(Future.successful(HttpResponse(200, Some(taxableThresholdNotAStringJson))))

      withCaptureOfLoggingFrom(Logger) { logs =>
        connector.getVATThreshold(todayDate)
        eventually {
          logs.size shouldBe 1
        }
        found(logs)(1, "[getVATThreshold] taxable-threshold is not a string", Level.ERROR)
      }
    }

    "returns a particular log entry when empty JSON s returnedfrom the VR Threshold service" in new Setup {

      mockHttpGet(connector.url, HttpResponse(OK))

      when(mockWSHttp.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any[ExecutionContext]))
        .thenReturn(Future.successful(HttpResponse(200, Some(JsNull))))

      withCaptureOfLoggingFrom(Logger) { logs =>
        connector.getVATThreshold(todayDate)
        eventually {
          logs.size shouldBe 1
        }
        found(logs)(1, s"[getVATThreshold] taxable-threshold for $todayDate not found", Level.ERROR)
      }
    }

    "returns a notfound error when 404 received from VAT REG Threshold service" in new Setup {

      mockHttpFailedGET(connector.url, new NotFoundException("NOTFOUND"))

      withCaptureOfLoggingFrom(Logger) { logs =>
        connector.getVATThreshold(todayDate)
        eventually {
          logs.size shouldBe 1
        }
        found(logs)(1, "[VATThresholdConnector] [getVATThreshold] uk.gov.hmrc.http.NotFoundException: NOTFOUND", Level.ERROR)
      }
    }

    "returns an internal server error when 500 received from VAT REG Threshold service" in new Setup {

      mockHttpFailedGET(connector.url, new InternalServerException("INTERNALSERVERERROR"))

      withCaptureOfLoggingFrom(Logger) { logs =>
        connector.getVATThreshold(todayDate)
        eventually {
          logs.size shouldBe 1
        }
        found(logs)(1, "[VATThresholdConnector] [getVATThreshold] uk.gov.hmrc.http.InternalServerException: INTERNALSERVERERROR", Level.ERROR)
      }
    }

    "returns a bad gateway error when 502 received from VAT REG Threshold service" in new Setup {

      mockHttpFailedGET(connector.url, new BadGatewayException("BADGATEWAYERROR"))

      withCaptureOfLoggingFrom(Logger) { logs =>
        connector.getVATThreshold(todayDate)
        eventually {
          logs.size shouldBe 1
        }
        found(logs)(1, "[VATThresholdConnector] [getVATThreshold] uk.gov.hmrc.http.BadGatewayException: BADGATEWAYERROR", Level.ERROR)
      }
    }
  }
}
