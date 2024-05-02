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

import org.mockito.Mockito._
import org.mockito.{ArgumentMatchers}
import org.scalatestplus.mockito.MockitoSugar
import services._
import uk.gov.hmrc.http.cache.client.CacheMap

import scala.concurrent.Future

trait HandOffServiceMock {
  this: MockitoSugar =>

  lazy val mockHandOffService = mock[HandOffService]

  object HandOffServiceMocks {
    def cacheRegistrationID(cacheMap: CacheMap) = {
      when(mockHandOffService.cacheRegistrationID(ArgumentMatchers.anyString())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(cacheMap))
    }
  }

}
