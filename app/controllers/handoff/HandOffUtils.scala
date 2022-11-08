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

package controllers.handoff

import com.google.inject.Inject
import config.LangConstants
import play.api.i18n.Lang
import play.api.libs.json.{JsValue, Reads, __}
import play.api.mvc.{AnyContent, MessagesRequest}
import uk.gov.hmrc.play.language.LanguageUtils

class HandOffUtils @Inject() (languageUtils: LanguageUtils) {

  private val welshCookie = Lang(LangConstants.welsh)

  def getCurrentLang(implicit request: MessagesRequest[AnyContent]): String = {
    if (languageUtils.getCurrentLang == welshCookie) {
      LangConstants.welsh
    } else {
      LangConstants.english
    }
  }

  def readLang(payload: JsValue)(implicit request: MessagesRequest[AnyContent]): Lang = {
    payload.as[Option[String]](Reads.nullable(__ \ "language")).fold(Lang(getCurrentLang(request)))(Lang(_))
  }
}
