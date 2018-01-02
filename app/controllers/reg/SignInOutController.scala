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

import config.{FrontendAppConfig, FrontendAuthConnector, FrontendConfig}
import connectors._
import controllers.auth.{SCRSExternalUrls, SCRSRegime}
import models._
import play.api.Logger
import play.api.mvc.{Action, AnyContent, Request, Result}
import services._
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.frontend.auth.{Actions, AuthContext}
import uk.gov.hmrc.play.frontend.controller.FrontendController
import utils.SCRSExceptions
import java.io.File

import controllers.verification.{routes => emailRoutes}
import play.api.i18n.Messages.Implicits._
import play.api.Play.current
import uk.gov.hmrc.play.binders.ContinueUrl
import controllers.handoff.{routes => handoffRoutes}

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

object SignInOutController extends SignInOutController with ServicesConfig{
  val authConnector = FrontendAuthConnector
  val compRegConnector = CompanyRegistrationConnector
  val handOffService = HandOffService
  val emailService = EmailVerificationService
  val enrolmentsService = EnrolmentsService
  val metrics = MetricsService
  val cRFEBaseUrl = FrontendConfig.self
  val keystoreConnector = KeystoreConnector
  val corsRenewHost = FrontendAppConfig.corsRenewHost
}

trait SignInOutController extends FrontendController with Actions with ControllerErrorHandler with CommonService with SCRSExceptions {

  val compRegConnector : CompanyRegistrationConnector
  val handOffService : HandOffService
  val emailService : EmailVerificationService
  val enrolmentsService : EnrolmentsService
  val metrics : MetricsService
  val cRFEBaseUrl: String
  val keystoreConnector: KeystoreConnector
  val corsRenewHost: Option[String]

  def postSignIn(resend: Option[Boolean], handOffID: Option[String] = None, payload: Option[String] = None) =
    AuthorisedFor(taxRegime = SCRSRegime("post-sign-in"), pageVisibility = GGConfidence).async {
      implicit user =>
        implicit request =>
          hasOrgAffinity {
            hasFootprint { response =>
              whenRegistrationIsDraft(response) {
                checkHO5Progress(response) {
                  processDeferredHandoff(handOffID, payload, response) {
                    hasNoEnrolments {
                      emailService.isVerified(response.registrationId, response.emailData, resend) flatMap {
                        case (Some(true), Some(verifiedEmail)) =>
                          for {
                            a <- handOffService.cacheRegistrationID(response.registrationId)
                            b <- emailService.sendWelcomeEmail(response.registrationId, verifiedEmail)}
                            yield {
                              Redirect(routes.CompletionCapacityController.show())
                            }
                        case (Some(false), _) => Future.successful(Redirect(controllers.verification.routes.EmailVerificationController.verifyShow()))
                        case (None, _) => Future.successful(Redirect(controllers.verification.routes.EmailVerificationController.createShow()))
                        case _ => Future.successful(InternalServerError(defaultErrorPage))
                      } recover {
                        case ex: Throwable =>
                          Logger.error(s"[SignInOutController] [postSignIn] error occurred during post sign in - ${ex.getMessage}")
                          BadRequest(defaultErrorPage)
                      }
                    }
                  }
                }
              }
            }
          }
  }

  private def whenRegistrationIsDraft(throttleResponse: ThrottleResponse)(f: => Future[Result])
                                     (implicit hc : HeaderCarrier, user : AuthContext, req: Request[_]) : Future[Result] = {
    compRegConnector.fetchRegistrationStatus(throttleResponse.registrationId) flatMap {
      case Some("draft") => f
      case Some("locked") => redirectToHo1WithCachedRegistrationId(throttleResponse.registrationId)
      case Some("held") if !throttleResponse.paymentRefs => redirectToHo1WithCachedRegistrationId(throttleResponse.registrationId)
      case Some(_) => handOffService.cacheRegistrationID(throttleResponse.registrationId) map {
        _ => Redirect(controllers.dashboard.routes.DashboardController.show())
      }
      case _ => f
    } recover {
      case _: Exception => InternalServerError(defaultErrorPage)
    }
  }

  private def redirectToHo1WithCachedRegistrationId(regid: String)(implicit hc: HeaderCarrier, request : Request[_]) = handOffService.cacheRegistrationID(regid) map { _ =>
    Redirect(handoffRoutes.BasicCompanyDetailsController.basicCompanyDetails())
  }

  private def hasFootprint(f: ThrottleResponse => Future[Result])(implicit hc: HeaderCarrier, request : Request[AnyContent]) : Future[Result] = {
    val context = metrics.saveFootprintToCRTimer.time()
    compRegConnector.retrieveOrCreateFootprint flatMap {
      case FootprintFound(throttle) =>
        context.stop()
        f(throttle)
      case FootprintTooManyRequestsResponse =>
        context.stop()
        Future.successful(Redirect(routes.LimitReachedController.show()))
      case FootprintForbiddenResponse =>
        context.stop()
        Logger.error(s"[SignInOutController] [postSignIn] - retrieveOrCreateFootprint returned FootprintForbiddenResponse")
        Future.successful(Forbidden(defaultErrorPage))
      case FootprintErrorResponse(ex) =>
        context.stop()
        Logger.error(s"[SignInOutController] [postSignIn] - retrieveOrCreateFootprint returned FootprintErrorResponse($ex)")
        Future.successful(InternalServerError(defaultErrorPage))
    }
  }

  private def checkHO5Progress(throttle: ThrottleResponse)(f: => Future[Result])(implicit hc: HeaderCarrier) = {
    import constants.RegistrationProgressValues.HO5
    throttle.registrationProgress match {
      case Some(HO5) => cacheRegistrationID(throttle.registrationId).map { _ =>
        Redirect(controllers.handoff.routes.BasicCompanyDetailsController.basicCompanyDetails())
      }
      case Some(_) => f
      case None => f
    }
  }

  private[controllers] def processDeferredHandoff(optHandOffID: Option[String], optPayload: Option[String], throttleResponse: ThrottleResponse)(f: => Future[Result])
                                    (implicit hc: HeaderCarrier): Future[Result] = {

    def generateHandOffUrl(handOffID: String, payload: String): String = {
      import controllers.handoff.routes._
      Map(
        "HO1b" -> BasicCompanyDetailsController.returnToAboutYou(payload).url,
        "HO2" -> CorporationTaxDetailsController.corporationTaxDetails(payload).url,
        "HO3b" -> BusinessActivitiesController.businessActivitiesBack(payload).url,
        "HO4" -> CorporationTaxSummaryController.corporationTaxSummary(payload).url,
        "HO5b" -> IncorporationSummaryController.returnToCorporationTaxSummary(payload).url
      ).mapValues(url => s"${FrontendConfig.self}$url")(handOffID)
    }

    (optHandOffID, optPayload) match {
      case (Some(id), Some(p)) =>
        cacheRegistrationID(throttleResponse.registrationId) map (_ => Redirect(generateHandOffUrl(id, p)))
      case _ => f
    }
  }

  private def hasOrgAffinity(f: => Future[Result])(implicit hc: HeaderCarrier, authContext: AuthContext):Future[Result]={
    authConnector.getUserDetails[UserDetailsModel](authContext) flatMap {
      _.affinityGroup match {
        case "Organisation" => f
        case _ => Future.successful(Redirect(emailRoutes.EmailVerificationController.createGGWAccountAffinityShow()))
      }
    }
  }

  private def hasNoEnrolments(f: => Future[Result])(implicit hc : HeaderCarrier, authContext: AuthContext) : Future[Result] = {
    enrolmentsService.hasBannedRegimes(authContext) flatMap {
      case true =>
        Logger.warn("[SignInOutController][postSignIn] Throttle was incremented but user was blocked due to existing enrolments")
        metrics.blockedByEnrollment.inc(1)
        Future.successful(Redirect(emailRoutes.EmailVerificationController.createNewGGWAccountShow()))
      case false => f
    }
  }

  def signOut(continueUrl: Option[ContinueUrl] = None) = Action.async {
    implicit request =>
      val c = continueUrl match {
        case Some(str) => str.url
        case None => s"$cRFEBaseUrl${controllers.reg.routes.QuestionnaireController.show().url}"
      }
      Future.successful(Redirect(SCRSExternalUrls.logoutURL, Map("continue" -> Seq(c))))
  }

  def renewSession: Action[AnyContent] = AuthorisedFor(taxRegime = SCRSRegime("post-sign-in"), pageVisibility = GGConfidence) {
    implicit user =>
      implicit request =>
        type Headers = Seq[Tuple2[String, String]]
        val headers = corsRenewHost.fold[Headers](Seq()){ host =>
          Seq(
            "Access-Control-Allow-Origin" -> host,
            "Access-Control-Allow-Credentials" -> "true"
          )
        }
        Ok.sendFile(new File(("conf/renewSession.jpg"))).as("image/jpeg").withHeaders(headers:_*)
  }

  def destroySession: Action[AnyContent] = Action {
    Redirect(routes.SignInOutController.timeoutShow()).withNewSession
  }

  def timeoutShow = Action.async {
    implicit request =>
      Future.successful(Ok(views.html.timeout()))
  }
}
