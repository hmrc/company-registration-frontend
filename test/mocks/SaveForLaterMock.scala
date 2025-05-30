/*
 * Copyright 2023 HM Revenue & Customs
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

import connectors._
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.mockito.stubbing.OngoingStubbing
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Format
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

trait SaveForLaterMock {
    this: MockitoSugar =>

    lazy val mockS4LConnector = mock[S4LConnector]

    def mockS4LFetchAndGet[T](formId: String, model: Option[T], mockS4LConnector: S4LConnector = mockS4LConnector): OngoingStubbing[Future[Option[T]]] = {
      when(mockS4LConnector.fetchAndGet[T](ArgumentMatchers.anyString(), ArgumentMatchers.contains(formId))(ArgumentMatchers.any[HeaderCarrier](),
        ArgumentMatchers.any[ExecutionContext](), ArgumentMatchers.any[Format[T]]()))
        .thenReturn(Future.successful(model))
    }

    def mockS4LClear(mockS4LConnector: S4LConnector = mockS4LConnector) : OngoingStubbing[Future[Unit]] = {
      when(mockS4LConnector.clear(ArgumentMatchers.anyString())(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[ExecutionContext]()))
        .thenReturn(Future.successful())
    }

    def mockS4LSaveForm[T](formId: String, cacheMap: CacheMap, mockS4LConnector: S4LConnector = mockS4LConnector) : OngoingStubbing[Future[CacheMap]] = {
      when(mockS4LConnector.saveForm[T](ArgumentMatchers.anyString(), ArgumentMatchers.contains(formId), ArgumentMatchers.any[T]())(
        ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[ExecutionContext](), ArgumentMatchers.any[Format[T]]()))
        .thenReturn(Future.successful(cacheMap))
    }
}
