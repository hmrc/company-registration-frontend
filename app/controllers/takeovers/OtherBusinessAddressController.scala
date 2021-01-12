/*
 * Copyright 2021 HM Revenue & Customs
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

import config.FrontendAppConfig
import connectors.{BusinessRegistrationConnector, CompanyRegistrationConnector, KeystoreConnector}
import controllers.auth.AuthenticatedController
import controllers.reg.{ControllerErrorHandler, routes => regRoutes}
import controllers.takeovers.OtherBusinessAddressController._
import forms.takeovers.OtherBusinessAddressForm
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
import views.html.takeovers.OtherBusinessAddress

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class OtherBusinessAddressController @Inject()(val authConnector: PlayAuthConnector,
                                               val takeoverService: TakeoverService,
                                               val addressPrepopulationService: AddressPrepopulationService,
                                               val addressLookupFrontendService: AddressLookupFrontendService,
                                               val compRegConnector: CompanyRegistrationConnector,
                                               val businessRegConnector: BusinessRegistrationConnector,
                                               val keystoreConnector: KeystoreConnector,
                                               val scrsFeatureSwitches: SCRSFeatureSwitches,
                                               val controllerComponents: MessagesControllerComponents
                                              )(implicit val appConfig: FrontendAppConfig
                                              ) extends AuthenticatedController with ControllerErrorHandler with SessionRegistration {

  implicit val ec: ExecutionContext = controllerComponents.executionContext

  val show: Action[AnyContent] = Action.async { implicit request =>
    ctAuthorised {
      checkStatus { regId =>
        if (scrsFeatureSwitches.takeovers.enabled) {
          takeoverService.getTakeoverDetails(regId).flatMap {
            case Some(TakeoverDetails(false, _, _, _, _)) =>
              Future.successful(Redirect(regRoutes.AccountingDatesController.show()))
            case Some(TakeoverDetails(_, None, _, _, _)) =>
              Future.successful(Redirect(routes.OtherBusinessNameController.show()))
            case Some(TakeoverDetails(_, Some(businessName), Some(preselectedTakeoverAddress), _, _)) =>
              addressPrepopulationService.retrieveAddresses(regId).map {
                addressSeq =>
                  val prepopulatedForm = addressSeq.zipWithIndex.collectFirst {
                    case (preselectedAddress, index) if preselectedAddress.isEqualTo(preselectedTakeoverAddress) =>
                      index
                  } match {
                    case Some(index) =>
                      OtherBusinessAddressForm.form(businessName, addressSeq.length).fill(PreselectedAddress(index))
                    case None =>
                      OtherBusinessAddressForm.form(businessName, addressSeq.length)
                  }

                  Ok(OtherBusinessAddress(prepopulatedForm, businessName, addressSeq))
                    .addingToSession(addressSeqKey -> Json.toJson(addressSeq).toString())
              }
            case Some(TakeoverDetails(_, Some(businessName), _, _, _)) =>
              addressPrepopulationService.retrieveAddresses(regId).map {
                addressSeq =>
                  Ok(OtherBusinessAddress(OtherBusinessAddressForm.form(businessName, addressSeq.length), businessName, addressSeq))
                    .addingToSession(addressSeqKey -> Json.toJson(addressSeq).toString())
              }
            case None =>
              Future.successful(Redirect(routes.ReplacingAnotherBusinessController.show()))
          }
        }
        else {
          Future.failed(new NotFoundException("Takeovers feature switch was not enabled."))
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
                case (Some(TakeoverDetails(_, Some(businessName), _, _, _)), Some(addressSeq)) => OtherBusinessAddressForm.form(businessName, addressSeq.length).bindFromRequest.fold(
                  formWithErrors =>
                    Future.successful(BadRequest(OtherBusinessAddress(formWithErrors, businessName, addressSeq))),
                  {
                    case OtherAddress =>
                      addressLookupFrontendService.initialiseAlfJourney(
                        handbackLocation = controllers.takeovers.routes.OtherBusinessAddressController.handbackFromALF(None),
                        specificJourneyKey = takeoversKey,
                        lookupPageHeading = Messages("page.addressLookup.takeovers.otherBusinessAddress.lookup.heading", businessName),
                        confirmPageHeading = Messages("page.addressLookup.takeovers.otherBusinessAddress.confirm.description", businessName)
                      ).map(Redirect(_))
                    case PreselectedAddress(index) =>
                      takeoverService.updateBusinessAddress(regId, addressSeq(index)).map {
                        _ =>
                          Redirect(routes.WhoAgreedTakeoverController.show())
                            .removingFromSession(addressSeqKey)
                      }
                  }
                )
                case _ => Future.successful(Redirect(routes.OtherBusinessAddressController.show()))
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
                _ <- takeoverService.updateBusinessAddress(regId, address)
                _ <- businessRegConnector.updatePrePopAddress(regId, address)
              } yield Redirect(routes.WhoAgreedTakeoverController.show()).removingFromSession(addressSeqKey)
            case _ =>
              throw new Exception("[Takeovers] [Other Business Address] 'id' query string missing from ALF handback")
          }
      }
    }
  }
}

object OtherBusinessAddressController {
  val takeoversKey: String = "takeovers"
  val addressSeqKey: String = "addressSeq"
}
