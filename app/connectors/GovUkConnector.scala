/*
 * Copyright 2017 HM Revenue & Customs
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

import config.WSHttp
import connectors.CohoAPIConnector.getConfString
import models.DomainFormats
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.time.workingdays.BankHolidaySet

import scala.concurrent._
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.http.logging.LoggingDetails

object GovUkConnector extends GovUkConnector with ServicesConfig {
  val http = WSHttp
  lazy val bankHolidaysUrl = getConfString("bank-holidays.url", throw new Exception("bank-holidays.url not found"))
}

trait GovUkConnector extends DomainFormats {
  val http : HttpGet
  val bankHolidaysUrl: String
  val englandAndWales: String = "england-and-wales"

  def retrieveBankHolidaysForEnglandAndWales(implicit hc: HeaderCarrier): Future[BankHolidaySet] = {
    processBankHolidays(retrieveBankHolidays)
  }

  private[connectors] def processBankHolidays(holidaysF: Future[Map[String, BankHolidaySet]])(implicit ld: LoggingDetails): Future[BankHolidaySet] =
    holidaysF.map { holidays =>
      pullOutSetByName(holidays, englandAndWales)
    }.recoverWith {
      case t: Throwable => Future.failed(new Exception("Failed to retrieve bank holidays from gov.uk", t))
    }

  private[connectors] def pullOutSetByName(holidays: Map[String, BankHolidaySet], setName: String): BankHolidaySet = {
    holidays.getOrElse(setName, throw new Exception(s"Could not find a BankHolidaySet for $setName in $holidays"))
  }

  private def retrieveBankHolidays(implicit hc: HeaderCarrier): Future[Map[String, BankHolidaySet]] =
    http.GET[Map[String, BankHolidaySet]](bankHolidaysUrl + "/bank-holidays.json")
}
