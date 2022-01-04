/*
 * Copyright 2022 HM Revenue & Customs
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

import models.{NewAddress, TakeoverDetails}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import services.TakeoverService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

trait TakeoverServiceMock extends MockitoSugar {
  val mockTakeoverService: TakeoverService = mock[TakeoverService]

  def mockGetTakeoverDetails(registrationId: String)(response: Future[Option[TakeoverDetails]]): Unit =
    when(mockTakeoverService.getTakeoverDetails(ArgumentMatchers.eq(registrationId))(ArgumentMatchers.any[HeaderCarrier]))
      .thenReturn(response)

  def mockUpdateReplacingAnotherBusiness(registrationId: String, replacingAnotherBusiness: Boolean)(response: Future[TakeoverDetails]): Unit =
    when(mockTakeoverService.updateReplacingAnotherBusiness(ArgumentMatchers.eq(registrationId), ArgumentMatchers.eq(replacingAnotherBusiness))(ArgumentMatchers.any[HeaderCarrier]))
      .thenReturn(response)

  def mockUpdateBusinessName(registrationId: String, businessName: String)(response: Future[TakeoverDetails]): Unit =
    when(mockTakeoverService.updateBusinessName(ArgumentMatchers.eq(registrationId), ArgumentMatchers.eq(businessName))(ArgumentMatchers.any[HeaderCarrier]))
      .thenReturn(response)

  def mockUpdateBusinessAddress(registrationId: String, businessAddress: NewAddress)(response: Future[TakeoverDetails]): Unit =
    when(mockTakeoverService.updateBusinessAddress(ArgumentMatchers.eq(registrationId), ArgumentMatchers.eq(businessAddress))(ArgumentMatchers.any[HeaderCarrier]))
      .thenReturn(response)

  def mockUpdatePreviousOwnersName(registrationId: String, previousOwnersName: String)(response: Future[TakeoverDetails]): Unit =
    when(mockTakeoverService.updatePreviousOwnersName(ArgumentMatchers.eq(registrationId), ArgumentMatchers.eq(previousOwnersName))(ArgumentMatchers.any[HeaderCarrier]))
      .thenReturn(response)
  
  def mockUpdatePreviousOwnersAddress(registrationId: String, previousOwnersAddress: NewAddress)(response: Future[TakeoverDetails]): Unit =
    when(mockTakeoverService.updatePreviousOwnersAddress(ArgumentMatchers.eq(registrationId), ArgumentMatchers.eq(previousOwnersAddress))(ArgumentMatchers.any[HeaderCarrier]))
      .thenReturn(response)

}
