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

import connectors.AddressLookupConnector
import models.AlfJourneyConfig
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import org.mockito.stubbing.OngoingStubbing
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

trait AddressLookupConnectorMock extends MockitoSugar {

  val mockAddressLookupConnector: AddressLookupConnector = mock[AddressLookupConnector]

  def mockGetOnRampUrl(alfJourneyConfig: AlfJourneyConfig)(response: Future[String]): OngoingStubbing[Future[String]] =
    when(mockAddressLookupConnector.getOnRampURL(ArgumentMatchers.eq(alfJourneyConfig))(ArgumentMatchers.any[HeaderCarrier]()))
      .thenReturn(response)

}
