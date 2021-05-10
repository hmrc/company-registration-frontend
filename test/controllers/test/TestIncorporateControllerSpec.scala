/*
 * Copyright 2021 HM Revenue & Customs
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

package controllers.test

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import services.internal.TestIncorporationService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class TestIncorporateControllerSpec extends UnitSpec with MockitoSugar with GuiceOneAppPerSuite {

  implicit val system = ActorSystem("test")

  implicit def mat: Materializer = ActorMaterializer()

  val mockCheckIncorporationService = mock[TestIncorporationService]
  lazy val mockMcc = app.injector.instanceOf[MessagesControllerComponents]

  class Setup {
    val controller = new TestIncorporateController(mockMcc) {
      val checkIncorpService = mockCheckIncorporationService
    }
  }

  "incorporate" should {
    val transId = "transactionID"
    implicit val hc = HeaderCarrier()

    "return an OK when the endpoints have been hit successfully" in new Setup {
      when(mockCheckIncorporationService.incorporateTransactionId(eqTo(transId), eqTo(false))(any[HeaderCarrier]()))
        .thenReturn(Future.successful(true))

      val result = await(controller.incorporate(transId, false)(FakeRequest()))
      status(result) shouldBe 200
      bodyOf(result) shouldBe s"[SUCCESS] incorporating $transId"
    }

    "return a 500 when the endpoints have returned unsuccessfully" in new Setup {
      when(mockCheckIncorporationService.incorporateTransactionId(eqTo(transId), eqTo(true))(any[HeaderCarrier]()))
        .thenReturn(Future.failed(new Exception("")))

      intercept[Exception](await(controller.incorporate(transId, true)(FakeRequest())))
    }
  }
}