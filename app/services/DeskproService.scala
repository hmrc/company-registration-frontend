/*
 * Copyright 2019 HM Revenue & Customs
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

import javax.inject.Inject

import connectors.DeskproConnector
import models.external.Ticket
import models.{Ticket => TicketForm}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class DeskproServiceImpl @Inject()(val deskproConnector: DeskproConnector) extends DeskproService

trait DeskproService {
  val deskproConnector : DeskproConnector

  private[services] def getSessionId(implicit hc: HeaderCarrier) : String = hc.sessionId.map(_.value).getOrElse("n/a")

  private[services] def buildTicket(regId: String, data: TicketForm, uri: String)(implicit hc: HeaderCarrier) : Ticket =
    Ticket(
      data.name,
      data.email,
      subject = s"Company Registration submission failed for Registration ID: $regId",
      data.message,
      referrer = "https://www.tax.service.gov.uk/register-your-company",
      javascriptEnabled = "Y",
      userAgent = "company-registration-frontend",
      authId = uri,
      areaOfTax = "unknown",
      sessionId = getSessionId,
      service = "SCRS"
    )

  def submitTicket(regId: String, data: TicketForm, uri : String)(implicit hc: HeaderCarrier) : Future[Long] = {
    deskproConnector.submitTicket(buildTicket(regId, data, uri))
  }

}
