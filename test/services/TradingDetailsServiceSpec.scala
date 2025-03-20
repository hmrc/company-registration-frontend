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

package services

import java.util.UUID
import fixtures.TradingDetailsFixtures
import helpers.SCRSSpec
import mocks.SCRSMocks
import models.TradingDetails
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import play.api.libs.json.Format
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class TradingDetailsServiceSpec extends SCRSSpec with SCRSMocks with TradingDetailsFixtures {

  lazy val regID = UUID.randomUUID.toString

  class Setup {
    object TestService extends TradingDetailsService {
      val keystoreConnector = mockKeystoreConnector
      val compRegConnector = mockCompanyRegistrationConnector
    }
  }

  "Updating the trading details data for a given user" should {
    "return a TradingDetailsSuccessResponse" in new Setup {
      when(mockKeystoreConnector.fetchAndGet[String](ArgumentMatchers.eq("registrationID"))(ArgumentMatchers.any[HeaderCarrier](),
        ArgumentMatchers.any[ExecutionContext](), ArgumentMatchers.any[Format[String]]()))
        .thenReturn(Future.successful(Some(regID)))

      when(mockCommonService.fetchRegistrationID(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[ExecutionContext]())).thenReturn(Future.successful(regID))

      when(mockCompanyRegistrationConnector.updateTradingDetails(ArgumentMatchers.eq(regID),
        ArgumentMatchers.eq(tradingDetailsTrue))(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[ExecutionContext]()))
        .thenReturn(Future.successful(tradingDetailsSuccessResponseTrue))

      val result = TestService.updateCompanyInformation(tradingDetailsTrue)
      val returned = await(result)

      returned mustBe tradingDetailsSuccessResponseTrue
    }
  }

  "Retrieving the trading details data for a given user" should {
    "return a TradingDetails model" in new Setup {

      mockHttpGET[Option[TradingDetails]](Some(tradingDetailsTrue))

      when(mockCompanyRegistrationConnector.retrieveTradingDetails(ArgumentMatchers.eq(regID))(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[ExecutionContext]()))
        .thenReturn(Future.successful(Some(tradingDetailsTrue)))

      val result = TestService.retrieveTradingDetails(regID)

      val returned = await(result)

      returned mustBe tradingDetailsTrue
    }

    "return an 'empty' TradingDetailsModel" in new Setup {

      mockHttpGET[Option[TradingDetails]](None)

      when(mockCompanyRegistrationConnector.retrieveTradingDetails(ArgumentMatchers.eq(regID))(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[ExecutionContext]()))
        .thenReturn(Future.successful(None))

      val result = TestService.retrieveTradingDetails(regID)

      val returned = await(result)

      returned mustBe TradingDetails()
    }
  }
}
