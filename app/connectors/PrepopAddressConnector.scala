/*
 * Copyright 2021 HM Revenue & Customs
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

import config.{FrontendAppConfig, WSHttp}
import javax.inject.{Inject, Singleton}
import models.NewAddress
import play.api.Logger
import play.api.libs.json._
import uk.gov.hmrc.http._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PrepopAddressConnector @Inject()(appConfig: FrontendAppConfig,
                                       http: WSHttp
                                      )(implicit executionContext: ExecutionContext) {

  def getPrepopAddresses(registrationId: String)(implicit hc: HeaderCarrier): Future[Seq[NewAddress]] =
    http.GET[HttpResponse](appConfig.prepopAddressUrl(registrationId)) map { response =>
      (response.json \ "addresses").validate[Seq[NewAddress]] match {
        case JsSuccess(addresses, _) =>
          addresses
        case JsError(errors) =>
          Logger.error(s"[Get prepop addresses] Incoming JSON failed format validation with reason(s): $errors")
          Nil
      }
    } recover {
      case _: NotFoundException =>
        Nil
      case e: ForbiddenException =>
        Logger.error(s"[Get prepop address] Forbidden request (${e.responseCode}) ${e.message}")
        Nil
      case e: Exception =>
        Logger.error(s"[Get prepop address] Unexpected error calling business registration (${e.getMessage})")
        Nil
    }

  def updatePrepopAddress(registrationId: String, address: NewAddress)(implicit hc: HeaderCarrier): Future[Boolean] =
    http.POST[JsValue, HttpResponse](appConfig.prepopAddressUrl(registrationId), Json.toJson(address))
      .map (_ => true)
      .recover {
        case e: Exception =>
          Logger.error(s"[Update prepop address] Unexpected error updating prepop address (${e.getMessage})")
          false
      }

}




