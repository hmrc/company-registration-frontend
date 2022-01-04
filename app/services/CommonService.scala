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

package services

import java.time.LocalDate

import connectors.KeystoreConnector
import play.api.Logger
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.CacheMap
import utils.SCRSExceptions

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait CommonService {
  self : SCRSExceptions =>

  val keystoreConnector: KeystoreConnector

  def fetchRegistrationID(implicit hc: HeaderCarrier): Future[String] = {
    keystoreConnector.fetchAndGet[String]("registrationID").map {
      case Some(regID) => regID
      case None =>
        Logger.error(s"[CommonService] [fetchRegistrationID] - Could not find a registration ID in keystore")
        throw RegistrationIDNotFoundException
    }
  }

  def cacheRegistrationID(registrationID: String)(implicit hc: HeaderCarrier): Future[CacheMap] = {
    keystoreConnector.cache("registrationID", registrationID)
  }

  def updateLastActionTimestamp()(implicit hc: HeaderCarrier): Future[CacheMap] = {
    keystoreConnector.cache("lastActionTimestamp", LocalDate.now)
  }

}