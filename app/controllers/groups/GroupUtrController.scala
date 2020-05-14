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
import forms.GroupUtrForm
import javax.inject.{Inject, Singleton}
import models._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent}
import services.{CommonService, GroupService}
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.http.InternalServerException
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import utils.{SCRSExceptions, SessionRegistration}
import views.html.groups.GroupUtrView

import scala.concurrent.Future

@Singleton
class GroupUtrController @Inject()(val authConnector: PlayAuthConnector,
                                   val keystoreConnector: KeystoreConnector,
                                   val groupService: GroupService,
                                   val compRegConnector: CompanyRegistrationConnector,
                                   val messagesApi: MessagesApi
                                  )(implicit val appConfig: FrontendAppConfig)
  extends FrontendController with AuthFunction with CommonService with ControllerErrorHandler
    with SCRSExceptions with I18nSupport with SessionRegistration {

  val show: Action[AnyContent] = Action.async { implicit request =>
    ctAuthorised {
      checkStatus { regID =>
        groupService.retrieveGroups(regID).flatMap {
          case Some(Groups(true, Some(companyName), Some(_), Some(companyUtr))) =>
            Future.successful(Ok(GroupUtrView(GroupUtrForm.form.fill(companyUtr), companyName.name)))
          case Some(Groups(true, Some(companyName), Some(_), None)) =>
            Future.successful(Ok(GroupUtrView(GroupUtrForm.form, companyName.name)))
          case Some(Groups(true, Some(_), None, _)) =>
            Future.successful(Redirect(controllers.groups.routes.GroupAddressController.show().url))
          case Some(Groups(true, None, _, _)) =>
            Future.successful(Redirect(controllers.groups.routes.GroupNameController.show().url))
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
                Future.successful(BadRequest(GroupUtrView(errors, companyName.name))),
              groupUtr => {
                groupService.updateGroupUtr(groupUtr, groups, regID).map { _ =>
                  Redirect(controllers.handoff.routes.GroupController.PSCGroupHandOff())
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