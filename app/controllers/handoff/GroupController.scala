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

package controllers.handoff

import config.FrontendAppConfig
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import controllers.auth.AuthenticatedController
import controllers.reg.ControllerErrorHandler
import javax.inject.{Inject, Singleton}
import models.Groups
import models.handoff.{BackHandoff, GroupHandBackModel}
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import services.{GroupService, HandBackService, HandOffService, NavModelNotFoundException}
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.http.HeaderCarrier
import utils._
import views.html.error_template_restart

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class GroupController @Inject()(val authConnector: PlayAuthConnector,
                                val keystoreConnector: KeystoreConnector,
                                val handOffService: HandOffService,
                                val compRegConnector: CompanyRegistrationConnector,
                                val handBackService: HandBackService,
                                val groupService: GroupService,
                                val jwe: JweCommon,
                                val controllerComponents: MessagesControllerComponents
                               )(implicit val appConfig: FrontendAppConfig)
  extends AuthenticatedController with I18nSupport with SessionRegistration with ControllerErrorHandler {

  implicit val ec: ExecutionContext = controllerComponents.executionContext

  // 3.1 handback
  def groupHandBack(requestData: String): Action[AnyContent] = Action.async {
    implicit request =>
      ctAuthorisedHandoff("HO3-1", requestData) {
        registeredHandOff("HO3-1", requestData) { regID =>
          handBackService.processGroupsHandBack(requestData).flatMap {
            case Success(GroupHandBackModel(_, _, _, _, _, Some(true))) =>
              pscHandOffToGroupsIfDataInTxApi(regID)
            case Success(GroupHandBackModel(_, _, _, _, _, Some(false))) =>
              groupService.dropGroups(regID).map { _ =>
                Redirect(controllers.handoff.routes.GroupController.PSCGroupHandOff())
              }
            case _ => Future.successful(BadRequest(error_template_restart("3-1", "PayloadError")))
          }
        }
      }
  }

  def back: Action[AnyContent] = Action.async {
    implicit request =>
      ctAuthorisedOptStr(Retrievals.externalId) { externalID =>
        (for {
          navModel <- handOffService.fetchNavModel()
          backPayload <- handOffService.buildBackHandOff(externalID)
        } yield {
          val payload = jwe.encrypt[BackHandoff](backPayload).getOrElse("")
          Redirect(handOffService.buildHandOffUrl(s"${navModel.receiver.nav("3-1").reverse}", payload))
        }) recover {
          case ex: NavModelNotFoundException => Redirect(controllers.reg.routes.SignInOutController.postSignIn(None))
        }
      }
  }

  private[controllers] def pscHandOffToGroupsIfDataInTxApi(regID: String)(implicit hc: HeaderCarrier): Future[Result] = {
    groupService.fetchTxID(regID).flatMap { txId =>
      groupService.returnListOfShareholders(txId).flatMap { eitherShareholders =>
        groupService.dropOldGroups(eitherShareholders, regID)
      }
    }.flatMap {
      case Nil => Future.successful(Redirect(controllers.handoff.routes.GroupController.PSCGroupHandOff()))
      case _ => Future.successful(Redirect(controllers.groups.routes.GroupReliefController.show()))
    }
  }

  // 3.2 hand off
  val PSCGroupHandOff: Action[AnyContent] = Action.async {
    implicit request =>
      ctAuthorisedOptStr(Retrievals.externalId) { externalID =>
        registered { regId =>
          groupService.retrieveGroups(regId).flatMap { optGroups =>
            handOffService.buildPSCPayload(regId, externalID, optGroups) map {
              case Some((url, payload)) => Redirect(handOffService.buildHandOffUrl(url, payload))
              case None => BadRequest(error_template_restart("3-2", "EncryptionError"))
            } recover {
              case ex: NavModelNotFoundException => Redirect(controllers.reg.routes.SignInOutController.postSignIn(None))
            }
          }
        }
      }
  }

  // 3b-1 hand back, back link from Coho
  def pSCGroupHandBack(request: String): Action[AnyContent] = Action.async {
    implicit _request =>
      val groupsRedirect = (optGroups: Option[Groups]) => {
        optGroups.fold(Redirect(controllers.reg.routes.SignInOutController.postSignIn())) { groupsExist =>
          if (!groupsExist.groupRelief) {
            Redirect(controllers.groups.routes.GroupReliefController.show())
          } else {
            Redirect(controllers.groups.routes.GroupUtrController.show())
          }
        }
      }
      ctAuthorisedHandoff("HO3b-1", request) {
        registeredHandOff("HO3b-1", request) { regID =>
          handBackService.processGroupsHandBck(request).flatMap {
            case Success(_) => groupService.retrieveGroups(regID).map(groupsRedirect)
            case Failure(PayloadError) => Future.successful(BadRequest(error_template_restart("3b-1", "PayloadError")))
            case Failure(DecryptionError) => Future.successful(BadRequest(error_template_restart("3b-1", "DecryptionError")))
            case _ => Future.successful(InternalServerError(defaultErrorPage))
          }
        }
      }
  }
}