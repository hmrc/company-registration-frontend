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

import connectors.TakeoverConnector
import javax.inject.Inject
import models.{NewAddress, TakeoverDetails}
import uk.gov.hmrc.http.{HeaderCarrier, InternalServerException}

import scala.concurrent.{ExecutionContext, Future}

class TakeoverService @Inject()(takeoverConnector: TakeoverConnector)(implicit ec: ExecutionContext) {
  def getTakeoverDetails(registrationId: String)(implicit hc: HeaderCarrier): Future[Option[TakeoverDetails]] =
    takeoverConnector.getTakeoverDetails(registrationId)

  def updateReplacingAnotherBusiness(registrationId: String, replacingAnotherBusiness: Boolean)(implicit hc: HeaderCarrier): Future[TakeoverDetails] =
    if (replacingAnotherBusiness) {
      takeoverConnector.getTakeoverDetails(registrationId).flatMap {
        case None =>
          takeoverConnector.updateTakeoverDetails(registrationId, TakeoverDetails(replacingAnotherBusiness))
        case Some(takeoverDetails) if takeoverDetails.replacingAnotherBusiness == replacingAnotherBusiness =>
          Future.successful(takeoverDetails)
        case Some(takeoverDetails) =>
          takeoverConnector.updateTakeoverDetails(registrationId, takeoverDetails.copy(replacingAnotherBusiness = replacingAnotherBusiness))
      }
    } else {
      takeoverConnector.updateTakeoverDetails(registrationId, TakeoverDetails(replacingAnotherBusiness))
    }

  def updateBusinessName(registrationId: String, businessName: String)(implicit hc: HeaderCarrier): Future[TakeoverDetails] =
    takeoverConnector.getTakeoverDetails(registrationId).flatMap {
      case Some(TakeoverDetails(false, _, _, _, _)) =>
        Future.failed(new InternalServerException("Reached takeover business page in a no takeover flow"))
      case None =>
        Future.failed(new InternalServerException("Could not retrieve takeover details when trying to update Business name"))
      case Some(takeoverDetails) =>
        takeoverConnector.updateTakeoverDetails(registrationId, takeoverDetails.copy(businessName = Some(businessName)))
    }

  def updateBusinessAddress(registrationId: String, address: NewAddress)(implicit hc: HeaderCarrier): Future[TakeoverDetails] =
    takeoverConnector.getTakeoverDetails(registrationId).flatMap {
      case Some(TakeoverDetails(false, _, _, _, _)) =>
        Future.failed(new InternalServerException("Reached takeover business page in a no takeover flow"))
      case None =>
        Future.failed(new InternalServerException("Could not retrieve takeover details when trying to update Business Address"))
      case Some(TakeoverDetails(true, None, _, _, _)) =>
        Future.failed(new InternalServerException("Reached takeover Business Address without a business name"))
      case Some(takeoverDetails) =>
        takeoverConnector.updateTakeoverDetails(registrationId, takeoverDetails.copy(businessTakeoverAddress = Some(address)))
    }

  def updatePreviousOwnersName(registrationId: String, name: String)(implicit hc: HeaderCarrier): Future[TakeoverDetails] =
    takeoverConnector.getTakeoverDetails(registrationId).flatMap {
      case Some(TakeoverDetails(false, _, _, _, _)) =>
        Future.failed(new InternalServerException("Reached takeover business page in a no takeover flow"))
      case None =>
        Future.failed(new InternalServerException("Could not retrieve takeover details when trying to update Previous Owners Name"))
      case Some(TakeoverDetails(true, None, _, _, _)) =>
        Future.failed(new InternalServerException("Reached Who Agreed Takeover without a business name"))
      case Some(TakeoverDetails(true, _, None, _, _)) =>
        Future.failed(new InternalServerException("Reached Who Agreed Takeover without a business address"))
      case Some(takeoverDetails) =>
        takeoverConnector.updateTakeoverDetails(registrationId, takeoverDetails.copy(previousOwnersName = Some(name)))
    }
}
