/*
 * Copyright 2018 HM Revenue & Customs
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

package services.internal

import connectors.{CohoAPIConnector, CohoApiSuccessResponse, IncorpInfoConnector}
import org.mockito.Matchers.{any, eq => eqTo}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class CheckIncorporationServiceSpec extends UnitSpec with MockitoSugar {

  val mockCohoApiConnector = mock[CohoAPIConnector]
  val mockIncorpInfoConnector = mock[IncorpInfoConnector]

  class Setup {
    val service = new CheckIncorporationService {
      val cohoApiConnector = mockCohoApiConnector
      val incorpInfoConnector = mockIncorpInfoConnector
    }
  }

  implicit val hc = HeaderCarrier()

  "fetchIncorporationStatus" should {

    val timePoint = Some("123456789")
    val itemsPerPage = 1
    val queryString = "/?timepoint=123456789&items_per_page=1"
    val response = CohoApiSuccessResponse(Json.parse("""{"test":"json"}"""))

    "return a CohoApiResponse from the connector" in new Setup {
      when(mockCohoApiConnector.fetchIncorporationStatus(eqTo(timePoint), eqTo(itemsPerPage))(any()))
        .thenReturn(Future.successful(response))

      await(service.fetchIncorporationStatus(timePoint, itemsPerPage)) shouldBe response
    }
  }

  "incorporateTransactionId" should {
    val transId = "transactionID"

    "return false if the incorporation update did not get injected correctly" in new Setup {
      when(mockIncorpInfoConnector.injectTestIncorporationUpdate(eqTo(transId), eqTo(true))(any[HeaderCarrier]()))
        .thenReturn(Future.successful(false))

      when(mockIncorpInfoConnector.manuallyTriggerIncorporationUpdate(any[HeaderCarrier]()))
        .thenReturn(Future.successful(true))

      await(service.incorporateTransactionId(transId, isSuccess = true)) shouldBe false
    }

    "return false if the trigger was not fired correctly" in new Setup {
      when(mockIncorpInfoConnector.injectTestIncorporationUpdate(eqTo(transId), eqTo(false))(any[HeaderCarrier]()))
        .thenReturn(Future.successful(true))

      when(mockIncorpInfoConnector.manuallyTriggerIncorporationUpdate(any[HeaderCarrier]()))
        .thenReturn(Future.successful(false))

      await(service.incorporateTransactionId(transId, isSuccess = false)) shouldBe false
    }

    "create an Incorporation Update and trigger a response from II" in new Setup {
      when(mockIncorpInfoConnector.injectTestIncorporationUpdate(eqTo(transId), eqTo(false))(any[HeaderCarrier]()))
        .thenReturn(Future.successful(true))

      when(mockIncorpInfoConnector.manuallyTriggerIncorporationUpdate(any[HeaderCarrier]()))
        .thenReturn(Future.successful(true))

      await(service.incorporateTransactionId(transId, isSuccess = false)) shouldBe true
    }
  }
}
