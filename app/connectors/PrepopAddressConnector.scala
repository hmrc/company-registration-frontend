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

import config.AppConfig
import models.NewAddress
import play.api.libs.json._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2
import utils.Logging

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PrepopAddressConnector @Inject()(appConfig: AppConfig,
                                       httpClientV2: HttpClientV2
                                      )(implicit executionContext: ExecutionContext) extends Logging {

  def getPrepopAddresses(registrationId: String)(implicit hc: HeaderCarrier): Future[Seq[NewAddress]] = {
    val url = appConfig.prepopAddressUrl(registrationId)

    httpClientV2.get(url"$url").execute[HttpResponse] map { response =>
      (response.json \ "addresses").validate[Seq[NewAddress]] match {
        case JsSuccess(addresses, _) =>
          addresses
        case JsError(errors) =>
          logger.error(s"[getPrepopAddresses] Incoming JSON failed format validation with reason(s): $errors")
          Nil
      }
    } recover {
      case _: NotFoundException =>
        Nil
      case e: ForbiddenException =>
        logger.error(s"[getPrepopAddresses] Forbidden request (${e.responseCode}) ${e.message}")
        Nil
      case e: Exception =>
        logger.error(s"[getPrepopAddresses] Unexpected error calling business registration (${e.getMessage})")
        Nil
    }
  }

  def updatePrepopAddress(registrationId: String, address: NewAddress)(implicit hc: HeaderCarrier): Future[Boolean] = {
    val url = appConfig.prepopAddressUrl(registrationId)
    httpClientV2.post(url"$url")
      .withBody(Json.toJson(address))
      .execute[HttpResponse]
      .map(_ => true)
      .recover {
        case e: Exception =>
          logger.error(s"[updatePrepopAddress] Unexpected error updating prepop address (${e.getMessage})")
          false
      }
  }

}




