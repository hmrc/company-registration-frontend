/*
 * Copyright 2018 HM Revenue & Customs
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

import audit.events._
import config.{FrontendAuditConnector, FrontendAuthConnector}
import connectors.{CompanyRegistrationConnector, S4LConnector, KeystoreConnector}
import models._
import play.api.libs.json.Json
import play.api.mvc.{AnyContent, Request, RequestHeader}
import address.client.{Country, AddressRecord, RecordSet, Address}
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import utils.SCRSExceptions
import models.{Address => OldAddress}

import scala.concurrent.Future
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.http.HeaderCarrier

object PPOBService extends PPOBService {
  val compRegConnector = CompanyRegistrationConnector
  val keystoreConnector = KeystoreConnector
  val s4LConnector = S4LConnector
  val authConnector = FrontendAuthConnector
  val auditConnector = FrontendAuditConnector
  val addressLookupService = AddressLookupService
}

trait PPOBService extends SCRSExceptions {

  val keystoreConnector: KeystoreConnector
  val compRegConnector: CompanyRegistrationConnector
  val s4LConnector : S4LConnector
  val authConnector : AuthConnector
  val auditConnector : AuditConnector
  val addressLookupService : AddressLookupService

  private lazy val emptyFutureCacheMap = Future.successful(CacheMap("N/A", Map.empty))

  implicit val formatRecordSet = Json.format[RecordSet]

  def retrieveCompanyDetails(regID: String)(implicit user: AuthContext, hc: HeaderCarrier): Future[CompanyDetails] = {
    for{
      companyDetails <- compRegConnector.retrieveCompanyDetails(regID)
    } yield {
      companyDetails match {
        case Some(details) => details
        case None => throw CompanyDetailsNotFoundException
      }
    }
  }

  def auditROAddress(regId: String, userDetails: UserDetailsModel, companyName: String, chro: CHROAddress)
                    (implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[AuditResult] = {
    val event = new ROUsedAsPPOBAuditEvent(ROUsedAsPPOBAuditEventDetail(regId, userDetails.authProviderId, companyName, chro))
    auditConnector.sendExtendedEvent(event)
  }

  def buildAddress(cD: CompanyDetails, addressType: String, optAddress: Option[NewAddress]): CompanyDetails = {

    val address = (addressType, optAddress) match {
      case ("RO", _) => None
      case ("PPOB", Some(a)) => Some(OldAddress(
        None,
        a.addressLine1,
        a.addressLine2,
        a.addressLine3,
        a.addressLine4,
        a.postcode,
        a.country,
        None
      ))
    }

    CompanyDetails(
      cD.companyName,
      cD.cHROAddress,
      PPOB(
        addressType,
        address
      ),
      cD.jurisdiction
    )
  }

  def saveAddress(regId: String, addressType: String, address: Option[NewAddress] = None)
                 (implicit hc: HeaderCarrier, auth: AuthContext): Future[CompanyDetails] = {
    retrieveCompanyDetails(regId) flatMap { details =>
      compRegConnector.updateCompanyDetails(regId, buildAddress(details, addressType, address))
    }
  }
}
