/*
 * Copyright 2023 HM Revenue & Customs
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

import config.AppConfig
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import controllers.auth.AuthenticatedController
import controllers.groups.GroupAddressController._
import controllers.reg.ControllerErrorHandler
import forms.GroupAddressForm

import javax.inject.{Inject, Singleton}
import models._
import play.api.i18n.{Messages, MessagesProvider}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import services.{AddressLookupFrontendService, GroupService}
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.http.{HeaderCarrier, InternalServerException}
import utils.SessionRegistration
import views.html.groups.{GroupAddressView}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class GroupAddressController @Inject()(val authConnector: PlayAuthConnector,
                                       val groupService: GroupService,
                                       val compRegConnector: CompanyRegistrationConnector,
                                       val keystoreConnector: KeystoreConnector,
                                       val addressLookupFrontendService: AddressLookupFrontendService,
                                       val controllerComponents: MessagesControllerComponents,
                                       view: GroupAddressView
                                      )(implicit val appConfig: AppConfig, implicit val ec: ExecutionContext)
  extends SessionRegistration with AuthenticatedController {

  val show: Action[AnyContent] = Action.async { implicit request =>
    ctAuthorised {
      checkStatus { regID =>
        groupService.retrieveGroups(regID).flatMap {
          case Some(groups@Groups(true, Some(companyName), optAddressAndType, _)) =>
            (companyName.nameType, optAddressAndType) match {
              case ("Other", Some(addressAndType)) =>
                Future.successful(Ok(view(
                  GroupAddressForm.form.fill(GroupAddressChoice(addressAndType.addressType)),
                  Map("ALF" -> addressAndType.address.toString),
                  companyName.name))
                )
              case ("Other", None) => alfRedirect(regID, groups)
              case _ =>
                groupService.retreiveValidatedTxApiAddress(groups, regID).flatMap {
                  case None => alfRedirect(regID, groups)
                  case Some(address) =>
                    groupService.dropOldFields(groups, address, regID).flatMap { updatedGroups =>
                      Future.successful(Ok(view(
                        updatedGroups.addressAndType.fold(GroupAddressForm.form)(address => GroupAddressForm.form.fill(GroupAddressChoice(address.addressType))),
                        groupService.createAddressMap(updatedGroups.addressAndType, address),
                        companyName.name))
                      )
                    }
                }
            }
          case Some(Groups(true, None, _, _)) =>
            Future.successful(Redirect(controllers.groups.routes.GroupNameController.show.url))
          case _ =>
            Future.successful(Redirect(controllers.reg.routes.SignInOutController.postSignIn()))
        }
      }
    }
  }


  val submit: Action[AnyContent] = Action.async { implicit request =>
    ctAuthorised {
      checkStatus { regID =>
        groupService.retrieveGroups(regID).flatMap {
          case Some(groups@Groups(true, Some(companyName), optAddressAndType, _)) =>
            GroupAddressForm.form.bindFromRequest.fold(
              errors => {
                groupService.retreiveValidatedTxApiAddress(groups, regID).flatMap {
                  case Some(address) => Future.successful(BadRequest(view(
                    errors,
                    groupService.createAddressMap(optAddressAndType, address),
                    companyName.name)))
                  case None => alfRedirect(regID, groups)
                }
              },
              success => {
                success.choice match {
                  case "TxAPI" => groupService.saveTxShareHolderAddress(groups, regID).flatMap {
                    _.fold(
                      _ => alfRedirect(regID, groups),
                      _ => Future.successful(Redirect(controllers.groups.routes.GroupUtrController.show)))
                  }
                  case "Other" => alfRedirect(regID, groups)
                  case _ => Future.successful(Redirect(controllers.groups.routes.GroupUtrController.show))
                }
              }
            )
          case _ =>
            Future.failed(new InternalServerException("[GroupAddressController] [submit] Missing prerequisite group data"))
        }
      }
    }
  }

  def handbackFromALF(alfId: Option[String]): Action[AnyContent] = Action.async {
    implicit request =>
      ctAuthorised {
        checkStatus { regID =>
          alfId match {
            case Some(id) =>
              for {
                address <- addressLookupFrontendService.getAddress(id)
                _ <- groupService.updateGroupAddress(GroupsAddressAndType("ALF", address), regID)
              } yield Redirect(controllers.groups.routes.GroupUtrController.show)
            case None =>
              throw new InternalServerException("[GroupsAddressController] [handbackFromALF] 'id' query string missing from ALF handback")
          }
        }
      }
  }

  private def alfRedirect(regID: String, groups: Groups)(implicit hc: HeaderCarrier, messagesProvider: MessagesProvider): Future[Result] = {
    groups.nameOfCompany match {
      case Some(groupCompanyName) =>
        addressLookupFrontendService.initialiseAlfJourney(
          handbackLocation = controllers.groups.routes.GroupAddressController.handbackFromALF(None),
          specificJourneyKey = groupsKey,
          lookupPageHeading = Messages("page.addressLookup.Groups.lookup.heading", groupCompanyName.name),
          confirmPageHeading = Messages("page.addressLookup.Groups.confirm.description", groupCompanyName.name)
        ).map(Redirect(_))
      case None =>
        throw new Exception("[GroupsAddressController] [alfRedirect] user attempted to skip to ALF without saving a group name")
    }
  }
}

object GroupAddressController {
  val groupsKey = "Groups"
}