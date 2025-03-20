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

package controllers.test

import config.AppConfig

import javax.inject.Inject
import play.api.libs.json.JsValue
import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.ExecutionContext

class ModifyThrottledUsersControllerImpl @Inject()(val appConfig: AppConfig,
                                                   val httpClientV2: HttpClientV2,
                                                   mcc: MessagesControllerComponents) extends ModifyThrottledUsersController(mcc) {
  lazy val crUrl = appConfig.servicesConfig.baseUrl("company-registration")
}

abstract class ModifyThrottledUsersController(mcc: MessagesControllerComponents) extends FrontendController(mcc) {

  implicit val ec: ExecutionContext = mcc.executionContext
  val httpClientV2: HttpClientV2
  val crUrl: String

  def modifyThrottledUsers(usersIn: Int) = Action.async {
    implicit request =>
      httpClientV2
        .get(url"$crUrl/company-registration/test-only/modify-throttled-users/$usersIn")
        .execute[JsValue]
        .map { res =>
          val usersIn = (res \ "users_in").as[Int]
          Ok(s"users_in set to $usersIn")
        }
  }
}