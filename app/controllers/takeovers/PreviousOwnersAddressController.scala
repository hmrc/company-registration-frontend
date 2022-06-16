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

package controllers.takeovers

import config.AppConfig
import connectors.{BusinessRegistrationConnector, CompanyRegistrationConnector, KeystoreConnector}
import controllers.auth.AuthenticatedController
import controllers.reg.{ControllerErrorHandler, routes => regRoutes}
import controllers.takeovers.PreviousOwnersAddressController._
import forms.takeovers.HomeAddressForm
import javax.inject.{Inject, Singleton}
import models.takeovers.{OtherAddress, PreselectedAddress}
import models.{NewAddress, TakeoverDetails}
import play.api.i18n.Messages
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.{AddressLookupFrontendService, AddressPrepopulationService, TakeoverService}
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.http.NotFoundException
import utils.{SCRSFeatureSwitches, SessionRegistration}
import views.html.takeovers.{HomeAddress => HomeAddressView}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PreviousOwnersAddressController @Inject()(val authConnector: PlayAuthConnector,
                                                val takeoverService: TakeoverService,
                                                val addressPrepopulationService: AddressPrepopulationService,
                                                val addressLookupFrontendService: AddressLookupFrontendService,
                                                val compRegConnector: CompanyRegistrationConnector,
                                                val businessRegConnector: BusinessRegistrationConnector,
                                                val keystoreConnector: KeystoreConnector,
                                                val scrsFeatureSwitches: SCRSFeatureSwitches,
                                                val controllerComponents: MessagesControllerComponents,
                                                val controllerErrorHandler: ControllerErrorHandler,
                                                view: HomeAddressView
                                               )(implicit val appConfig: AppConfig
                                               ) extends AuthenticatedController with SessionRegistration {

  implicit val ec: ExecutionContext = controllerComponents.executionContext

  val show: Action[AnyContent] = Action.async { implicit request =>
    ctAuthorised {
      checkStatus {
        regId =>

          takeoverService.getTakeoverDetails(regId).flatMap {
            case Some(TakeoverDetails(false, _, _, _, _)) =>
              Future.successful(Redirect(regRoutes.AccountingDatesController.show))
            case Some(TakeoverDetails(_, None, _, _, _)) =>
              Future.successful(Redirect(routes.OtherBusinessNameController.show))
            case Some(TakeoverDetails(_, _, None, _, _)) =>
              Future.successful(Redirect(routes.OtherBusinessAddressController.show))
            case Some(TakeoverDetails(_, _, _, None, _)) =>
              Future.successful(Redirect(routes.WhoAgreedTakeoverController.show))
            case Some(TakeoverDetails(_, _, _, Some(previousOwnersName), optPreviousOwnerHomeAddress)) =>
              addressPrepopulationService.retrieveAddresses(regId).map {
                addressSeq =>
                  val prepopulatedForm = addressSeq.zipWithIndex.collectFirst {
                    case (preselectedAddress, index) if optPreviousOwnerHomeAddress.exists(address => address isEqualTo preselectedAddress) =>
                      index
                  } match {
                    case Some(index) =>
                      HomeAddressForm.form(addressSeq.length).fill(PreselectedAddress(index))
                    case None =>
                      HomeAddressForm.form(addressSeq.length)
                  }

                  Ok(view(prepopulatedForm, previousOwnersName, addressSeq))
                    .addingToSession(addressSeqKey -> Json.toJson(addressSeq).toString())
              }
            case None =>
              Future.successful(Redirect(routes.ReplacingAnotherBusinessController.show))
          }
      }
    }
  }

  val submit: Action[AnyContent] = Action.async { implicit request =>
    ctAuthorised {
      registered {
        regId =>
          takeoverService.getTakeoverDetails(regId).flatMap {
            optTakeoverDetails =>
              val optAddressSeq: Option[Seq[NewAddress]] = request.session.get(addressSeqKey).map(Json.parse(_).as[Seq[NewAddress]])
              (optTakeoverDetails, optAddressSeq) match {
                case (Some(TakeoverDetails(_, _, _, Some(previousOwnersName), _)), Some(addressSeq)) =>
                  HomeAddressForm.form(addressSeq.length).bindFromRequest.fold(
                    formWithErrors =>
                      Future.successful(BadRequest(view(formWithErrors, previousOwnersName, addressSeq))),
                    {
                      case OtherAddress =>
                        addressLookupFrontendService.initialiseAlfJourney(
                          handbackLocation = controllers.takeovers.routes.PreviousOwnersAddressController.handbackFromALF(None),
                          specificJourneyKey = takeoversKey,
                          lookupPageHeading = Messages("page.addressLookup.takeovers.homeAddress.lookup.heading", previousOwnersName),
                          confirmPageHeading = Messages("page.addressLookup.takeovers.homeAddress.confirm.description", previousOwnersName)
                        ).map(Redirect(_))
                      case PreselectedAddress(index) =>
                        takeoverService.updatePreviousOwnersAddress(regId, addressSeq(index)).map {
                          _ => Redirect(regRoutes.AccountingDatesController.show).removingFromSession(addressSeqKey)
                        }
                    }
                  )
                case _ => Future.successful(Redirect(routes.PreviousOwnersAddressController.show))
              }
          }
      }
    }
  }

  def handbackFromALF(alfId: Option[String]): Action[AnyContent] = Action.async { implicit request =>
    ctAuthorised {
      checkStatus {
        regId =>
          alfId match {
            case Some(id) =>
              for {
                address <- addressLookupFrontendService.getAddress(id)
                _ <- takeoverService.updatePreviousOwnersAddress(regId, address)
                _ <- businessRegConnector.updatePrePopAddress(regId, address)
              } yield Redirect(regRoutes.AccountingDatesController.show).removingFromSession(addressSeqKey)
            case _ =>
              throw new Exception("[Takeovers] [Previous Owners Address] 'id' query string missing from ALF handback")
          }
      }
    }
  }
}

object PreviousOwnersAddressController {
  val takeoversKey: String = "takeovers"
  val addressSeqKey: String = "addressSeq"
}
