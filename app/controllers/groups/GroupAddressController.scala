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

package controllers.groups

import config.FrontendAppConfig
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import controllers.auth.AuthFunction
import controllers.reg.ControllerErrorHandler
import forms.GroupAddressForm
import javax.inject.Inject
import models._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, Request, Result}
import services.{AddressLookupFrontendService, GroupPageEnum, GroupService}
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import utils.SessionRegistration
import views.html.groups.GroupAddressView

import scala.concurrent.Future

class GroupAddressControllerImpl @Inject()(val authConnector: PlayAuthConnector,
                                           val groupService: GroupService,
                                           val compRegConnector: CompanyRegistrationConnector,
                                           val keystoreConnector: KeystoreConnector,
                                           val appConfig: FrontendAppConfig,
                                           val addressLookupFrontendService: AddressLookupFrontendService,
                                           val messagesApi: MessagesApi) extends GroupAddressController

trait GroupAddressController extends FrontendController with AuthFunction with ControllerErrorHandler with SessionRegistration with I18nSupport {
  implicit val appConfig: FrontendAppConfig
  val addressLookupFrontendService: AddressLookupFrontendService

  val groupService: GroupService

  def handbackFromALF: Action[AnyContent] = Action.async {
    implicit request =>
      ctAuthorised {
        checkStatus { regID =>
          groupService.retrieveGroups(regID).flatMap { existingOptGroups =>
            groupService.groupsUserSkippedPage(existingOptGroups, GroupPageEnum.shareholderAddressPage) {
              handBackFromALFFunc(regID)
            }
          }
        }
      }
  }

  val show = Action.async { implicit request =>
    ctAuthorised {
      checkStatus { regID =>
        groupService.retrieveGroups(regID).flatMap {
          optGroups =>
            groupService.groupsUserSkippedPage(optGroups, GroupPageEnum.shareholderAddressPage) {
              showFunc(regID)
            }
        }
      }
    }
  }
  val submit = Action.async { implicit request =>
    ctAuthorised {
      checkStatus { regID =>
        groupService.retrieveGroups(regID).flatMap { existingOptGroups =>
          groupService.groupsUserSkippedPage(existingOptGroups, GroupPageEnum.shareholderAddressPage) {
            submitFunc(regID)
          }
        }
      }
    }
  }

  protected def handBackFromALFFunc(regID:String)(groups:Groups)(implicit request:Request[_]): Future[Result] = {
    for {
      address <- addressLookupFrontendService.getAddress
      res <- groupService.updateGroupAddress(GroupsAddressAndType("ALF", address), groups, regID)
    } yield Redirect(controllers.groups.routes.GroupUtrController.show())
  }

  protected def showFunc(regID:String)(groups: Groups)(implicit request:Request[_]): Future[Result] = {
    val form = (groupsBlock: Groups) => groupsBlock.addressAndType.fold(GroupAddressForm.form)(grps => GroupAddressForm.form.fill(GroupAddressChoice(grps.addressType)))
    val futureSuccess = (grps: Groups, addresses: Map[String,String]) => Future.successful(Ok(GroupAddressView(form(grps), addresses, groups.nameOfCompany.get.name)))
    val isOtherName = groups.nameOfCompany.get.nameType == "Other"
    val isAddressEmpty = groups.addressAndType.isEmpty
    (isOtherName, isAddressEmpty) match {
      case (true, true) => alfRedirect(regID)
      case (true, false) =>
        val potentialAddressInMap = Map("ALF" -> groups.addressAndType.get.address.mkString)
        futureSuccess(groups,potentialAddressInMap)
      case (false, _) => groupService.returnMapOfAddressesMatchDropAndReturnUpdatedGroups(groups, regID).flatMap { addressMapAndGroups =>
        val (mapOfAddresses, updatedGroups) = addressMapAndGroups
        if (mapOfAddresses.isEmpty) {
          alfRedirect(regID)
        } else {
         futureSuccess(updatedGroups,mapOfAddresses)
        }
      }

    }
  }
  protected def submitFunc(regID:String)(groups:Groups)(implicit r:Request[_]) = {
    GroupAddressForm.form.bindFromRequest.fold(
      errors => {
        groupService.returnMapOfAddressesMatchDropAndReturnUpdatedGroups(groups, regID).flatMap { addressMapAndGroups =>
          val (mapOfAddresses, updatedGroups) = addressMapAndGroups
          Future.successful(BadRequest(GroupAddressView(errors, mapOfAddresses, updatedGroups.nameOfCompany.get.name)))
        }
      },
      success => {
        success.choice match {
          case "TxAPI" => groupService.saveTxShareHolderAddress(groups, regID).flatMap {
            _.fold(
              _ => alfRedirect(regID),
              _ => Future.successful(Redirect(controllers.groups.routes.GroupUtrController.show())))
          }
          case "Other" => alfRedirect(regID)
          case _ => Future.successful(Redirect(controllers.groups.routes.GroupUtrController.show()))
        }
      }
    )
  }

  private def alfRedirect(regID: String)(implicit hc:HeaderCarrier) = {
    addressLookupFrontendService.buildAddressLookupUrlGroups(
      regID, controllers.groups.routes.GroupAddressController.handbackFromALF(), "Groups").map(Redirect(_))
  }
}