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

import models.AlfJourneyConfig
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.mockito.stubbing.OngoingStubbing
import org.scalatestplus.mockito.MockitoSugar
import play.api.i18n.{MessagesApi, MessagesProvider}
import play.api.mvc.Call
import services.AddressLookupConfigBuilderService

trait AddressLookupConfigBuilderServiceMock extends MockitoSugar {

  val mockAddressLookupConfigBuilderService: AddressLookupConfigBuilderService = mock[AddressLookupConfigBuilderService]

  def mockBuildLegacyConfig(handbackLocation: Call,
                            specificJourneyKey: String,
                            lookupPageHeading: String,
                            confirmPageHeading: String
                           )(response: AlfJourneyConfig): OngoingStubbing[AlfJourneyConfig] =
    when(
      mockAddressLookupConfigBuilderService.buildConfig(
        ArgumentMatchers.eq(handbackLocation),
        ArgumentMatchers.eq(specificJourneyKey),
        ArgumentMatchers.eq(lookupPageHeading),
        ArgumentMatchers.eq(confirmPageHeading)
      )(ArgumentMatchers.any[MessagesApi], ArgumentMatchers.any[MessagesProvider])
    ) thenReturn response

}
