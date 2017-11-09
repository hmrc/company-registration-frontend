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

package services

import java.util.UUID

import builders.AuthBuilder
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import fixtures.TradingDetailsFixtures
import helpers.SCRSSpec
import mocks.SCRSMocks
import models.TradingDetails
import org.mockito.Mockito._
import org.mockito.Matchers
import play.api.libs.json.Format

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

class TradingDetailsServiceSpec extends SCRSSpec with SCRSMocks with TradingDetailsFixtures with AuthBuilder {

  val mockKeyStoreConnector = mock[KeystoreConnector]
  val mockCompRegConnector = mock[CompanyRegistrationConnector]
  val mockCommonService = mock[CommonService]

  lazy val regID = UUID.randomUUID.toString

  implicit val user = createTestUser

  class Setup {
    object TestService extends TradingDetailsService {
      val keystoreConnector = mockKeyStoreConnector
      val compRegConnector = mockCompRegConnector
    }
  }

  "Updating the trading details data for a given user" should {
    "return a TradingDetailsSuccessResponse" in new Setup {
      when(mockKeyStoreConnector.fetchAndGet[String](Matchers.eq("registrationID"))(Matchers.any[HeaderCarrier](), Matchers.any[Format[String]]()))
        .thenReturn(Future.successful(Some(regID)))

      when(mockCommonService.fetchRegistrationID(Matchers.any[HeaderCarrier]())).thenReturn(Future.successful(regID))

      when(mockCompRegConnector.updateTradingDetails(Matchers.eq(regID), Matchers.eq(tradingDetailsTrue))(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(tradingDetailsSuccessResponseTrue))

      val result = TestService.updateCompanyInformation(tradingDetailsTrue)
      val returned = await(result)

      returned shouldBe tradingDetailsSuccessResponseTrue
    }
  }

  "Retrieving the trading details data for a given user" should {
    "return a TradingDetails model" in new Setup {

      mockHttpGet[Option[TradingDetails]]("testUrl", Some(tradingDetailsTrue))

      when(mockCompRegConnector.retrieveTradingDetails(Matchers.eq(regID))(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(tradingDetailsTrue)))

      val result = TestService.retrieveTradingDetails(regID)

      val returned = await(result)

      returned shouldBe tradingDetailsTrue
    }

    "return an 'empty' TradingDetailsModel" in new Setup {

      mockHttpGet[Option[TradingDetails]]("testUrl", None)

      when(mockCompRegConnector.retrieveTradingDetails(Matchers.eq(regID))(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      val result = TestService.retrieveTradingDetails(regID)

      val returned = await(result)

      returned shouldBe TradingDetails()
    }
  }
}
