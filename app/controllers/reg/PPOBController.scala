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

package controllers.reg

import _root_.connectors.{BusinessRegistrationConnector, CompanyRegistrationConnector, KeystoreConnector, S4LConnector}
import config.FrontendAppConfig
import controllers.auth.AuthFunction
import forms.PPOBForm
import javax.inject.Inject
import models._
import models.handoff.BackHandoff
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import repositories.NavModelRepo
import services._
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.auth.core.retrieve.Retrievals
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import utils._

import scala.concurrent.Future

class PPOBControllerImpl @Inject()(val authConnector: PlayAuthConnector,
                                   val s4LConnector: S4LConnector,
                                   val keystoreConnector: KeystoreConnector,
                                   val compRegConnector: CompanyRegistrationConnector,
                                   val handOffService: HandOffService,
                                   val businessRegConnector: BusinessRegistrationConnector,
                                   val appConfig: FrontendAppConfig,
                                   val navModelRepo: NavModelRepo,
                                   val jwe: JweCommon,
                                   val addressLookupFrontendService: AddressLookupFrontendService,
                                   val pPOBService: PPOBService,
                                   val scrsFeatureSwitches: SCRSFeatureSwitches,
                                   val messagesApi: MessagesApi) extends PPOBController {
  lazy val navModelMongo =  navModelRepo.repository
}

trait PPOBController extends FrontendController with AuthFunction
  with SessionRegistration with ControllerErrorHandler with I18nSupport {

  implicit val appConfig: FrontendAppConfig

  val s4LConnector : S4LConnector
  val keystoreConnector : KeystoreConnector
  val addressLookupFrontendService : AddressLookupFrontendService
  val compRegConnector: CompanyRegistrationConnector
  val pPOBService : PPOBService
  val handOffService : HandOffService
  val businessRegConnector: BusinessRegistrationConnector
  val jwe: JweCommon

  def show: Action[AnyContent] = Action.async {
    implicit request =>
      ctAuthorised {
        checkStatus { regId =>
          for {
            addresses <- pPOBService.fetchAddressesAndChoice(regId)
            ro = addresses._1
            ppob = addresses._2
            choice = addresses._3
            form = PPOBForm.aLFForm.fill(choice)
          } yield {
            Ok(views.html.reg.PrinciplePlaceOfBusiness(form, ro, ppob))
          }
        }
      }
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
                addresses <- pPOBService.fetchAddressesAndChoice(regId)
                ro = addresses._1
                ppob = addresses._2
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
                  addressLookupFrontendService.buildAddressLookupUrl(controllers.reg.routes.PPOBController.saveALFAddress(), "PPOB") map {
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
            navModel <- handOffService.fetchNavModel()
            backPayload <- handOffService.buildBackHandOff(externalID)
          } yield {
            val payload = jwe.encrypt[BackHandoff](backPayload).getOrElse("")
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
}