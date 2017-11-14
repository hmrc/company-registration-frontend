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

package services.internal

import connectors.{CohoApiSuccessResponse, CohoAPIConnector}
import org.mockito.Matchers
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.Json
import uk.gov.hmrc.play.test.UnitSpec
import org.mockito.Mockito._

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

class CheckIncorporationServiceSpec extends UnitSpec with MockitoSugar {

  val mockCohoApiConnector = mock[CohoAPIConnector]

  class Setup {
    val service = new CheckIncorporationService {
      val cohoApiConnector = mockCohoApiConnector
    }
  }

  "fetchIncorporationStatus" should {

    val timePoint = Some("123456789")
    val itemsPerPage = 1
    val queryString = "/?timepoint=123456789&items_per_page=1"
    val response = CohoApiSuccessResponse(Json.parse("""{"test":"json"}"""))

    implicit val hc = HeaderCarrier()

    "return a CohoApiResponse from the connector" in new Setup {
      when(mockCohoApiConnector.fetchIncorporationStatus(Matchers.eq(timePoint), Matchers.eq(itemsPerPage))(Matchers.any()))
        .thenReturn(Future.successful(response))

      await(service.fetchIncorporationStatus(timePoint, itemsPerPage)) shouldBe response
    }
  }
}
