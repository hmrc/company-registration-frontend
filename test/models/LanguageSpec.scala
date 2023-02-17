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

package models

import config.LangConstants
import helpers.SCRSSpec
import play.api.i18n.Lang
import play.api.libs.json.Json

class LanguageSpec extends SCRSSpec {

  val language = Language(LangConstants.english)
  val languageJson = Json.obj("code" -> LangConstants.english)

  "Language" must {

    "serialize to JSON correctly" in {
      Json.toJson(language) mustBe languageJson
    }

    "deserialize from JSON correctly" in {
      languageJson.as[Language] mustBe language
    }

    "be able to be constructed from a Lang instance" in {
      Language(Lang(LangConstants.english)) mustBe language
    }
  }
}
