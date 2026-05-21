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

import connectors.DataCacheConnector
import helpers.UnitSpec
import mocks.SCRSMocks
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.cache.CacheItem

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DataCacheServiceSpec extends UnitSpec with GuiceOneAppPerSuite with MockitoSugar with SCRSMocks {

  implicit val hc: HeaderCarrier = HeaderCarrier()
  private val mockConnector      = mock[DataCacheConnector]

  implicit val format: Format[TestData] = Json.format[TestData]
  private val service                   = new DataCacheService(mockConnector)

  private def setup() = {
    val mockConnector = mock[DataCacheConnector]
    val service       = new DataCacheService(mockConnector)

    (mockConnector, service)
  }

  case class TestData(value: String)

  "saveForm" should {

    "save data via connector" in {

      val userId = "user-123"
      val formId = "form-abc"
      val data   = TestData("test")

      val cacheItem = CacheItem(
        id = userId,
        data = Json.obj(formId -> Json.toJson(data)),
        createdAt = java.time.Instant.now(),
        modifiedAt = java.time.Instant.now()
      )

      when(
        mockConnector.saveForm[TestData](
          eqTo(userId),
          eqTo(formId),
          eqTo(data)
        )(any(), any(), any())
      ).thenReturn(Future.successful(cacheItem))

      val result =
        service.saveForm(userId, formId, data).futureValue

      result mustBe cacheItem

      verify(mockConnector, times(1))
        .saveForm[TestData](userId, formId, data)
    }
  }

  "fetchForm" should {

    "return cached form data" in {

      val (mockConnector, service) = setup()

      val userId = "user-123"
      val formId = "form-abc"

      val cachedData = TestData("cached")

      when(
        mockConnector.fetchAndGet[TestData](
          eqTo(userId),
          eqTo(formId)
        )(any(), any(), any())
      ).thenReturn(Future.successful(Some(cachedData)))

      val result =
        service.fetchForm[TestData](userId, formId).futureValue

      result mustBe Some(cachedData)

      verify(mockConnector)
        .fetchAndGet[TestData](userId, formId)
    }

    "return None when no cached data exists" in {

      val (mockConnector, service) = setup()

      val userId = "user-123"
      val formId = "form-abc"

      when(
        mockConnector.fetchAndGet[TestData](
          eqTo(userId),
          eqTo(formId)
        )(any(), any(), any())
      ).thenReturn(Future.successful(None))

      val result =
        service.fetchForm[TestData](userId, formId).futureValue

      result mustBe None

      verify(mockConnector)
        .fetchAndGet[TestData](userId, formId)
    }
  }

  "clearCache" should {

    "clear cached data for the user" in {

      val userId = "user-123"

      when(
        mockConnector.clear(
          eqTo(userId)
        )(any(), any())
      ).thenReturn(Future.successful(()))

      val result: Unit =
        service.clearCache(userId).futureValue

      result mustBe ()

      verify(mockConnector, times(1))
        .clear(userId)
    }
  }
}
