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

package services.internal

import connectors.IncorpInfoConnector
import org.mockito.Matchers.{any, eq => eqTo}
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class TestIncorporationServiceSpec extends UnitSpec with MockitoSugar {

  val mockIncorpInfoConnector = mock[IncorpInfoConnector]

  class Setup {
    val service = new TestIncorporationService {
      val incorpInfoConnector = mockIncorpInfoConnector
    }
  }
  implicit val hc = HeaderCarrier()
  "incorporateTransactionId" should {
    val transId = "transactionID"

    "return exception if the incorporation update did not get injected correctly" in new Setup {
      when(mockIncorpInfoConnector.injectTestIncorporationUpdate(eqTo(transId), eqTo(true))(any[HeaderCarrier]()))
        .thenReturn(Future.failed(new Exception("foo")))

      when(mockIncorpInfoConnector.manuallyTriggerIncorporationUpdate(any[HeaderCarrier]()))
        .thenReturn(Future.successful(true))

      intercept[Exception](await(service.incorporateTransactionId(transId, isSuccess = true)))
    }

    "return false if the trigger was not fired correctly" in new Setup {
      when(mockIncorpInfoConnector.injectTestIncorporationUpdate(eqTo(transId), eqTo(false))(any[HeaderCarrier]()))
        .thenReturn(Future.successful(true))

      when(mockIncorpInfoConnector.manuallyTriggerIncorporationUpdate(any[HeaderCarrier]()))
        .thenReturn(Future.failed(new Exception("foo")))

      intercept[Exception](await(service.incorporateTransactionId(transId, isSuccess = false)))
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