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

import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.mockito.stubbing.OngoingStubbing
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}

import java.net.URL
import scala.concurrent.Future

trait WSHTTPMock {
  this: MockitoSugar =>

   lazy val mockHttpClientV2: HttpClientV2 = mock[HttpClientV2]
   lazy val mockRequestBuilder: RequestBuilder = mock[RequestBuilder]

  def mockHttpGET[T](thenReturn: T): OngoingStubbing[Future[T]] = {
    when(mockHttpClientV2.get(ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(mockRequestBuilder)
    when(mockRequestBuilder.execute[T](ArgumentMatchers.any(),ArgumentMatchers.any()))
      .thenReturn(Future.successful(thenReturn))
  }

  def mockHttpGET[T](url: URL, thenReturn: Future[T]): OngoingStubbing[Future[T]] = {
    when(mockHttpClientV2.get(ArgumentMatchers.eq(url))(ArgumentMatchers.any())).thenReturn(mockRequestBuilder)
    when(mockRequestBuilder.execute[T](ArgumentMatchers.any(),ArgumentMatchers.any()))
      .thenReturn(thenReturn)
  }

  def mockHttpFailedGET[T](exception: Exception): OngoingStubbing[Future[T]] = {
    when(mockHttpClientV2.get(ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(mockRequestBuilder)
    when(mockRequestBuilder.execute[T](ArgumentMatchers.any(),ArgumentMatchers.any()))
      .thenReturn(Future.failed(exception))
  }

  def mockHttpPOST[I, O](thenReturn: O): OngoingStubbing[Future[O]] = {
    when(mockHttpClientV2.post(ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(mockRequestBuilder)
    when(mockRequestBuilder.withBody(ArgumentMatchers.any[I]())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(mockRequestBuilder)
    when(mockRequestBuilder.execute[O](ArgumentMatchers.any(),ArgumentMatchers.any()))
      .thenReturn(Future.successful(thenReturn))
  }

  def mockHttpPOST[I, O](url: URL,thenReturn: O): OngoingStubbing[Future[O]] = {
    when(mockHttpClientV2.post(ArgumentMatchers.eq(url))(ArgumentMatchers.any())).thenReturn(mockRequestBuilder)
    when(mockRequestBuilder.withBody(ArgumentMatchers.any[I]())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(mockRequestBuilder)
    when(mockRequestBuilder.execute[O](ArgumentMatchers.any(),ArgumentMatchers.any()))
      .thenReturn(Future.successful(thenReturn))
  }

  def mockHttpFailedPOST[I, O](exception: Exception): OngoingStubbing[Future[O]] = {
    when(mockHttpClientV2.post(ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(mockRequestBuilder)
    when(mockRequestBuilder.withBody(ArgumentMatchers.any[I]())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(mockRequestBuilder)
    when(mockRequestBuilder.execute[O](ArgumentMatchers.any(),ArgumentMatchers.any()))
      .thenReturn(Future.failed(exception))
  }

  def mockHttpPUT[I, O](thenReturn: O): OngoingStubbing[Future[O]] = {
    when(mockHttpClientV2.put(ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(mockRequestBuilder)
    when(mockRequestBuilder.withBody(ArgumentMatchers.any[I]())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(mockRequestBuilder)
    when(mockRequestBuilder.execute[O](ArgumentMatchers.any(),ArgumentMatchers.any()))
      .thenReturn(Future.successful(thenReturn))
  }

  def mockHttpFailedPUT[I, O](exception: Exception): OngoingStubbing[Future[O]] = {
    when(mockHttpClientV2.put(ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(mockRequestBuilder)
    when(mockRequestBuilder.withBody(ArgumentMatchers.any[I]())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(mockRequestBuilder)
    when(mockRequestBuilder.execute[O](ArgumentMatchers.any(),ArgumentMatchers.any()))
      .thenReturn(Future.failed(exception))
  }

  def mockHttpDELETE[T](thenReturn:T): OngoingStubbing[Future[T]] = {
    when(mockHttpClientV2.delete(ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(mockRequestBuilder)
    when(mockRequestBuilder.execute[T](ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(thenReturn))
  }

  def mockHttpFailedDELETE[T](exception: Exception): OngoingStubbing[Future[T]] = {
    when(mockHttpClientV2.delete(ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(mockRequestBuilder)
    when(mockRequestBuilder.execute[T](ArgumentMatchers.any(), ArgumentMatchers.any()))
      .thenReturn(Future.failed(exception))
  }
}
