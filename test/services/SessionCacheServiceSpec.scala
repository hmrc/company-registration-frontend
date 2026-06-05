/*
 * Copyright 2026 HM Revenue & Customs
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

package services

import connectors.SessionCacheConnector
import helpers.UnitSpec
import mocks.SCRSMocks
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{verify, when}
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SessionCacheServiceSpec extends UnitSpec with GuiceOneAppPerSuite with MockitoSugar with SCRSMocks {

  implicit val hc: HeaderCarrier = HeaderCarrier()
  private val mockConnector = mock[SessionCacheConnector]

  implicit val format: Format[TestModel] = Json.format[TestModel]
  private val service = new SessionCacheService(mockConnector)

  case class TestModel(name: String)

  "SessionCacheService" should {

    "save" should {

      "store data in session cache and return saved value" in {

        val key  = "test-key"
        val data = TestModel("test-name")

        when(
          mockConnector.cache[TestModel](eqTo(key), eqTo(data))(any(), any(), any())
        ).thenReturn(Future.successful(data))

        val result = service.save[TestModel](key, data).futureValue

        result mustBe data

        verify(mockConnector)
          .cache[TestModel](eqTo(key), eqTo(data))(any(), any(), any())
      }
    }

    "get" should {

      "return cached data when present" in {

        val key  = "test-key"
        val data = TestModel("cached-value")

        when(
          mockConnector.fetchAndGet[TestModel](eqTo(key))(any(), any(), any())
        ).thenReturn(Future.successful(Some(data)))

        val result = service.get[TestModel](key).futureValue

        result mustBe Some(data)

        verify(mockConnector)
          .fetchAndGet[TestModel](eqTo(key))(any(), any(), any())
      }

      "return None when cached data is absent" in {

        val key = "missing-key"

        when(
          mockConnector.fetchAndGet[TestModel](eqTo(key))(any(), any(), any())
        ).thenReturn(Future.successful(None))

        val result = service.get[TestModel](key).futureValue

        result mustBe None

        verify(mockConnector)
          .fetchAndGet[TestModel](eqTo(key))(any(), any(), any())
      }
    }

    "clear" should {

      "remove session cache data" in {

        when(mockConnector.remove()(any(), any()))
          .thenReturn(Future.successful(()))

        service.clear().futureValue

        verify(mockConnector).remove()(any(), any())
      }
    }
  }
}
