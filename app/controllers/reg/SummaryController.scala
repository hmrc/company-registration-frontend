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

package controllers.reg

import config.AppConfig
import connectors.{CompanyRegistrationConnector, KeystoreConnector, S4LConnector}
import controllers.auth.AuthenticatedController
import models._
import models.handoff.BackHandoff
import play.api.Logging
import play.api.i18n.I18nSupport
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repositories.NavModelRepo
import services._
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.auth.core.retrieve.Retrievals
import utils._
import views.html.reg.{Summary => SummaryView}
import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SummaryController @Inject()(val authConnector: PlayAuthConnector,
                                  val s4LConnector: S4LConnector,
                                  val compRegConnector: CompanyRegistrationConnector,
                                  val keystoreConnector: KeystoreConnector,
                                  val metaDataService: MetaDataService,
                                  val takeoverService: TakeoverService,
                                  val handOffService: HandOffService,
                                  val navModelRepo: NavModelRepo,
                                  val scrsFeatureSwitches: SCRSFeatureSwitches,
                                  val jwe: JweCommon,
                                  val controllerComponents: MessagesControllerComponents,
                                  val controllerErrorHandler: ControllerErrorHandler,
                                  view: SummaryView)
                                 (implicit val appConfig: AppConfig, implicit val ec: ExecutionContext)
  extends AuthenticatedController with CommonService with SCRSExceptions
    with SessionRegistration with I18nSupport with Logging {
  lazy val navModelMongo = navModelRepo.repository


  val show: Action[AnyContent] = Action.async {
    implicit request =>
      ctAuthorised {
        checkStatus { regID =>
          (for {
            cc <- metaDataService.getApplicantData(regID)
            accountingDates <- compRegConnector.retrieveAccountingDetails(regID)
            ctContactDets <- compRegConnector.retrieveContactDetails(regID)
            companyDetails <- compRegConnector.retrieveCompanyDetails(regID)
            optTakeoverDetails <- takeoverService.getTakeoverDetails(regID)
            Some(tradingDetails) <- compRegConnector.retrieveTradingDetails(regID)
            cTRecord <- compRegConnector.retrieveCorporationTaxRegistration(regID)
          } yield {
            companyDetails match {
              case Some(details) =>
                val ppobAddress = extractPPOB(details.pPOBAddress)
                val rOAddress = details.cHROAddress
                val ctContactDetails = extractContactDetails(ctContactDets)
                val accountDates: AccountingDetails = accountingDates match {
                  case AccountingDetailsSuccessResponse(response) => response
                  case _ => throw new Exception("could not find company accounting details")
                }
                optTakeoverDetails match {
                  case Some(TakeoverDetails(true, Some(_), Some(_), Some(_), Some(_))) =>
                    Ok(view(details.companyName, details.jurisdiction, accountDates, ppobAddress, rOAddress, ctContactDetails, tradingDetails, optTakeoverDetails, cc))
                  case Some(TakeoverDetails(true, _, _, _, _)) =>
                    Redirect(controllers.takeovers.routes.TakeoverInformationNeededController.show)
                  case None | Some(TakeoverDetails(false, None, None, None, None)) =>
                    Ok(view(details.companyName, details.jurisdiction, accountDates, ppobAddress, rOAddress, ctContactDetails, tradingDetails, optTakeoverDetails, cc))
                  case Some(TakeoverDetails(false, _, _, _, _)) =>
                    Redirect(controllers.takeovers.routes.TakeoverInformationNeededController.show)
                  case _ =>
                    logger.error("[SummaryController] [show] Takeover details in unexpected state")
                    InternalServerError(controllerErrorHandler.defaultErrorPage)
                }
              case _ =>
                logger.error(s"[SummaryController] [show] Could not find company details for reg ID : $regID - suspected direct routing to summary page")
                InternalServerError(controllerErrorHandler.defaultErrorPage)
            }
          }) recover {
            case ex: Throwable =>
              logger.error(s"[SummaryController] [show] Error occurred while loading the summary page - ${ex.getMessage}")
              InternalServerError(controllerErrorHandler.defaultErrorPage)
          }
        }
      }
  }


  val submit: Action[AnyContent] = Action.async {
    implicit request =>
      ctAuthorised {
        Future.successful(Redirect(controllers.handoff.routes.IncorporationSummaryController.incorporationSummary))
      }
  }

  def back: Action[AnyContent] = Action.async {
    implicit request =>
      ctAuthorisedOptStr(Retrievals.externalId) { externalID =>
        (for {
          _ <- fetchRegistrationID
          navModel <- handOffService.fetchNavModel()
          backPayload <- handOffService.buildBackHandOff(externalID)
        } yield {
          val payload = jwe.encrypt[BackHandoff](backPayload).getOrElse("")
          Redirect(handOffService.buildHandOffUrl(s"${navModel.receiver.nav("4").reverse}", payload))
        }) recover {
          case ex: NavModelNotFoundException => Redirect(controllers.reg.routes.SignInOutController.postSignIn(None))
        }
      }
  }

  def summaryBackLink(backKey: String): Action[AnyContent] = Action.async {
    implicit request =>
      ctAuthorisedOptStr(Retrievals.externalId) { externalID =>
        (for {
          journeyID <- fetchRegistrationID
          navModel <- handOffService.fetchNavModel()
        } yield {
          val payload = Json.obj(
            "user_id" -> externalID,
            "journey_id" -> journeyID,
            "hmrc" -> Json.obj(),
            "ch" -> navModel.receiver.chData,
            "links" -> Json.obj()
          )
          val url = navModel.receiver.jump(backKey)
          val encryptedPayload = jwe.encrypt[JsObject](payload).get
          Redirect(handOffService.buildHandOffUrl(url, encryptedPayload))
        }) recover {
          case ex: NavModelNotFoundException => Redirect(controllers.reg.routes.SignInOutController.postSignIn(None))
        }
      }
  }

  def extractPPOB(pPOBAddress: PPOB): PPOBModel = {
    PPOBModel(pPOBAddress, addressChoice = "")
  }

  def extractContactDetails(companyContactDetailsResponse: CompanyContactDetailsResponse): CompanyContactDetailsApi = {
    companyContactDetailsResponse match {
      case CompanyContactDetailsSuccessResponse(response) => CompanyContactDetails.toApiModel(response)
      case _ =>
        logger.error(s"[SummaryController] [extractContactDetails] Could not find company details - suspected direct routing to summary page")
        throw new Exception("could not find company contact details - suspected direct routing to summary page")
    }
  }
}
