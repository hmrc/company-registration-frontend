/*
 * Copyright 2020 HM Revenue & Customs
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
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future


trait ServiceConnectorMock {
  this: MockitoSugar =>

  val mockServiceConnector = mock[ServiceConnector]

  def getStatusMock(regid: String)(response: StatusResponse, mockConn: ServiceConnector = mockServiceConnector): OngoingStubbing[Future[StatusResponse]] = {
    when(mockConn.getStatus(Matchers.eq(regid))
    (Matchers.any[HeaderCarrier]))
      .thenReturn(Future.successful(response))
  }

  def cancelRegMock(regid: String)(response: CancellationResponse, mockConn: ServiceConnector = mockServiceConnector): OngoingStubbing[Future[CancellationResponse]] = {
    when(mockConn.cancelReg(Matchers.eq(regid))(Matchers.any[String => Future[StatusResponse]]())(Matchers.any[HeaderCarrier]))
      .thenReturn(Future.successful(response))
  }

  def canStatusBeCancelledMock(regid: String)(response: Future[String], mockConn: ServiceConnector = mockServiceConnector)
  : OngoingStubbing[Future[String]] = {
    when(mockConn.canStatusBeCancelled(Matchers.eq(regid))
    (Matchers.any[String => Future[StatusResponse]]())(Matchers.any[HeaderCarrier]))
      .thenReturn(response)
  }
}