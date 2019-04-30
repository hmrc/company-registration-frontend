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
import forms.GroupUtrForm
import javax.inject.Inject
import models._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, Request}
import services.{CommonService, GroupPageEnum, GroupService}
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import utils.{SCRSExceptions, SessionRegistration}
import views.html.groups.GroupUtrView

import scala.concurrent.Future


class GroupUtrControllerImpl @Inject()(val authConnector: PlayAuthConnector,
                                       val keystoreConnector: KeystoreConnector,
                                       val groupService: GroupService,
                                       val appConfig: FrontendAppConfig,
                                       val compRegConnector: CompanyRegistrationConnector,
                                       val messagesApi: MessagesApi) extends GroupUtrController


trait GroupUtrController extends FrontendController with AuthFunction with CommonService with ControllerErrorHandler with SCRSExceptions with I18nSupport with SessionRegistration {


  implicit val appConfig: FrontendAppConfig
  val keystoreConnector: KeystoreConnector
  val groupService: GroupService

  protected def showFunc(groups: Groups)(implicit request: Request[_]) = {
    val groupParentCompanyName = groups.nameOfCompany.get
    val formVal = groups.groupUTR.fold(GroupUtrForm.form)(utr => GroupUtrForm.form.fill(utr))
    Future.successful(Ok(GroupUtrView(formVal, groupParentCompanyName.name)))
  }

  protected def submitFunc(regID: String)(groups: Groups)(implicit request: Request[_]) = {
    GroupUtrForm.form.bindFromRequest.fold(
      errors =>
        Future.successful(BadRequest(GroupUtrView(errors, groups.nameOfCompany.get.name))),
      relief => {
        groupService.updateGroupUtr(relief, groups, regID).map { _ =>
          Redirect(controllers.handoff.routes.GroupController.PSCGroupHandOff())
        }
      }
    )
  }


  val show: Action[AnyContent] = Action.async { implicit request =>
    ctAuthorised {
      checkStatus { regID =>
        groupService.retrieveGroups(regID).flatMap {
          optGroups =>
            groupService.groupsUserSkippedPage(optGroups, GroupPageEnum.utrPage) {
              showFunc
            }
        }
      }
    }
  }


  val submit = Action.async { implicit request =>
    ctAuthorised {
      registered { regID =>
        groupService.retrieveGroups(regID).flatMap { optGroups =>
          groupService.groupsUserSkippedPage(optGroups, GroupPageEnum.utrPage) {
            submitFunc(regID)
          }
        }
      }
    }
  }
}