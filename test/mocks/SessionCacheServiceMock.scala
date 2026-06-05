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
import org.mockito.Mockito.when
import org.mockito.stubbing.OngoingStubbing
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Format
import services.SessionCacheService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

trait SessionCacheServiceMock {
  this: MockitoSugar =>

  lazy val mockSessionCacheService: SessionCacheService =
    mock[SessionCacheService]

  def mockSessionCacheGet[T](
      key: String,
      model: Option[T]
  ): OngoingStubbing[Future[Option[T]]] =
    when(
      mockSessionCacheService.get[T](ArgumentMatchers.eq(key))(
        ArgumentMatchers.any[HeaderCarrier](),
        ArgumentMatchers.any[Format[T]]()
      )
    ).thenReturn(Future.successful(model))

  def mockSessionCacheSave[T](
      key: String,
      model: T
  ): OngoingStubbing[Future[T]] =
    when(
      mockSessionCacheService.save[T](
        ArgumentMatchers.eq(key),
        ArgumentMatchers.any[T]()
      )(
        ArgumentMatchers.any[HeaderCarrier](),
        ArgumentMatchers.any[Format[T]]()
      )
    ).thenReturn(Future.successful(model))

  def mockSessionCacheClear(): OngoingStubbing[Future[Unit]] =
    when(
      mockSessionCacheService.clear()(
        ArgumentMatchers.any[HeaderCarrier]()
      )
    ).thenReturn(Future.successful(()))

  def mockSessionCacheGetFailed[T](
      key: String,
      exception: Throwable
  ): OngoingStubbing[Future[Option[T]]] =
    when(
      mockSessionCacheService.get[T](ArgumentMatchers.eq(key))(
        ArgumentMatchers.any[HeaderCarrier](),
        ArgumentMatchers.any[Format[T]]()
      )
    ).thenReturn(Future.failed(exception))

  def mockSessionCacheSaveFailed[T](
      key: String,
      exception: Throwable
  ): OngoingStubbing[Future[T]] =
    when(
      mockSessionCacheService.save[T](
        ArgumentMatchers.eq(key),
        ArgumentMatchers.any[T]()
      )(
        ArgumentMatchers.any[HeaderCarrier](),
        ArgumentMatchers.any[Format[T]]()
      )
    ).thenReturn(Future.failed(exception))
}
