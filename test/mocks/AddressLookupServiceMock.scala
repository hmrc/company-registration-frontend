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

import org.mockito.Matchers
import org.mockito.Mockito._
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.mockito.MockitoSugar
import services._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

trait AddressLookupServiceMock {
    this: MockitoSugar =>

    lazy val mockAddressLookupService = mock[AddressLookupService]

    def mockAddressLookup(postcode: String, response: AddressLookupResponse): OngoingStubbing[Future[AddressLookupResponse]] = {
      when(mockAddressLookupService.lookup(Matchers.contains(postcode), Matchers.any[Option[String]]())(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(response))
    }
  }
