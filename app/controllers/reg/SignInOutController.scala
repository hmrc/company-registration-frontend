/*
 * Copyright 2023 HM Revenue & Customs
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

import java.io.File
import config.AppConfig
import connectors._
import controllers.auth.AuthenticatedController
import controllers.handoff.{routes => handoffRoutes}
import controllers.verification.{routes => emailRoutes}

import javax.inject.Inject
import models.ThrottleResponse
import utils.Logging
import play.api.i18n.I18nSupport
import play.api.mvc._
import services._
import uk.gov.hmrc.auth.core.{AffinityGroup, Enrolments, PlayAuthConnector}
import uk.gov.hmrc.http.{HeaderCarrier, SessionKeys}
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl
import utils.SCRSExceptions
import views.html.{timeout => timeoutView}

import scala.concurrent.{ExecutionContext, Future}

class SignInOutController @Inject()(val authConnector: PlayAuthConnector,
                                    val compRegConnector: CompanyRegistrationConnector,
                                    val handOffService: HandOffService,
                                    val emailService: EmailVerificationService,
                                    val metrics: MetricsService,
                                    val keystoreConnector: KeystoreConnector,
                                    val enrolmentsService: EnrolmentsService,
                                    val controllerErrorHandler: ControllerErrorHandler,
                                    val controllerComponents: MessagesControllerComponents,
                                    timeoutView: timeoutView)(implicit val appConfig: AppConfig, implicit val ec: ExecutionContext)
  extends AuthenticatedController with CommonService with SCRSExceptions with I18nSupport with Logging {

  lazy val cRFEBaseUrl: String = appConfig.self
  lazy val corsRenewHost: Option[String] = appConfig.corsRenewHost

  def postSignIn(resend: Option[Boolean] = None, handOffID: Option[String] = None, payload: Option[String] = None): Action[AnyContent] = Action.async {
    implicit request =>
      ctAuthorisedPostSignIn { authDetails =>
        hasOrgAffinity(authDetails.affinityGroup) {
          hasFootprint { response =>
            whenRegistrationIsDraft(response) {
              checkHO5Progress(response) {
                processDeferredHandoff(handOffID, payload, response) {
                  hasNoEnrolments(authDetails.enrolments) {
                    emailService.checkEmailStatus(response.registrationId, response.emailData, authDetails) map {
                      case VerifiedEmail() => Redirect(routes.CompletionCapacityController.show)
                      case NotVerifiedEmail() => Redirect(routes.RegistrationEmailController.show)
                      case NoEmail() => Redirect(controllers.verification.routes.EmailVerificationController.createShow)
                      case _ => InternalServerError(controllerErrorHandler.defaultErrorPage)

                    } recover {
                      case ex: Throwable =>
                        logger.error(s"[postSignIn] error occurred during post sign in - ${ex.getMessage}")
                        BadRequest(controllerErrorHandler.defaultErrorPage)
                    }
                  }
                }
              }
            }
          }
        }
      }
  }

  private def whenRegistrationIsDraft(throttleResponse: ThrottleResponse)(f: => Future[Result])
                                     (implicit hc: HeaderCarrier, req: Request[_]): Future[Result] = {
    compRegConnector.fetchRegistrationStatus(throttleResponse.registrationId) flatMap {
      case Some("draft") => f
      case Some("locked") => redirectToHo1WithCachedRegistrationId(throttleResponse.registrationId)
      case Some("held") if !throttleResponse.paymentRefs => redirectToHo1WithCachedRegistrationId(throttleResponse.registrationId)
      case Some(_) => handOffService.cacheRegistrationID(throttleResponse.registrationId) map {
        _ => Redirect(controllers.dashboard.routes.DashboardController.show)
      }
      case _ => f
    } recover {
      case _: Exception => InternalServerError(controllerErrorHandler.defaultErrorPage)
    }
  }


  private def redirectToHo1WithCachedRegistrationId(regid: String)(implicit hc: HeaderCarrier, request: Request[_]) = handOffService.cacheRegistrationID(regid) map { _ =>
    Redirect(handoffRoutes.BasicCompanyDetailsController.basicCompanyDetails)
  }

  private def hasFootprint(f: ThrottleResponse => Future[Result])(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {
    val context = metrics.saveFootprintToCRTimer.time()
    compRegConnector.retrieveOrCreateFootprint() flatMap {
      case FootprintFound(throttle) =>
        context.stop()
        f(throttle)
      case FootprintTooManyRequestsResponse =>
        context.stop()
        Future.successful(Redirect(routes.LimitReachedController.show))
      case FootprintForbiddenResponse =>
        context.stop()
        logger.error(s"[postSignIn] retrieveOrCreateFootprint returned FootprintForbiddenResponse")
        Future.successful(Forbidden(controllerErrorHandler.defaultErrorPage))
      case FootprintErrorResponse(ex) =>
        context.stop()
        logger.error(s"[postSignIn] retrieveOrCreateFootprint returned FootprintErrorResponse($ex)")
        Future.successful(InternalServerError(controllerErrorHandler.defaultErrorPage))
    }
  }

  private def checkHO5Progress(throttle: ThrottleResponse)(f: => Future[Result])(implicit hc: HeaderCarrier) = {
    import constants.RegistrationProgressValues.HO5
    throttle.registrationProgress match {
      case Some(HO5) => cacheRegistrationID(throttle.registrationId).map { _ =>
        Redirect(controllers.handoff.routes.BasicCompanyDetailsController.basicCompanyDetails)
      }
      case Some(_) => f
      case None => f
    }
  }

  private[controllers] def processDeferredHandoff(optHandOffID: Option[String], optPayload: Option[String], throttleResponse: ThrottleResponse)(f: => Future[Result])
                                                 (implicit hc: HeaderCarrier): Future[Result] = {

    def generateHandOffUrl(handOffID: String, payload: String): String = {
      Map(
        "HO1b" -> controllers.handoff.routes.BasicCompanyDetailsController.returnToAboutYou(payload).url,
        "HO2" -> controllers.handoff.routes.CorporationTaxDetailsController.corporationTaxDetails(payload).url,
        "HO3b" -> controllers.handoff.routes.BusinessActivitiesController.businessActivitiesBack(payload).url,
        "HO3-1" -> controllers.handoff.routes.GroupController.groupHandBack(payload).url,
        "HO3b-1" -> controllers.handoff.routes.GroupController.pSCGroupHandBack(payload).url,
        "HO4" -> controllers.handoff.routes.CorporationTaxSummaryController.corporationTaxSummary(payload).url,
        "HO5b" -> controllers.handoff.routes.IncorporationSummaryController.returnToCorporationTaxSummary(payload).url
      ).mapValues(url => s"${appConfig.self}$url")(handOffID)
    }

    (optHandOffID, optPayload) match {
      case (Some(id), Some(p)) =>
        cacheRegistrationID(throttleResponse.registrationId) map (_ => Redirect(generateHandOffUrl(id, p)))
      case _ => f
    }
  }

  private def hasOrgAffinity(orgAffinity: AffinityGroup)(f: => Future[Result])(implicit hc: HeaderCarrier): Future[Result] = {
    orgAffinity match {
      case AffinityGroup.Organisation => f
      case _ => Future.successful(Redirect(emailRoutes.EmailVerificationController.createGGWAccountAffinityShow))
    }
  }

  private def hasNoEnrolments(enrolments: Enrolments)(f: => Future[Result])(implicit hc: HeaderCarrier): Future[Result] = {
    if (enrolmentsService.hasBannedRegimes(enrolments)) {
      logger.warn("[postSignIn] Throttle was incremented but user was blocked due to existing enrolments")
      metrics.blockedByEnrollment.inc(1)
      Future.successful(Redirect(emailRoutes.EmailVerificationController.createNewGGWAccountShow))
    } else f
  }

  def signOut(continueUrl: Option[RedirectUrl] = None): Action[AnyContent] = Action.async {
    implicit request =>
      val c = continueUrl match {
        case Some(str) => str.unsafeValue
        case None => appConfig.betaFeedbackUrl
      }
      Future.successful(Redirect(appConfig.logoutURL, Map("continue" -> Seq(c))))
  }

  def renewSession: Action[AnyContent] = Action.async {
    implicit request => {
      val optHcAuth = hc.authorization
      val optSessionAuthToken = request.session.get(SessionKeys.authToken)
      logger.debug(s"[renewSession] mdtp cookie present? ${request.cookies.get("mdtp").isDefined}")
      (optHcAuth, optSessionAuthToken) match {
        case (Some(hcAuth), Some(sAuth)) => if (hcAuth.value == sAuth) {
          logger.debug("[renewSession] hcAuth and session auth present and equal")
        }
        else {
          logger.warn("[renewSession] hcAuth and session auth present but not equal")
        }
        case (Some(_), None) => logger.debug("[renewSession] hcAuth present, session auth not")
        case (None, Some(_)) => logger.debug("[renewSession] session auth present, hcAuth auth not")
        case _ => logger.debug("[renewSession] neither session auth or hcAuth present")
      }
      ctAuthorised {
        type Headers = Seq[Tuple2[String, String]]
        val headers = corsRenewHost.fold[Headers](Seq()) { host =>
          Seq(
            "Access-Control-Allow-Origin" -> host,
            "Access-Control-Allow-Credentials" -> "true"
          )
        }
        updateLastActionTimestamp() map { _ =>
          Ok.sendFile(new File("conf/renewSession.jpg")).as("image/jpeg").withHeaders(headers: _*)
        }
      }
    }
  }

  def destroySession: Action[AnyContent] = Action {
    Redirect(routes.SignInOutController.timeoutShow).withNewSession
  }

  def timeoutShow: Action[AnyContent] = Action.async {
    implicit request =>
      Future.successful(Ok(timeoutView()))
  }
}