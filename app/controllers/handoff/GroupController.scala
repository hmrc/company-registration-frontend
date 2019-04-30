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

package controllers.handoff

import javax.inject.Inject
import config.FrontendAppConfig
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import controllers.auth.AuthFunction
import controllers.reg.ControllerErrorHandler
import models.Groups
import models.handoff.{BackHandoff, GroupHandBackModel}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, Result}
import services.{GroupService, HandBackService, HandOffService, NavModelNotFoundException}
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.auth.core.retrieve.Retrievals
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import utils._
import views.html.error_template_restart

import scala.concurrent.Future
import scala.util.{Failure, Success}

class GroupControllerImpl @Inject()(val authConnector: PlayAuthConnector,
                                    val keystoreConnector: KeystoreConnector,
                                    val handOffService: HandOffService,
                                    val appConfig: FrontendAppConfig,
                                    val compRegConnector: CompanyRegistrationConnector,
                                    val handBackService: HandBackService,
                                    val messagesApi: MessagesApi,
                                    val scrsFeatureSwitches: SCRSFeatureSwitches,
                                    val groupService: GroupService,
                                    val jwe: JweCommon) extends GroupController

trait GroupController extends FrontendController with AuthFunction with I18nSupport with SessionRegistration with ControllerErrorHandler {

  val handBackService: HandBackService
  val handOffService: HandOffService
  implicit val appConfig: FrontendAppConfig
  val scrsFeatureSwitches: SCRSFeatureSwitches
  val groupService: GroupService
  val jwe: JweCommon
  def pscEnabled = scrsFeatureSwitches.pscHandOff.enabled

  // 3.1 handback
  def groupHandBack(requestData: String): Action[AnyContent] = Action.async {
    implicit request =>
      ctAuthorisedHandoff("HO3-1", requestData) {
        registeredHandOff("HO3-1", requestData) { regID =>
          handBackService.processGroupsHandBack(requestData).flatMap {
              case Success(GroupHandBackModel(_, _, _, _, _, Some(true))) if !pscEnabled =>
                Future.successful(Redirect(controllers.handoff.routes.GroupController.PSCGroupHandOff()))
              case Success(GroupHandBackModel(_, _, _, _, _, Some(true))) if pscEnabled =>
                pscHandOffToGroupsIfDataInTxApi(regID)
              case Success(GroupHandBackModel(_, _, _, _, _, Some(false))) =>
                groupService.dropGroups(regID).map { _ =>
                  Redirect(controllers.handoff.routes.GroupController.PSCGroupHandOff)
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
          navModel    <- handOffService.fetchNavModel()
          backPayload <- handOffService.buildBackHandOff(externalID)
        } yield {
          val payload = jwe.encrypt[BackHandoff](backPayload).getOrElse("")
          Redirect(handOffService.buildHandOffUrl(s"${navModel.receiver.nav("3-1").reverse}", payload))
        }) recover {
          case ex: NavModelNotFoundException => Redirect(controllers.reg.routes.SignInOutController.postSignIn(None))
        }
      }
  }

  private[controllers] def pscHandOffToGroupsIfDataInTxApi(regID: String)(implicit hc:HeaderCarrier): Future[Result] = {
    groupService.potentiallyDropGroupsBasedOnReturnFromTXApiAndReturnList(regID).flatMap {
      case Nil => Future.successful(Redirect(controllers.handoff.routes.GroupController.PSCGroupHandOff()))
      case list => groupService.hasDataChangedIfSoDropGroups(list, regID).map { _ =>
        Redirect(controllers.groups.routes.GroupReliefController.show())
      }
    }
  }

  // 3.2 hand off
  val PSCGroupHandOff: Action[AnyContent] = Action.async {
    implicit request =>
      ctAuthorisedOptStr(Retrievals.externalId) { externalID =>
        registered { regId =>
          groupService.retrieveGroups(regId).flatMap { optGroups =>
            val featureSwitchDrivenGroups = optGroups.flatMap{ grps => if(pscEnabled) Some(grps) else None}
            handOffService.buildPSCPayload(regId, externalID, featureSwitchDrivenGroups) map {
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
        optGroups.fold(Redirect(controllers.reg.routes.SignInOutController.postSignIn())){groupsExist =>
          if(!groupsExist.groupRelief) {
            Redirect(controllers.groups.routes.GroupReliefController.show())
          } else {
            Redirect(controllers.groups.routes.GroupUtrController.show())
          }
        }
      }
      ctAuthorisedHandoff("HO3b-1", request) {
        registeredHandOff("HO3b-1", request) { regID =>
          handBackService.processGroupsHandBack(request).flatMap {
            case Success(_) => groupService.retrieveGroups(regID).map(groupsRedirect)
            case Failure(PayloadError) => Future.successful(BadRequest(error_template_restart("3b-1", "PayloadError")))
            case Failure(DecryptionError) => Future.successful(BadRequest(error_template_restart("3b-1", "DecryptionError")))
            case _ => Future.successful(InternalServerError(defaultErrorPage))
          }
        }
      }
  }
}