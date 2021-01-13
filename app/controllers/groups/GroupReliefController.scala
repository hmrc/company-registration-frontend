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

package controllers.groups

import config.FrontendAppConfig
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import controllers.auth.AuthenticatedController
import controllers.reg.ControllerErrorHandler
import forms.GroupReliefForm
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.GroupService
import uk.gov.hmrc.auth.core.PlayAuthConnector
import utils.SessionRegistration
import views.html.groups.GroupReliefView

import scala.concurrent.ExecutionContext

@Singleton
class GroupReliefController @Inject()(val authConnector: PlayAuthConnector,
                                      val groupService: GroupService,
                                      val compRegConnector: CompanyRegistrationConnector,
                                      val keystoreConnector: KeystoreConnector,
                                      val controllerComponents: MessagesControllerComponents
                                     )(implicit val appConfig: FrontendAppConfig)
  extends AuthenticatedController with ControllerErrorHandler with SessionRegistration with I18nSupport {

  implicit val ec: ExecutionContext = controllerComponents.executionContext

  val show: Action[AnyContent] = Action.async { implicit request =>
    ctAuthorised {
      checkStatus { regID =>
        for {
          groups <- groupService.retrieveGroups(regID)
          companyName <- compRegConnector.fetchCompanyName(regID)
        } yield {
          val form: Form[Boolean] = groups.fold(GroupReliefForm.form)(grps => GroupReliefForm.form.fill(grps.groupRelief))
          Ok(GroupReliefView(form, companyName))
        }
      }
    }
  }

  val submit: Action[AnyContent] = Action.async { implicit request =>
    ctAuthorised {
      registered { regID =>
        GroupReliefForm.form.bindFromRequest.fold(
          errors =>
            compRegConnector.fetchCompanyName(regID).map { cName =>
              BadRequest(GroupReliefView(errors, cName))
            },
          relief => {
            groupService.updateGroupRelief(relief, regID).map { _ =>
              if (relief) {
                Redirect(routes.GroupNameController.show())
              } else {
                Redirect(controllers.handoff.routes.GroupController.PSCGroupHandOff())
              }
            }
          }
        )
      }
    }
  }
}