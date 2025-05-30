/*
 * Copyright 2025 HM Revenue & Customs
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

import com.google.inject.{Inject, Singleton}
import utils.Logging

import scala.concurrent.ExecutionContext

@Singleton
class BankHolidaysLoader @Inject()(val bankHolidaysService: BankHolidaysService)(implicit val ec: ExecutionContext) extends Logging {

  bankHolidaysService.loadEnglandAndWalesBankHolidays().map { data =>
    logger.info("BankHolidays loaded at start-up")
    logger.info(s"$data")
  }.recover {
    case ex =>
      logger.warn("Failed to load BankHolidays at startup.", ex)
      logger.warn("Fallback - BankHolidays will be read from conf file")
  }
}
