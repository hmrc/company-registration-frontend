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

import connectors.{CompanyRegistrationConnector, PrepopAddressConnector}
import javax.inject.{Inject, Singleton}
import models.NewAddress
import uk.gov.hmrc.http.{HeaderCarrier, InternalServerException}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AddressPrepopulationService @Inject()(companyRegistrationConnector: CompanyRegistrationConnector,
                                            prepopAddressConnector: PrepopAddressConnector)(implicit ec: ExecutionContext) {
  def retrieveAddresses(registrationId: String)(implicit hc: HeaderCarrier): Future[Seq[NewAddress]] = for {
    optCompanyDetails <- companyRegistrationConnector.retrieveCompanyDetails(registrationId)
    optFormattedRoAddress <- optCompanyDetails match {
      case Some(companyDetails) => companyRegistrationConnector.validateRegisteredOfficeAddress(registrationId, companyDetails.cHROAddress)
      case None => Future.failed(new InternalServerException("[AddressPrepopulationService] Cannot retrieve addresses, there are no stored company details"))
    }
    preEnteredAddresses <- prepopAddressConnector.getPrepopAddresses(registrationId)
  } yield preEnteredAddresses ++ optFormattedRoAddress
}
