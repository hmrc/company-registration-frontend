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

package controllers

import javax.inject.Inject
import play.api.Configuration
import play.api.i18n.{Lang, MessagesApi}
import play.api.mvc.{Call, ControllerComponents}
import uk.gov.hmrc.play.language.{LanguageController, LanguageUtils}


class CRLanguageController @Inject()(implicit override val messagesApi: MessagesApi,
                                     configuration: Configuration,
                                     languageUtils: LanguageUtils,
                                     val cc: ControllerComponents
                                    )
  extends LanguageController(configuration, languageUtils, cc) {

  /** Converts a string to a URL, using the route to this controller. **/
  def langToCall(lang: String): Call = controllers.routes.CRLanguageController.switchToLanguage(lang)

  /** Provides a fallback URL if there is no referer in the request header. **/
  override protected def fallbackURL: String = configuration.getString(s"language.fallbackUrl").getOrElse("/")

  /** Returns a mapping between strings and the corresponding Lang object. **/
  override def languageMap: Map[String, Lang] = Map(
    "english" -> Lang("en"),
    "cymraeg" -> Lang("cy")
  )
}