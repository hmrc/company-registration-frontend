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

package services

import config.LangConstants
import helpers.SCRSSpec
import models.Language
import org.mockito.ArgumentMatchers.{eq => eqTo}
import org.mockito.Mockito.when
import play.api.i18n.Lang

import scala.concurrent.{ExecutionContext, Future}


class LanguageServiceSpec extends SCRSSpec {

  val regId = "reg12345"
  val lang = Lang(LangConstants.english)

  implicit val ec = ExecutionContext.Implicits.global

  object TestLanguageService extends LanguageService(mockCompanyRegistrationConnector)

  "LanguageService" when {

    "calling .updateLanguage(regId: String, lang: Lang)" must {

      "return true when call to update Language is successful" in {

        when(mockCompanyRegistrationConnector.updateLanguage(eqTo(regId), eqTo(Language(lang)))(eqTo(hc), eqTo(ec)))
          .thenReturn(Future.successful(true))

        await(TestLanguageService.updateLanguage(regId, lang)) mustBe true
      }

      "throw exception if Future unexpectedly fails" in { //note, this should never happen as a recover block exists within the connector

        when(mockCompanyRegistrationConnector.updateLanguage(eqTo(regId), eqTo(Language(lang)))(eqTo(hc), eqTo(ec)))
          .thenReturn(Future.failed(new Exception("bang")))

        intercept[Exception](await(TestLanguageService.updateLanguage(regId, lang))).getMessage mustBe "bang"
      }
    }
  }
}
