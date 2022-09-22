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

package controllers.reg

import _root_.connectors.{BusinessRegistrationConnector, CompanyRegistrationConnector, KeystoreConnector, S4LConnector}
import config.AppConfig
import controllers.auth.AuthenticatedController
import controllers.reg.PPOBController._
import forms.PPOBForm
import javax.inject.{Inject, Singleton}
import models._
import models.handoff.BackHandoff
import play.api.Logging
import play.api.i18n.Messages
import play.api.mvc._
import repositories.NavModelRepo
import services._
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.auth.core.retrieve.Retrievals
import uk.gov.hmrc.http.HeaderCarrier
import utils._
import views.html.reg.{PrinciplePlaceOfBusiness => PrinciplePlaceOfBusinessView}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PPOBController @Inject()(val authConnector: PlayAuthConnector,
                               val s4LConnector: S4LConnector,
                               val keystoreConnector: KeystoreConnector,
                               val compRegConnector: CompanyRegistrationConnector,
                               val handOffService: HandOffService,
                               val businessRegConnector: BusinessRegistrationConnector,
                               val navModelRepo: NavModelRepo,
                               val jwe: JweCommon,
                               val addressLookupFrontendService: AddressLookupFrontendService,
                               val pPOBService: PPOBService,
                               val scrsFeatureSwitches: SCRSFeatureSwitches,
                               val controllerComponents: MessagesControllerComponents,
                               val controllerErrorHandler: ControllerErrorHandler,
                               view: PrinciplePlaceOfBusinessView)
                              (implicit val appConfig: AppConfig, implicit val ec: ExecutionContext) extends AuthenticatedController
  with SessionRegistration with Logging {
  lazy val navModelMongo = navModelRepo.repository

  def show: Action[AnyContent] = Action.async {
    implicit request =>
      ctAuthorised {
        checkStatus { regId =>
          for {
            addresses <- pPOBService.fetchAddressesAndChoice(regId)
            choice = addresses._3
            form = PPOBForm.aLFForm.fill(choice)
          } yield {
            val addressMap = pPOBService.getAddresses(addresses)
              .collect { case (id, address) =>  id -> address.toString }
            Ok(view(form, addressMap))
          }
        }
      }
  }

  def saveALFAddress(alfId: Option[String]): Action[AnyContent] = Action.async {
    implicit request =>
      ctAuthorised {
        checkStatus {
          regId =>
            alfId match {
              case Some(id) => for {
                address <- addressLookupFrontendService.getAddress(id)
                _ <- pPOBService.saveAddress(regId, ppobKey, Some(address))
                _ <- updatePrePopAddress(regId, address)
              } yield {
                Redirect(controllers.reg.routes.CompanyContactDetailsController.show)
              }
              case None =>
                throw new Exception("[PPOBController] [ALF Handback] 'id' query string missing from ALF handback")
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
              } yield {
                val addressMap = pPOBService.getAddresses(addresses)
                  .collect { case (id, chAddress) =>  id -> chAddress.toString }
                BadRequest(view(errors, addressMap))
              }
            },
            success => {
              success.choice match {
                case "ROAddress" =>
                  for {
                    _ <- pPOBService.saveAddress(regId, "RO")
                    companyDetails <- pPOBService.retrieveCompanyDetails(regId)
                    _ <- pPOBService.auditROAddress(regId, credID, companyDetails.companyName, companyDetails.cHROAddress)
                  } yield {
                    Redirect(controllers.reg.routes.CompanyContactDetailsController.show)
                  }
                case "PPOBAddress" =>
                  Future.successful(Redirect(controllers.reg.routes.CompanyContactDetailsController.show))
                case "OtherAddress" =>
                  addressLookupFrontendService.initialiseAlfJourney(
                    handbackLocation = controllers.reg.routes.PPOBController.saveALFAddress(None),
                    specificJourneyKey = ppobKey,
                    lookupPageHeading = Messages("page.addressLookup.PPOB.lookup.heading"),
                    confirmPageHeading = Messages("page.addressLookup.PPOB.confirm.description")
                  ).map(Redirect(_))
                case unexpected =>
                  logger.warn(s"[PPOBController] [Submit] '$unexpected' address choice submitted for reg ID: $regId")
                  Future.successful(BadRequest(controllerErrorHandler.defaultErrorPage))
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

object PPOBController {
  val ppobKey = "PPOB"
}