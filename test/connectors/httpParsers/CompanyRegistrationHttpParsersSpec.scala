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

package connectors.httpParsers

import ch.qos.logback.classic.Level
import config.LangConstants
import helpers.SCRSSpec
import models.{Language, LanguageSpec}
import play.api.http.Status.{INTERNAL_SERVER_ERROR, NOT_FOUND, NO_CONTENT}
import uk.gov.hmrc.http.HttpResponse
import utils.LogCapturingHelper

class CompanyRegistrationHttpParsersSpec extends SCRSSpec with LogCapturingHelper {

  val regId = "reg12345"
  val language = Language(LangConstants.english)

  object TestCompanyRegistrationHttpParsers extends CompanyRegistrationHttpParsers

  "CompanyRegistrationHttpParsers" when {

    "calling .updateLanguageHttpReads(regId: String, language: Language)" when {

      val rds = TestCompanyRegistrationHttpParsers.updateLanguageHttpReads(regId, language)

      s"HttpResponse is NO_CONTENT ($NO_CONTENT)" must {

        "return true (and log debug message)" in {

          withCaptureOfLoggingFrom(TestCompanyRegistrationHttpParsers.logger) { logs =>
            rds.read("PUT", "/language", HttpResponse(NO_CONTENT, "")) mustBe true
            logs.containsMsg(Level.DEBUG, s"[TestCompanyRegistrationHttpParsers][updateLanguageHttpReads] Updated language to: '${language.code}' for regId: '$regId'")
          }
        }
      }

      s"HttpResponse is NOT_FOUND ($NOT_FOUND)" must {

        "return false (and log warn message)" in {

          withCaptureOfLoggingFrom(TestCompanyRegistrationHttpParsers.logger) { logs =>
            rds.read("PUT", "/language", HttpResponse(NOT_FOUND, "")) mustBe false
            logs.containsMsg(Level.WARN, s"[TestCompanyRegistrationHttpParsers][updateLanguageHttpReads] No document was found ($NOT_FOUND) when attempting to update language to: '${language.code}' for regId: '$regId'")
          }
        }
      }

      s"HttpResponse is any other unexpected status e.g. INTERNAL_SERVER_ERROR ($INTERNAL_SERVER_ERROR)" must {

        "return false (and log error message)" in {

          withCaptureOfLoggingFrom(TestCompanyRegistrationHttpParsers.logger) { logs =>
            rds.read("PUT", "/language", HttpResponse(INTERNAL_SERVER_ERROR, "")) mustBe false
            logs.containsMsg(Level.ERROR, s"[TestCompanyRegistrationHttpParsers][updateLanguageHttpReads] An unexpected status of '$INTERNAL_SERVER_ERROR' was returned when attempting to update language to: '${language.code}' for regId: '$regId'")
          }
        }
      }
    }
  }
}
