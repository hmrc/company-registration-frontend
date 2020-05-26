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

package services

import config.FrontendAppConfig
import connectors.AddressLookupConnector
import javax.inject.{Inject, Singleton}
import models._
import play.api.i18n.MessagesApi
import play.api.libs.json._
import play.api.mvc.Call
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future
import scala.util.control.NoStackTrace

case class QueryStringMissingException() extends NoStackTrace

@Singleton
class AddressLookupFrontendService @Inject()(addressLookupFrontendConnector: AddressLookupConnector,
                                             frontendAppConfig: FrontendAppConfig,
                                             addressLookupConfigBuilderService: AddressLookupConfigBuilderService,
                                             messagesApi: MessagesApi) {

  lazy val addressLookupFrontendURL: String = frontendAppConfig.baseUrl("address-lookup-frontend")
  lazy val companyRegistrationFrontendURL: String = frontendAppConfig.self
  lazy val timeoutInSeconds: Int = frontendAppConfig.timeoutInSeconds.toInt

  def initialiseAlfJourney(handbackLocation: Call, specificJourneyKey: String, lookupPageHeading: String, confirmPageHeading: String)(implicit hc: HeaderCarrier): Future[String] = {
    val config = addressLookupConfigBuilderService.buildConfig(
      handbackLocation = handbackLocation,
      specificJourneyKey = specificJourneyKey,
      lookupPageHeading = lookupPageHeading,
      confirmPageHeading = confirmPageHeading
    )(messagesApi)

    addressLookupFrontendConnector.getOnRampURL(config)
  }

  def getAddress(id: String)(implicit hc: HeaderCarrier): Future[NewAddress] = addressLookupFrontendConnector.getAddress(id)

}
