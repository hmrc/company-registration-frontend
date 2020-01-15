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

package controllers.groups

import config.FrontendAppConfig
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import controllers.auth.AuthFunction
import controllers.reg.ControllerErrorHandler
import forms.GroupNameForm
import javax.inject.Inject

import models.Groups
import play.api.i18n.{I18nSupport, MessagesApi}
import services.{GroupPageEnum, GroupService}
import uk.gov.hmrc.auth.core.PlayAuthConnector
import play.api.mvc.{Action, Request}
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import utils.SessionRegistration
import views.html.groups.GroupNameView

import scala.concurrent.Future

class GroupNameControllerImpl @Inject()(val authConnector: PlayAuthConnector,
                                        val groupService: GroupService,
                                        val compRegConnector: CompanyRegistrationConnector,
                                        val keystoreConnector: KeystoreConnector,
                                        val appConfig: FrontendAppConfig,
                                        val messagesApi: MessagesApi) extends GroupNameController

trait GroupNameController extends FrontendController with AuthFunction with ControllerErrorHandler with SessionRegistration with I18nSupport {
  implicit val appConfig: FrontendAppConfig
  val groupService: GroupService

  protected def showFunc(groups: Groups, regID: String)(implicit request: Request[_]) = {
    groupService.checkGroupNameMatchAndPotentiallyDropOptionalBlocks(groups, regID).map { res =>
      val (listOfShareholders, groupsUpdated) = res
      val formVal = groups.nameOfCompany.fold(GroupNameForm.form)(gName => GroupNameForm.form.fill(gName))
      Ok(GroupNameView(formVal, groupsUpdated.nameOfCompany, listOfShareholders))
    }
  }

    protected def submitFunc(regID: String)(groups: Groups)(implicit request: Request[_]) = {

      groupService.checkGroupNameMatchAndPotentiallyDropOptionalBlocks(groups, regID).flatMap { res =>
        val (listOfShareholders, groupsUpdated) = res
        GroupNameForm.form.bindFromRequest.fold(
          errors => {
            Future.successful(BadRequest(GroupNameView(errors, groupsUpdated.nameOfCompany, listOfShareholders)))
          },
          name => {
            groupService.updateGroupName(name, groupsUpdated, regID).map { _ =>
              Redirect(controllers.groups.routes.GroupAddressController.show())
            }
          }
        )
      }
    }

      val show = Action.async { implicit request =>
        ctAuthorised {
          checkStatus { regID =>
            groupService.retrieveGroups(regID).flatMap {
              optGroups =>
                groupService.groupsUserSkippedPage(optGroups, GroupPageEnum.shareholderNamePage) { groups =>
                  showFunc(groups, regID)
                }
            }
          }
        }
      }



      val submit = Action.async { implicit request =>
        ctAuthorised {
          checkStatus { regID =>
            groupService.retrieveGroups(regID).flatMap {
              optGroups =>
                groupService.groupsUserSkippedPage(optGroups, GroupPageEnum.shareholderNamePage) {
                  submitFunc(regID)
                }
            }
          }
        }
      }
    }



