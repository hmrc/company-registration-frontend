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

import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import models.{TradingDetails, TradingDetailsResponse}
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.http.HeaderCarrier
import utils.SCRSExceptions

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object TradingDetailsService extends TradingDetailsService {
  val keystoreConnector = KeystoreConnector
  val compRegConnector = CompanyRegistrationConnector
}

trait TradingDetailsService extends CommonService with SCRSExceptions {

  val compRegConnector: CompanyRegistrationConnector

  def updateCompanyInformation(tradingDetails : TradingDetails)(implicit hc: HeaderCarrier, user: AuthContext) : Future[TradingDetailsResponse] = {
    for {
      regID <- fetchRegistrationID
      tD <- compRegConnector.updateTradingDetails(regID, tradingDetails)
    } yield {
      tD
    }
  }

  def retrieveTradingDetails(registrationID : String)(implicit hc: HeaderCarrier, user : AuthContext) : Future[TradingDetails] = {
    compRegConnector.retrieveTradingDetails(registrationID).map(_.fold(TradingDetails())(t => t))
  }
}
