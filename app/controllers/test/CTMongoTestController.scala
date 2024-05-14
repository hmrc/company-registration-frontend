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

import config.{AppConfig, WSHttp}
import javax.inject.Inject
import play.api.libs.json.JsValue
import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.http.{HeaderCarrier, HttpGet}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.{ExecutionContext, Future}


class CTMongoTestControllerImpl @Inject()(val wSHttp: WSHttp,
                                          val appConfig: AppConfig,
                                          mcc: MessagesControllerComponents)(implicit override val ec: ExecutionContext) extends CTMongoTestController(mcc)(ec) {

  lazy val ctUrl = appConfig.servicesConfig.baseUrl("company-registration")
}

abstract class CTMongoTestController(mcc: MessagesControllerComponents)(implicit val ec: ExecutionContext) extends FrontendController(mcc) {

  val wSHttp: HttpGet
  val ctUrl: String

  def dropCollection = Action.async {
    implicit request =>
      for {
        ct <- dropCTCollection
        ctMessage = (ct \ "message").as[String]
      } yield {
        Ok(s"$ctMessage")
      }
  }

  def dropCTCollection(implicit hc: HeaderCarrier): Future[JsValue] = {
    wSHttp.GET[JsValue](s"$ctUrl/company-registration/test-only/drop-ct")(readJsValue, hc, ec)
  }
}