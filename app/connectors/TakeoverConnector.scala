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

package connectors

import config.{AppConfig, WSHttp}
import connectors.httpParsers.AddressLookupHttpParsers.rawReads

import javax.inject.Inject
import models.TakeoverDetails
import utils.Logging
import play.api.http.Status._
import play.api.libs.json.{JsError, JsSuccess}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

class TakeoverConnector @Inject()(appConfig: AppConfig,
                                  http: WSHttp)(implicit ec: ExecutionContext) extends Logging {
  def takeOverDetailsUrl(registrationId: String) = s"${appConfig.companyRegistrationUrl}/company-registration/corporation-tax-registration/$registrationId/takeover-details"

  def getTakeoverDetails(registrationId: String)(implicit hc: HeaderCarrier): Future[Option[TakeoverDetails]] = {
    http.GET[HttpResponse](takeOverDetailsUrl(registrationId)).map {
      res =>
        res.status match {
          case OK =>
            res.json.validate[TakeoverDetails] match {
              case JsSuccess(takeOverDetails, _) =>
                Some(takeOverDetails)
              case JsError(errors) =>
                logger.error(s"[getTakeoverDetails] could not parse takeover details json to Takeover Details $registrationId for keys ${errors.map(_._1)}")
                None
            }
          case _ =>
            None
        }
    }
  }


  def updateTakeoverDetails(registrationId: String, takeoverDetails: TakeoverDetails)(implicit hc: HeaderCarrier): Future[TakeoverDetails] = {
    http.PUT[TakeoverDetails, HttpResponse](takeOverDetailsUrl(registrationId), takeoverDetails)(implicitly, rawReads, hc, ec).map {
      res =>
        res.json.validate[TakeoverDetails] match {
          case JsSuccess(takeOverDetails, _) =>
            takeOverDetails
          case JsError(errors) =>
            logger.error(s"[updateTakeoverDetails] could not parse takeover details json to Takeover Details $registrationId for keys ${errors.map(_._1)}")
            throw new Exception("Update returned invalid json")
        }
    }
  }

}
