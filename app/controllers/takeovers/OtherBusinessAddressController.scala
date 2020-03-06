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

package controllers.takeovers

import config.FrontendAppConfig
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import controllers.auth.AuthFunction
import controllers.reg.{ControllerErrorHandler, routes => regRoutes}
import controllers.takeovers.OtherBusinessAddressController._
import forms.takeovers.OtherBusinessAddressForm
import javax.inject.{Inject, Singleton}
import models.{NewAddress, TakeoverDetails}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent}
import services.{AddressLookupFrontendService, AddressPrepopulationService, TakeoverService}
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.http.NotFoundException
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import utils.{SCRSFeatureSwitches, SessionRegistration}
import views.html.takeovers.OtherBusinessAddress

import scala.concurrent.Future

@Singleton
class OtherBusinessAddressController @Inject()(val authConnector: PlayAuthConnector,
                                               val takeoverService: TakeoverService,
                                               val addressPrepopulationService: AddressPrepopulationService,
                                               val addressLookupFrontendService: AddressLookupFrontendService,
                                               val compRegConnector: CompanyRegistrationConnector,
                                               val keystoreConnector: KeystoreConnector,
                                               val scrsFeatureSwitches: SCRSFeatureSwitches
                                              )(implicit val appConfig: FrontendAppConfig,
                                                val messagesApi: MessagesApi
                                              ) extends FrontendController with AuthFunction with ControllerErrorHandler with SessionRegistration with I18nSupport {

  val show: Action[AnyContent] = Action.async { implicit request =>
    ctAuthorised {
      checkStatus { regId =>
        if (scrsFeatureSwitches.takeovers.enabled) {
          takeoverService.getTakeoverDetails(regId).flatMap {
            case Some(TakeoverDetails(false, _, _, _, _)) =>
              Future.successful(Redirect(regRoutes.AccountingDatesController.show()))
            case Some(TakeoverDetails(_, None, _, _, _)) =>
              Future.successful(Redirect(routes.OtherBusinessNameController.show()))
            case Some(TakeoverDetails(_, Some(businessName), Some(prePopAddress), _, _)) =>
              addressPrepopulationService.retrieveAddresses(regId).map {
                addressSeq =>
                  Ok(OtherBusinessAddress(OtherBusinessAddressForm.form.fill(addressSeq.indexOf(prePopAddress).toString), businessName, addressSeq))
                    .addingToSession(addressSeqKey -> Json.toJson(addressSeq).toString())
                    .addingToSession(businessNameKey -> businessName)
              }
            case Some(TakeoverDetails(_, Some(businessName), _, _, _)) =>
              addressPrepopulationService.retrieveAddresses(regId).map {
                addressSeq =>
                  Ok(OtherBusinessAddress(OtherBusinessAddressForm.form, businessName, addressSeq))
                    .addingToSession(addressSeqKey -> Json.toJson(addressSeq).toString())
                    .addingToSession(businessNameKey -> businessName)
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
      registered { regId =>
        val optBusinessName: Option[String] = request.session.get(businessNameKey)
        val optAddressSeq: Option[Seq[NewAddress]] = request.session.get(addressSeqKey).map(Json.parse(_).as[Seq[NewAddress]])
        (optBusinessName, optAddressSeq) match {
          case (Some(businessName), Some(addressSeq)) => OtherBusinessAddressForm.form.bindFromRequest.fold(
            formWithErrors =>
              Future.successful(BadRequest(OtherBusinessAddress(formWithErrors, businessName, addressSeq))),
            addressChoice => {
              if (addressChoice == "Other") {
                Future.successful(Redirect(regRoutes.AccountingDatesController.show())) //TODO redirect to ALF
              }
              else {
                takeoverService.updateBusinessAddress(regId, addressSeq(addressChoice.toInt)).map {
                  _ =>
                    Redirect(regRoutes.AccountingDatesController.show()) //TODO redirect to next page when it's done
                      .removingFromSession(businessNameKey)
                      .removingFromSession(addressSeqKey)
                }
              }
            }
          )
          case _ => Future.successful(Redirect(routes.OtherBusinessAddressController.show()))
        }
      }
    }
  }

  val handbackFromALF: Action[AnyContent] = Action.async { implicit request =>
    ctAuthorised {
      checkStatus { regId =>
        for {
          address <- addressLookupFrontendService.getAddress
          _ <- takeoverService.updateBusinessAddress(regId, address)
        } yield Redirect(regRoutes.AccountingDatesController.show()) //TODO redirect to next page when it's done
          .removingFromSession(businessNameKey)
          .removingFromSession(addressSeqKey)
      }
    }
  }
}

object OtherBusinessAddressController {
  val businessNameKey: String = "businessName"
  val addressSeqKey: String = "addressSeq"
}
