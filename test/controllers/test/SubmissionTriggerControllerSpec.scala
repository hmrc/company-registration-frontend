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

package controllers.test

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import connectors.CompanyRegistrationConnector
import org.mockito.Matchers.any
import org.scalatest.mockito.MockitoSugar
import org.mockito.Mockito._
import play.api.test.FakeRequest
import uk.gov.hmrc.play.http.HttpResponse
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class SubmissionTriggerControllerSpec extends UnitSpec with MockitoSugar {

  implicit val system = ActorSystem("test")
  implicit def mat: Materializer = ActorMaterializer()

  val mockCRConnector = mock[CompanyRegistrationConnector]

  class Setup {
    val controller = new SubmissionTriggerController {
      val cRConnector = mockCRConnector
    }
  }

  "triggerSubmissionCheck" should {

    "return an OK when a 200 HttpResponse is returned from the connector" in new Setup {
      when(mockCRConnector.checkAndProcessNextSubmission(any()))
        .thenReturn(Future.successful(HttpResponse(200, responseString = Some("body"))))

      val result = await(controller.triggerSubmissionCheck(FakeRequest()))
      status(result) shouldBe 200
      bodyOf(result) shouldBe "body"
    }

    "return a 400 when a 400 HttpResponse is returned from the connector" in new Setup {
      when(mockCRConnector.checkAndProcessNextSubmission(any()))
        .thenReturn(Future.successful(HttpResponse(400, responseString = Some("A http exception was caught - 400 body"))))

      val result = await(controller.triggerSubmissionCheck(FakeRequest()))
      status(result) shouldBe 400
      bodyOf(result) shouldBe "A http exception was caught - 400 body"
    }

    "return a 500 when a 500 HttpResponse is returned from the connector" in new Setup {
      when(mockCRConnector.checkAndProcessNextSubmission(any()))
        .thenReturn(Future.successful(HttpResponse(500, responseString = Some("A http exception was caught - 500 body"))))

      val result = await(controller.triggerSubmissionCheck(FakeRequest()))
      status(result) shouldBe 500
      bodyOf(result) shouldBe "A http exception was caught - 500 body"
    }
  }
}
