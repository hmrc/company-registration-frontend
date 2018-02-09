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

package controllers.reg

import _root_.connectors.{BusinessRegistrationConnector, CompanyRegistrationConnector, KeystoreConnector, S4LConnector}
import address.client.RecordSet
import config.FrontendAuthConnector
import controllers.auth.AuthFunction
import forms.PPOBForm
import models._
import models.handoff.BackHandoff
import play.api.Logger
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import repositories.NavModelRepo
import services._
import uk.gov.hmrc.auth.core.retrieve.Retrievals
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.frontend.controller.FrontendController
import utils.{Jwe, MessagesSupport, SessionRegistration}

import scala.concurrent.Future

object PPOBController extends PPOBController{
  val authConnector = FrontendAuthConnector
  val s4LConnector = S4LConnector
  val keystoreConnector = KeystoreConnector
  val addressLookupService = AddressLookupService
  val addressLookupFrontendService = AddressLookupFrontendService
  val companyRegistrationConnector = CompanyRegistrationConnector
  val pPOBService = PPOBService
  val handOffService = HandOffServiceImpl
  val navModelMongo =  NavModelRepo.repository
  val businessRegConnector = BusinessRegistrationConnector
}

trait PPOBController extends FrontendController with AuthFunction with HandOffNavigator with ServicesConfig with AddressConverter
  with SessionRegistration with ControllerErrorHandler with MessagesSupport {

  val s4LConnector : S4LConnector
  val keystoreConnector : KeystoreConnector
  val addressLookupService : AddressLookupService
  val addressLookupFrontendService : AddressLookupFrontendService
  val companyRegistrationConnector: CompanyRegistrationConnector
  val pPOBService : PPOBService
  val handOffService : HandOffService
  val businessRegConnector: BusinessRegistrationConnector

  implicit val formatRecordSet = Json.format[RecordSet]

  def show: Action[AnyContent] = Action.async {
    implicit request =>
      ctAuthorised {
        checkStatus { regId =>
          for {
            ctReg <- companyRegistrationConnector.retrieveCorporationTaxRegistration(regId)
            ro = ctReg.as(NewAddress.roReads)
            ppob = ctReg.asOpt(NewAddress.ppobFormats)
            choice = addressChoice(ppob, ctReg)
            form = PPOBForm.aLFForm.fill(choice)
          } yield {
            Ok(views.html.reg.PrinciplePlaceOfBusiness(form, ro, ppob))
          }
        }
      }
  }

  private[controllers] def addressChoice(ppob: Option[_], ctReg: JsValue): PPOBChoice = {
    if(ppob.isDefined) PPOBChoice("PPOB")else if(ctReg.as[String](NewAddress.readAddressType) == "RO") PPOBChoice("RO") else PPOBChoice("")
  }

  val saveALFAddress: Action[AnyContent] = Action.async {
    implicit request =>
      ctAuthorised {
        checkStatus { regId =>
          for {
            address <- addressLookupFrontendService.getAddress
            res <- pPOBService.saveAddress(regId, "PPOB", Some(address))
            _ <- updatePrePopAddress(regId, address)
          } yield res match {
            case _ => Redirect(controllers.reg.routes.CompanyContactDetailsController.show())
          }
        }
      }
  }

  def submit = Action.async {
    implicit request =>
      ctAuthorisedCredID { credID =>
        checkStatus { regId =>
          PPOBForm.aLFForm.bindFromRequest().fold[Future[Result]](
            errors => {
              for {
                ctReg <- companyRegistrationConnector.retrieveCorporationTaxRegistration(regId)
                ro = ctReg.as(NewAddress.roReads)
                ppob = ctReg.asOpt(NewAddress.ppobFormats)
              } yield {
                BadRequest(views.html.reg.PrinciplePlaceOfBusiness(errors, ro, ppob))
              }
            },
            success => {
              success.choice match {
                case "RO" =>
                  for {
                    _ <- pPOBService.saveAddress(regId, "RO")
                    companyDetails <- pPOBService.retrieveCompanyDetails(regId)
                    _ <- pPOBService.auditROAddress(regId, credID, companyDetails.companyName, companyDetails.cHROAddress)
                  } yield {
                    Redirect(controllers.reg.routes.CompanyContactDetailsController.show())
                  }
                case "PPOB" =>
                  Future.successful(Redirect(controllers.reg.routes.CompanyContactDetailsController.show()))
                case "Other" =>
                  addressLookupFrontendService.buildAddressLookupUrl("ctreg1", controllers.reg.routes.PPOBController.saveALFAddress()) map {
                    redirectUrl => Redirect(redirectUrl)
                  }
                case unexpected =>
                  Logger.warn(s"[PPOBController] [Submit] '$unexpected' address choice submitted for reg ID: $regId")
                  Future.successful(BadRequest(defaultErrorPage))
              }
            }
          )
        }
      }
  }


  def back: Action[AnyContent] = Action.async {
    implicit request =>
      ctAuthorisedOptStr(Retrievals.externalId) { externalID =>
        registered { _ =>
          (for {
            navModel <- fetchNavModel()
            backPayload <- handOffService.buildBackHandOff(externalID)
          } yield {
            val payload = Jwe.encrypt[BackHandoff](backPayload).getOrElse("")
            val url = navModel.receiver.nav("2").reverse
            Redirect(handOffService.buildHandOffUrl(url, payload))
          }).recover {
            case ex: NavModelNotFoundException => Redirect(controllers.reg.routes.SignInOutController.postSignIn(None))
          }
        }
      }
  }


  private[controllers] def updatePrePopAddress(regId: String, address: NewAddress)(implicit hc: HeaderCarrier): Future[Boolean] = {
    val a = Address(
      None,
      address.addressLine1,
      address.addressLine2,
      address.addressLine3,
      address.addressLine4,
      address.postcode,
      address.country,
      None,
      auditRef = address.auditRef
    )

    businessRegConnector.updatePrePopAddress(regId, a)
  }

  private def getAddressFromRecordSetByID(recordSet: RecordSet, id: String): Option[Address] = {
    val address = for(r <- recordSet.addresses if r.id == id) yield r
    if (address.nonEmpty) Some(addressLookupService.convertLookupAddressToHMRCFormat(address.head)) else None
  }
}
