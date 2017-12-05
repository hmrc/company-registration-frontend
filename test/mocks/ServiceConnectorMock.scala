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

package mocks

import connectors._
import org.mockito.Matchers
import org.mockito.Mockito.when
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future


trait ServiceConnectorMock {
  this: MockitoSugar =>

  val mockServiceConnector = mock[ServiceConnector]

  def getStatusMock(response: StatusResponse) : OngoingStubbing[Future[StatusResponse]] = {
    when(mockServiceConnector.getStatus(Matchers.any[String])
    (Matchers.any[HeaderCarrier]))
      .thenReturn(Future.successful(response))
  }
  def cancelRegMock(response:CancellationResponse): OngoingStubbing[Future[CancellationResponse]] = {
    when(mockServiceConnector.cancelReg(Matchers.any[String])(Matchers.any[Function1[String, Future[StatusResponse]]]())(Matchers.any[HeaderCarrier])).thenReturn(Future.successful(response))
  }
  def canStatusBeCancelledMock(response:Future[String])
  :OngoingStubbing[Future[String]] ={
    when(mockServiceConnector.canStatusBeCancelled(Matchers.any[String])
    (Matchers.any[Function1[String, Future[StatusResponse]]]())(Matchers.any[HeaderCarrier])).thenReturn(response)
  }

}
