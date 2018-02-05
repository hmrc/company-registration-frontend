/*
 * Copyright 2018 HM Revenue & Customs
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

import config.FrontendAuthConnector
import connectors.{CompanyRegistrationConnector, KeystoreConnector, S4LConnector}
import controllers.auth.SCRSRegime
import models._
import models.handoff.BackHandoff
import play.api.Logger
import play.api.libs.json.{JsObject, Json}
import services._
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.frontend.auth.Actions
import uk.gov.hmrc.play.frontend.controller.FrontendController
import utils.{Jwe, MessagesSupport, SCRSExceptions, SessionRegistration}
import views.html.reg.Summary
import repositories.NavModelRepo

import scala.concurrent.Future

object SummaryController extends SummaryController {
  val authConnector = FrontendAuthConnector
  val s4LConnector = S4LConnector
  val companyRegistrationConnector = CompanyRegistrationConnector
  val keystoreConnector = KeystoreConnector
  val metaDataService = MetaDataService
  val handOffService = HandOffServiceImpl
  val navModelMongo =  NavModelRepo.repository
}

trait SummaryController extends FrontendController with Actions with CommonService with SCRSExceptions
  with HandOffNavigator with ServicesConfig with ControllerErrorHandler
  with SessionRegistration with MessagesSupport {

  val s4LConnector : S4LConnector
  val companyRegistrationConnector : CompanyRegistrationConnector

  val metaDataService : MetaDataService
  val handOffService : HandOffService

  val show = AuthorisedFor(taxRegime = SCRSRegime("first-hand-off"), pageVisibility = GGConfidence).async {
    implicit user =>
      implicit request =>
        checkStatus{regID =>
        (for {
          cc <- metaDataService.getApplicantData(regID)
          accountingDates <- companyRegistrationConnector.retrieveAccountingDetails(regID)
          ctContactDets <- companyRegistrationConnector.retrieveContactDetails(regID)
          companyDetails <-companyRegistrationConnector.retrieveCompanyDetails(regID)
          Some(tradingDetails) <- companyRegistrationConnector.retrieveTradingDetails(regID)
          cTRecord <- companyRegistrationConnector.retrieveCorporationTaxRegistration(regID)
        } yield {
          companyDetails match {
            case Some(details) =>
              val ppobAddress = extractPPOB(details.pPOBAddress)
              val rOAddress = details.cHROAddress
              val ctContactDetails = extractContactDetails(ctContactDets)
              val accountDates : AccountingDetails = accountingDates match {
                case AccountingDetailsSuccessResponse(response) => response
                case _ => throw new Exception ("could not find company accounting details")
              }
              Ok(Summary(details.companyName, details.jurisdiction, accountDates, ppobAddress, rOAddress, ctContactDetails, tradingDetails, cc))
            case _ =>
              Logger.error(s"[SummaryController] [show] Could not find company details for reg ID : $regID - suspected direct routing to summary page")
              InternalServerError(defaultErrorPage)
          }
        }) recover {
          case ex: Throwable =>
            Logger.error(s"[SummaryController] [show] Error occurred while loading the summary page - ${ex.getMessage}")
            InternalServerError(defaultErrorPage)
        }
    }
  }


  val submit = AuthorisedFor(taxRegime = SCRSRegime("first-hand-off"), pageVisibility = GGConfidence).async {
    implicit user =>
      implicit request =>
        Future.successful(Redirect(controllers.handoff.routes.IncorporationSummaryController.incorporationSummary()))
  }

  def back = AuthorisedFor(taxRegime = SCRSRegime("first-hand-off"), pageVisibility = GGConfidence).async {
    implicit user =>
      implicit request =>
        (for {
          journey_id <- fetchRegistrationID
          user_id <- handOffService.externalUserId
          navModel <- fetchNavModel()
          backPayload <- handOffService.buildBackHandOff
        } yield {
          val payload = Jwe.encrypt[BackHandoff](backPayload).getOrElse("")
          Redirect(handOffService.buildHandOffUrl(s"${navModel.receiver.nav("4").reverse}", payload))
        }) recover{
    case ex: NavModelNotFoundException => Redirect(controllers.reg.routes.SignInOutController.postSignIn(None))}
  }

  def summaryBackLink(backKey: String) = AuthorisedFor(taxRegime = SCRSRegime("first-hand-off"), pageVisibility = GGConfidence).async {
    implicit user =>
      implicit request =>

        (for{
          userID <- handOffService.externalUserId
          journeyID <- fetchRegistrationID
          navModel <- fetchNavModel()
        } yield {
          val payload = Json.obj(
            "user_id" -> userID,
            "journey_id" -> journeyID,
            "hmrc" -> Json.obj(),
            "ch" -> navModel.receiver.chData,
            "links" -> Json.obj()
          )
          val url = navModel.receiver.jump(backKey)
          val encryptedPayload = Jwe.encrypt[JsObject](payload).get
          Redirect(handOffService.buildHandOffUrl(url, encryptedPayload))
        }) recover{
          case ex: NavModelNotFoundException => Redirect(controllers.reg.routes.SignInOutController.postSignIn(None))}
  }

  def extractPPOB(pPOBAddress: PPOB): PPOBModel = {
    PPOBModel(pPOBAddress, addressChoice = "")
  }

  def extractContactDetails (companyContactDetailsResponse: CompanyContactDetailsResponse) = {
    companyContactDetailsResponse match {
      case CompanyContactDetailsSuccessResponse(response) => response
      case _ =>
        Logger.error(s"[SummaryController] [extractContactDetails] Could not find company details - suspected direct routing to summary page")
        throw new Exception ("could not find company contact details - suspected direct routing to summary page")
    }
  }
}
