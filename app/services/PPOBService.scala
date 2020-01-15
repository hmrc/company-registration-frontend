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

package services

import audit.events._
import connectors.{CompanyRegistrationConnector, KeystoreConnector, S4LConnector}
import javax.inject.Inject
import models.{Address => OldAddress, _}
import play.api.libs.json.JsValue
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import utils.SCRSExceptions

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PPOBServiceImpl @Inject()(val compRegConnector: CompanyRegistrationConnector,
                                val keystoreConnector: KeystoreConnector,
                                val s4LConnector: S4LConnector,
                                val auditConnector: AuditConnector) extends PPOBService

trait PPOBService extends SCRSExceptions {
  val keystoreConnector: KeystoreConnector
  val compRegConnector: CompanyRegistrationConnector
  val s4LConnector : S4LConnector
  val auditConnector : AuditConnector

  def retrieveCompanyDetails(regID: String)(implicit hc: HeaderCarrier): Future[CompanyDetails] = {
    for {
      companyDetails <- compRegConnector.retrieveCompanyDetails(regID)
    } yield {
      companyDetails match {
        case Some(details) => details
        case None => throw CompanyDetailsNotFoundException
      }
    }
  }

  def fetchAddressesAndChoice(regId: String)(implicit hc: HeaderCarrier): Future[(Option[CHROAddress], Option[NewAddress], PPOBChoice)] = {
    compRegConnector.retrieveCorporationTaxRegistration(regId) flatMap { ctReg =>
      val ro = (ctReg \\ "cHROAddress").head.as[CHROAddress]
      val ppob = ctReg.asOpt(NewAddress.ppobFormats)
      val choice = addressChoice(ppob, ctReg)

      compRegConnector.checkROValidPPOB(regId, ro) map {
        case Some(_) => (Some(ro), ppob, choice)
        case _       => (None, ppob, choice)
      }
    }
  }

  private[services] def addressChoice(ppob: Option[_], ctReg: JsValue) = {
    (ppob, ctReg) match {
      case (ppob, ctReg) if ppob.isDefined => PPOBChoice("PPOB")
      case (ppob, ctReg) if ctReg.as[String](NewAddress.readAddressType) == "RO" => PPOBChoice("RO")
      case _ => PPOBChoice("")
    }
  }

  def auditROAddress(regId: String, credID : String, companyName: String, chro: CHROAddress)
                    (implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[AuditResult] = {
    val event = new ROUsedAsPPOBAuditEvent(ROUsedAsPPOBAuditEventDetail(regId, credID, companyName, chro))
    auditConnector.sendExtendedEvent(event)
  }

  private def retrieveNewAddress(regId : String, cD: CompanyDetails, addressType : String, optAddress : Option[NewAddress])(implicit hc : HeaderCarrier): Future[Option[NewAddress]] =
    (addressType, optAddress) match {
      case ("RO", _) => compRegConnector.checkROValidPPOB(regId, cD.cHROAddress)
      case ("PPOB", a) => Future.successful(a)
      case _ => Future.successful(None)
    }

  def buildAddress(regId : String, cD: CompanyDetails, addressType: String, optAddress: Option[NewAddress])(implicit hc : HeaderCarrier): Future[CompanyDetails] = {
    retrieveNewAddress(regId, cD, addressType, optAddress) map { result =>
      result map { a =>
        OldAddress(
          None,
          a.addressLine1,
          a.addressLine2,
          a.addressLine3,
          a.addressLine4,
          a.postcode,
          a.country,
          None
        )
      }
    } map { address =>
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
  }

  def saveAddress(regId: String, addressType: String, address: Option[NewAddress] = None)(implicit hc: HeaderCarrier): Future[CompanyDetails] = {
    for {
      details     <- retrieveCompanyDetails(regId)
      newDetails  <- buildAddress(regId, details, addressType, address)
      _           <- compRegConnector.updateCompanyDetails(regId, newDetails)
    } yield {
      newDetails
    }
  }
}