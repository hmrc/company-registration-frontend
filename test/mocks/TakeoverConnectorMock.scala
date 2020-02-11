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

import connectors.TakeoverConnector
import models.TakeoverDetails
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

trait TakeoverConnectorMock extends MockitoSugar {
  val mockTakeoverConnector: TakeoverConnector = mock[TakeoverConnector]

  def mockGetTakeoverDetails(registrationId: String)(response: Future[Option[TakeoverDetails]]): Unit =
    when(mockTakeoverConnector.getTakeoverDetails(Matchers.eq(registrationId))(Matchers.any[HeaderCarrier]))
      .thenReturn(response)

  def mockUpdateTakeoverDetails(registrationId: String, takeoverDetails: TakeoverDetails)(response: Future[TakeoverDetails]): Unit =
    when(mockTakeoverConnector.updateTakeoverDetails(Matchers.eq(registrationId), Matchers.eq(takeoverDetails))(Matchers.any[HeaderCarrier]))
      .thenReturn(response)
}
