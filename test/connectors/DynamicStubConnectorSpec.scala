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

package connectors

import models.IncorporationResponse
import org.scalatest.mockito.MockitoSugar
import org.mockito.Mockito._
import org.mockito.Matchers
import play.api.test.FakeRequest
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.ws.WSHttp
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http.{CoreDelete, CoreGet, CorePost, HeaderCarrier}

class DynamicStubConnectorSpec extends UnitSpec with WithFakeApplication with MockitoSugar with ServicesConfig {

  val mockHttp = mock[WSHttp with CoreGet]

  val resp = new IncorporationResponse("123456789","processing","n/a")

  val returnedList : List[IncorporationResponse] = List(resp)

  implicit val hc: HeaderCarrier = HeaderCarrier()

  class Setup {
    object TestConnector extends DynamicStubConnector {
      val http = mockHttp
      val stubUrl = baseUrl("incorp-dy-stub")
      val busRegDyUrl = baseUrl("business-registration-dynamic-stub")
    }
  }

  "FetchAllClients" should {
    "return a list" in new Setup {
      when(mockHttp.GET[Option[List[IncorporationResponse]]](Matchers.any())(Matchers.any(), Matchers.any[HeaderCarrier](), Matchers.any[ExecutionContext]()))
        .thenReturn(Future.successful(Some(returnedList)))
      val response = TestConnector.getIncorporationStatus(resp._id)
      await(response).getClass shouldBe Some(returnedList).getClass
    }
  }
}
