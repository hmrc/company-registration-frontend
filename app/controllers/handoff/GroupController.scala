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

package controllers.handoff

import config.AppConfig
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import controllers.auth.AuthenticatedController
import controllers.reg.ControllerErrorHandler

import javax.inject.Inject
import models.Groups
import models.handoff.{BackHandoff, GroupHandBackModel}
import play.api.i18n.{I18nSupport, Lang}
import play.api.libs.json.JsValue
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, MessagesRequest, Result}
import services.{GroupService, HandBackService, HandOffService, LanguageService, NavModelNotFoundException}
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.http.HeaderCarrier
import utils._
import views.html.error_template_restart

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class GroupController @Inject()(val authConnector: PlayAuthConnector,
                                val keystoreConnector: KeystoreConnector,
                                val handOffService: HandOffService,
                                val compRegConnector: CompanyRegistrationConnector,
                                val handBackService: HandBackService,
                                val groupService: GroupService,
                                val jwe: JweCommon,
                                val controllerComponents: MessagesControllerComponents,
                                val controllerErrorHandler: ControllerErrorHandler,
                                handOffUtils: HandOffUtils,
                                error_template_restart: error_template_restart,
                                languageService: LanguageService
                               )(implicit val appConfig: AppConfig, implicit val ec: ExecutionContext)
  extends AuthenticatedController with I18nSupport with SessionRegistration {

  // 3.1 handback
  def groupHandBack(requestData: String): Action[AnyContent] = Action.async {
    implicit request =>
      ctAuthorisedHandoff("HO3-1", requestData) {
        registeredHandOff("HO3-1", requestData) { regID =>
          handBackService.processGroupsHandBack(requestData).flatMap {
            case Success(GroupHandBackModel(_, _, _, _, lang, _ , Some(hasShareholders))) =>
              if (hasShareholders) pscHandOffToGroupsIfDataInTxApi(regID, lang) else pscHandOffToGroups(regID, lang)
            case _ =>
              Future.successful(BadRequest(error_template_restart("3-1", "PayloadError")))
          }
        }
      }
  }

  private[controllers] def pscHandOffToGroups(regID: String, updatedLanguage: String)(implicit hc: HeaderCarrier): Future[Result] =
    for {
      _ <- groupService.dropGroups(regID)
      lang = Lang(updatedLanguage)
      _ <- languageService.updateLanguage(regID, lang)
    } yield Redirect(controllers.handoff.routes.GroupController.PSCGroupHandOff).withLang(lang)

  private[controllers] def pscHandOffToGroupsIfDataInTxApi(regID: String, updatedLanguage: String)(implicit hc: HeaderCarrier): Future[Result] =
    for {
      txId <- groupService.fetchTxID(regID)
      eitherShareholders <- groupService.returnListOfShareholders(txId)
      shareholders <- groupService.dropOldGroups(eitherShareholders, regID)
      lang = Lang(updatedLanguage)
      _ <- languageService.updateLanguage(regID, lang)
    } yield shareholders match {
      case Nil => Redirect(controllers.handoff.routes.GroupController.PSCGroupHandOff).withLang(lang)
      case _ => Redirect(controllers.groups.routes.GroupReliefController.show).withLang(lang)
    }

  def back: Action[AnyContent] = Action.async {
    implicit request =>
      ctAuthorisedOptStr(Retrievals.externalId) { externalID =>
        (for {
          navModel <- handOffService.fetchNavModel()
          backPayload <- handOffService.buildBackHandOff(externalID, handOffUtils.getCurrentLang(request))
        } yield {
          val payload = jwe.encrypt[BackHandoff](backPayload).getOrElse("")
          Redirect(handOffService.buildHandOffUrl(s"${navModel.receiver.nav("3-1").reverse}", payload))
        }) recover {
          case ex: NavModelNotFoundException => Redirect(controllers.reg.routes.SignInOutController.postSignIn(None))
        }
      }
  }

  // 3.2 hand off
  val PSCGroupHandOff: Action[AnyContent] = Action.async {
    implicit request =>
      ctAuthorisedOptStr(Retrievals.externalId) { externalID =>
        registered { regId =>
          groupService.retrieveGroups(regId).flatMap { optGroups =>
            handOffService.buildPSCPayload(regId, externalID, optGroups, handOffUtils.getCurrentLang(request)) map {
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
      ctAuthorisedHandoff("HO3b-1", request) {
        registeredHandOff("HO3b-1", request) { regID =>
          handBackService.processGroupsHandBck(request).flatMap {
            case Success(payload) => pSCGroupHandBackSuccessRedirect(regID, payload)
            case Failure(PayloadError) => Future.successful(BadRequest(error_template_restart("3b-1", "PayloadError")))
            case Failure(DecryptionError) => Future.successful(BadRequest(error_template_restart("3b-1", "DecryptionError")))
            case _ => Future.successful(InternalServerError(controllerErrorHandler.defaultErrorPage))
          }
        }
      }
  }

  private[controllers] def pSCGroupHandBackSuccessRedirect(regId: String, payload: JsValue)(implicit hc: HeaderCarrier, request: MessagesRequest[AnyContent]): Future[Result] = {
    val lang = handOffUtils.readLang(payload)
    for {
      _ <- languageService.updateLanguage(regId, lang)
      optGroups <- groupService.retrieveGroups(regId)
    } yield optGroups match {
      case Some(groups) if groups.groupRelief =>
        Redirect(controllers.groups.routes.GroupUtrController.show).withLang(lang)
      case Some(_) =>
        Redirect(controllers.groups.routes.GroupReliefController.show).withLang(lang)
      case None =>
        Redirect(controllers.reg.routes.SignInOutController.postSignIn())
    }
  }
}