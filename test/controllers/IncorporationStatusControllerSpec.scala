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

package controllers

import config.FrontendAuthConnector
import connectors.DynamicStubConnector
import controllers.test.IncorporationStatusController
import models.IncorporationResponse
import org.mockito.Matchers
import org.scalatest.mockito.MockitoSugar
import org.mockito.Mockito._
import play.api.test.FakeRequest
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import play.api.test.Helpers._
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.ws.WSHttp

import scala.concurrent.Future

class IncorporationStatusControllerSpec extends UnitSpec with WithFakeApplication with MockitoSugar {

  val mockHttp = mock[WSHttp]

  val mockAuthConnector= mock[AuthConnector]
  val mockStubConnector = mock[DynamicStubConnector]

  val resp = new IncorporationResponse("123456789","processing","n/a")

  val returnedList : List[IncorporationResponse] = List(resp)

  class Setup {
    object TestController extends IncorporationStatusController {
      val authConnector = mockAuthConnector
      val chApiConnector = mockStubConnector
    }
  }

  "Auth Connector" should {
    "be the FrontendAuthConnector" in {
      IncorporationStatusController.authConnector shouldBe FrontendAuthConnector
    }
  }

  "Chapi Connector" should {
    "be the DynamicStubConnector" in {
      IncorporationStatusController.chApiConnector shouldBe DynamicStubConnector
    }
  }

  "Sending a GET to the IncorpStatusController" should {
    "return a 200" in new Setup{
      when(mockStubConnector.getIncorporationStatus(Matchers.eq("123456789"))(Matchers.any()))
        .thenReturn(Future.successful(Some(returnedList)))

      val result = TestController.getIncorporationStatus(resp._id)(FakeRequest())
      status(result) shouldBe OK
    }
  }
}
