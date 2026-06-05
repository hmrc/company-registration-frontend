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

package mocks

import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.mockito.stubbing.OngoingStubbing
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Format
import services.DataCacheService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.cache.CacheItem

import scala.concurrent.Future

trait DataCacheServiceMock {
  this: MockitoSugar =>

  lazy val mockDataCacheService: DataCacheService =
    mock[DataCacheService]

  def mockFetchForm[T](
      formId: String,
      result: Option[T],
      mockService: DataCacheService = mockDataCacheService
  ): OngoingStubbing[Future[Option[T]]] =
    when(
      mockService.fetchForm[T](
        ArgumentMatchers.anyString(),
        ArgumentMatchers.contains(formId)
      )(
        ArgumentMatchers.any[HeaderCarrier](),
        ArgumentMatchers.any[Format[T]]()
      )
    ).thenReturn(Future.successful(result))

  def mockSaveForm[T](
      formId: String,
      cacheItem: CacheItem,
      mockService: DataCacheService = mockDataCacheService
  ): OngoingStubbing[Future[CacheItem]] =
    when(
      mockService.saveForm[T](
        ArgumentMatchers.anyString(),
        ArgumentMatchers.contains(formId),
        ArgumentMatchers.any[T]()
      )(
        ArgumentMatchers.any[HeaderCarrier](),
        ArgumentMatchers.any[Format[T]]()
      )
    ).thenReturn(Future.successful(cacheItem))

  def mockClearCache(
      mockService: DataCacheService = mockDataCacheService
  ): OngoingStubbing[Future[Unit]] =
    when(
      mockService.clearCache(
        ArgumentMatchers.anyString()
      )(
        ArgumentMatchers.any[HeaderCarrier]()
      )
    ).thenReturn(Future.successful(()))
}
