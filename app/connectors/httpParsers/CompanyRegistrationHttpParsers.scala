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

import models.Language
import play.api.http.Status.{NOT_FOUND, NO_CONTENT}
import uk.gov.hmrc.http.{HttpReads, HttpResponse}
import utils.Logging

trait CompanyRegistrationHttpParsers extends Logging {

  def updateLanguageHttpReads(regId: String, language: Language): HttpReads[Boolean] = (_: String, _: String, response: HttpResponse) => {

    val logContext = s" language to: '${language.code}' for regId: '$regId'"

    response.status match {
      case NO_CONTENT =>
        logger.debug(s"[updateLanguageHttpReads] Updated" + logContext)
        true
      case NOT_FOUND =>
        logger.warn(s"[updateLanguageHttpReads] No document was found ($NOT_FOUND) when attempting to update" + logContext)
        false
      case status =>
        logger.error(s"[updateLanguageHttpReads] An unexpected status of '$status' was returned when attempting to update" + logContext)
        false
    }
  }
}
