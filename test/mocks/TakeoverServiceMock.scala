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

import models.TakeoverDetails
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import services.TakeoverService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

trait TakeoverServiceMock extends MockitoSugar {
  val mockTakeoverService: TakeoverService = mock[TakeoverService]

  def mockGetTakeoverDetails(registrationId: String)(response: Future[Option[TakeoverDetails]]): Unit =
    when(mockTakeoverService.getTakeoverDetails(Matchers.eq(registrationId))(Matchers.any[HeaderCarrier]))
      .thenReturn(response)

  def mockUpdateReplacingAnotherBusiness(registrationId: String, replacingAnotherBusiness: Boolean)(response: Future[TakeoverDetails]): Unit =
    when(mockTakeoverService.updateReplacingAnotherBusiness(Matchers.eq(registrationId), Matchers.eq(replacingAnotherBusiness))(Matchers.any[HeaderCarrier]))
      .thenReturn(response)

  def mockUpdateBusinessName(registrationId: String, businessName: String)(response: Future[TakeoverDetails]): Unit =
    when(mockTakeoverService.updateBusinessName(Matchers.eq(registrationId), Matchers.eq(businessName))(Matchers.any[HeaderCarrier]))
      .thenReturn(response)

}
