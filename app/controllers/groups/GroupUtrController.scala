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

package controllers.groups

import config.AppConfig
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import controllers.auth.AuthenticatedController
import controllers.reg.ControllerErrorHandler
import forms.GroupUtrForm
import javax.inject.{Inject, Singleton}
import models._
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.{CommonService, GroupService}
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.http.InternalServerException
import utils.{SCRSExceptions, SessionRegistration}
import views.html.groups.GroupUtrView

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class GroupUtrController @Inject()(val authConnector: PlayAuthConnector,
                                   val keystoreConnector: KeystoreConnector,
                                   val groupService: GroupService,
                                   val compRegConnector: CompanyRegistrationConnector,
                                   val controllerComponents: MessagesControllerComponents,
                                   val view: GroupUtrView
                                  )(implicit val appConfig: AppConfig)
  extends AuthenticatedController with CommonService
    with SCRSExceptions with I18nSupport with SessionRegistration {

  implicit val ec: ExecutionContext = controllerComponents.executionContext

  val show: Action[AnyContent] = Action.async { implicit request =>
    ctAuthorised {
      checkStatus { regID =>
        groupService.retrieveGroups(regID).flatMap {
          case Some(Groups(true, Some(companyName), Some(_), Some(companyUtr))) =>
            Future.successful(Ok(view(GroupUtrForm.form.fill(companyUtr), companyName.name)))
          case Some(Groups(true, Some(companyName), Some(_), None)) =>
            Future.successful(Ok(view(GroupUtrForm.form, companyName.name)))
          case Some(Groups(true, Some(_), None, _)) =>
            Future.successful(Redirect(controllers.groups.routes.GroupAddressController.show.url))
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
      registered { regID =>
        groupService.retrieveGroups(regID).flatMap {
          case Some(groups@Groups(true, Some(companyName), Some(_), _)) =>
            GroupUtrForm.form.bindFromRequest.fold(
              errors =>
                Future.successful(BadRequest(view(errors, companyName.name))),
              groupUtr => {
                groupService.updateGroupUtr(groupUtr, groups, regID).map { _ =>
                  Redirect(controllers.handoff.routes.GroupController.PSCGroupHandOff)
                }
              }
            )
          case _ =>
            Future.failed(new InternalServerException("[GroupUtrController] [submit] Missing prerequisite group data"))
        }
      }
    }
  }
}