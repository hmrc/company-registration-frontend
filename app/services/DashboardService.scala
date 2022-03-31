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

package services

import javax.inject.Inject
import _root_.connectors._
import audit.events.{EmailMismatchEvent, EmailMismatchEventDetail}
import config.AppConfig
import models._
import models.auth.AuthDetails
import models.external.{OtherRegStatus, Statuses}
import org.joda.time.{DateTime, LocalDate}
import play.api.libs.json.JsValue
import play.api.mvc.{AnyContent, Call, Request}
import uk.gov.hmrc.auth.core.Enrolments
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import utils._
import play.api.libs.json.JodaReads._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NoStackTrace
import scala.util.{Failure, Success, Try}

class DashboardServiceImpl @Inject()(val keystoreConnector: KeystoreConnector,
                                     val companyRegistrationConnector: CompanyRegistrationConnector,
                                     val incorpInfoConnector: IncorpInfoConnector,
                                     val payeConnector: PAYEConnector,
                                     val vatConnector: VATConnector,
                                     val auditConnector: AuditConnector,
                                     val featureFlag: SCRSFeatureSwitches,
                                     val thresholdService: ThresholdService,
                                     val appConfig: AppConfig
                                    ) extends DashboardService {


  lazy val otrsUrl         = appConfig.servicesConfig.getConfString("otrs.url", throw new Exception("Could not find config for key: otrs.url"))
  lazy val payeBaseUrl     = appConfig.servicesConfig.getConfString("paye-registration-www.url-prefix", throw new Exception("Could not find config for key: paye-registration-www.url-prefix"))
  lazy val payeUri         = appConfig.servicesConfig.getConfString("paye-registration-www.start-url", throw new Exception("Could not find config for key: paye-registration-www.start-url"))
  lazy val vatBaseUrl      = appConfig.servicesConfig.getConfString("vat-registration-www.url-prefix", throw new Exception("Could not find config for key: vat-registration-www.url-prefix"))
  lazy val vatUri          = appConfig.servicesConfig.getConfString("vat-registration-www.start-url", throw new Exception("Could not find config for key: vat-registration-www.start-url"))

  lazy val loggingDays     = appConfig.servicesConfig.getConfString("alert-config.logging-day", throw new Exception("Could not find config key: LoggingDay"))
  lazy val loggingTimes    = appConfig.servicesConfig.getConfString("alert-config.logging-time", throw new Exception("Could not find config key: LoggingTime"))
}

sealed trait DashboardStatus
case class DashboardBuilt(d: Dashboard) extends DashboardStatus
case object RejectedIncorp extends DashboardStatus
case object CouldNotBuild extends DashboardStatus

class ConfirmationRefsNotFoundException extends NoStackTrace

trait DashboardService extends SCRSExceptions with AlertLogging with CommonService {

  import scala.language.implicitConversions

  val companyRegistrationConnector: CompanyRegistrationConnector
  val payeConnector, vatConnector: ServiceConnector
  val incorpInfoConnector: IncorpInfoConnector
  val auditConnector: AuditConnector
  val thresholdService: ThresholdService
  val otrsUrl: String
  val payeBaseUrl: String
  val payeUri: String
  val vatBaseUrl: String
  val vatUri: String
  val featureFlag: SCRSFeatureSwitches
  val appConfig: AppConfig

  def toDashboard(s: OtherRegStatus, thresholds: Option[Map[String, Int]])(implicit startURL: String, cancelURL: Call): ServiceDashboard = {
    ServiceDashboard(
      s.status,
      s.lastUpdate.map(_.toString("d MMMM yyyy")),
      s.ackRef,
      ServiceLinks(
        startURL,
        otrsUrl,
        s.restartURL,
        s.cancelURL.map(_ => cancelURL.url)
      ),
      thresholds
    )
  }

  def buildDashboard(regId: String, enrolments: Enrolments)(implicit hc: HeaderCarrier): Future[DashboardStatus] = {
    for {
      incorpCTDash <- buildIncorpCTDashComponent(regId, enrolments)
      payeDash <- buildPAYEDashComponent(regId, enrolments)
      hasVatCred = hasEnrolment(enrolments, List(appConfig.HMCE_VATDEC_ORG, appConfig.HMCE_VATVAR_ORG))
      vatDash <- buildVATDashComponent(regId, enrolments)
    } yield {
      incorpCTDash.status match {
        case "draft" => CouldNotBuild
        case "rejected" => RejectedIncorp
        case _ => DashboardBuilt(Dashboard("", incorpCTDash, payeDash, vatDash, hasVatCred, featureFlag.vat.enabled)) //todo: leaving company name blank until story gets played to add it back
      }
    }
  }

  private[services] def hasEnrolment(authEnrolments: Enrolments, enrolmentKeys: Seq[String])(implicit hc: HeaderCarrier): Boolean = {
    authEnrolments.enrolments.exists(e => enrolmentKeys.contains(e.key))
  }

  private[services] def statusToServiceDashboard(res: Future[StatusResponse], enrolments: Enrolments, payeEnrolments: Seq[String], thresholds: Option[Map[String, Int]])
                                                (implicit hc: HeaderCarrier, startURL: String, cancelURL: Call): Future[ServiceDashboard] = {
    res map {
      case SuccessfulResponse(status) => toDashboard(status, thresholds)
      case ErrorResponse => toDashboard(OtherRegStatus(Statuses.UNAVAILABLE, None, None, None, None), thresholds)
      case NotStarted => toDashboard(
        OtherRegStatus(
          if (hasEnrolment(enrolments, payeEnrolments)) Statuses.NOT_ELIGIBLE else Statuses.NOT_STARTED, None, None, None, None
        ),
        thresholds
      )
    }
  }

  private[services] def buildPAYEDashComponent(regId: String, enrolments: Enrolments)(implicit hc: HeaderCarrier): Future[ServiceDashboard] = {
    implicit val startURL: String = s"$payeBaseUrl$payeUri"
    implicit val cancelURL: Call = controllers.dashboard.routes.CancelRegistrationController.showCancelPAYE

    if (featureFlag.paye.enabled) {
      statusToServiceDashboard(payeConnector.getStatus(regId), enrolments, List(appConfig.IR_PAYE), Some(getCurrentPayeThresholds))
    } else {
      Future.successful(toDashboard(OtherRegStatus(Statuses.NOT_ENABLED, None, None, None, None), None))
    }
  }

  def getCurrentPayeThresholds: Map[String, Int] = thresholdService.fetchCurrentPayeThresholds()

  private[services] def buildVATDashComponent(regId: String, enrolments: Enrolments)(implicit hc: HeaderCarrier): Future[ServiceDashboard] = {
    implicit val startURL: String = s"$vatBaseUrl$vatUri"
    implicit val cancelURL: Call = controllers.dashboard.routes.CancelRegistrationController.showCancelVAT

    getCurrentVatThreshold flatMap { threshold =>
      Try(threshold.toInt) match {
        case Success(intThreshold) => if (featureFlag.vat.enabled) {
        statusToServiceDashboard(vatConnector.getStatus(regId), enrolments, List(appConfig.HMCE_VATDEC_ORG,appConfig.HMCE_VATVAR_ORG), Some(Map("yearly" -> intThreshold)))
      } else {
          Future.successful(toDashboard(OtherRegStatus(Statuses.NOT_ENABLED, None, None, None, None), Some(Map("yearly" -> intThreshold))))
        }
        case Failure(ex) => throw new Exception(s"Value from Vat Reg Threshold not an int for regid: ${regId} Exception was $ex")
      }
    }
  }

  private[services] def getCurrentVatThreshold(implicit hc: HeaderCarrier): Future[String] =
    thresholdService.fetchCurrentVatThreshold

  private[services] def buildIncorpCTDashComponent(regId: String, enrolments : Enrolments)(implicit hc: HeaderCarrier): Future[IncorpAndCTDashboard] = {
    companyRegistrationConnector.retrieveCorporationTaxRegistration(regId) flatMap {
      ctReg =>
        (ctReg \ "status").as[String] match {
          case "held" | "locked" => buildHeld(regId, ctReg)
          case "acknowledged" => Future.successful(buildAcknowledged(regId, ctReg, enrolments))
          case _ => Future.successful(ctReg.as[IncorpAndCTDashboard](IncorpAndCTDashboard.reads(None)))
        }
    }
  }

  private[services] def getCompanyName(regId: String)(implicit hc: HeaderCarrier): Future[String] = {
    for {
      confRefs <- companyRegistrationConnector.fetchConfirmationReferences(regId) map {
        case ConfirmationReferencesSuccessResponse(refs) => refs
        case _ => throw new ConfirmationRefsNotFoundException
      }
      transId = confRefs.transactionId
      companyName <- incorpInfoConnector.getCompanyName(transId)
    } yield companyName
  }

  private[services] def buildHeld(regId: String, ctReg: JsValue)(implicit hc: HeaderCarrier): Future[IncorpAndCTDashboard] = {
    for {
      heldSubmissionDate <- companyRegistrationConnector.fetchHeldSubmissionTime(regId)
      submissionDate      = heldSubmissionDate.map(date => extractSubmissionDate(date))
    } yield {
      ctReg.as[IncorpAndCTDashboard](IncorpAndCTDashboard.reads(submissionDate))
    }
  }

  private[services] def buildAcknowledged(regId: String, ctReg: JsValue, enrolments : Enrolments): IncorpAndCTDashboard = {
    val ctData = ctReg.as[IncorpAndCTDashboard](IncorpAndCTDashboard.reads(None))
    val matchCTUTR = for {
      ctutr     <- ctData.ctutr
      enrolment <- enrolments.getEnrolment(appConfig.IR_CT)
      id        <- enrolment.getIdentifier("UTR") if enrolment.isActivated
    } yield id.value == ctutr

    matchCTUTR match {
      case Some(true) => ctData
      case Some(false) =>
        pagerduty(PagerDutyKeys.CT_UTR_MISMATCH,Some(s" - for registration id: $regId"))
        ctData.copy(ctutr = None)
      case _ =>
        ctData.copy(ctutr = None)
    }
  }

  private[services] def extractSubmissionDate(jsonDate: JsValue): String = {
    val dgdt : DateTime = jsonDate.as[DateTime]
    dgdt.toString("d MMMM yyyy")
  }

  def checkForEmailMismatch(regID : String, authDetails : AuthDetails)(implicit hc : HeaderCarrier, req: Request[AnyContent]) : Future[Boolean] = {
    keystoreConnector.fetchAndGet[Boolean]("emailMismatchAudit") flatMap {
      case Some(mismatchResult) => Future.successful(mismatchResult)
      case _ => companyRegistrationConnector.retrieveEmail(regID) flatMap {
        case Some(crEmail) =>
          val mismatch = authDetails.email != crEmail.address
          if (mismatch) {
            for {
              result <- auditConnector.sendExtendedEvent(
                new EmailMismatchEvent(
                  EmailMismatchEventDetail(
                    authDetails.externalId,
                    authDetails.authProviderId.providerId,
                    regID
                  )
                )
              )
              _ <- keystoreConnector.cache("emailMismatchAudit", mismatch)
            } yield mismatch
          } else {
            Future.successful(mismatch)
          }
        case _ => Future.successful(false)
      }
    }
  }
}