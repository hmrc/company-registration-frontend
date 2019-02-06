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

package services

import _root_.connectors._
import audit.events.{EmailMismatchEvent, EmailMismatchEventDetail}
import config.FrontendAppConfig
import javax.inject.Inject
import models._
import models.auth.AuthDetails
import models.external.{OtherRegStatus, Statuses}
import org.joda.time.{DateTime, LocalDate}
import play.api.libs.json.JsValue
import play.api.mvc.{AnyContent, Call, Request}
import uk.gov.hmrc.auth.core.Enrolments
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import scala.concurrent.ExecutionContext.Implicits.global
import utils._

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
                                     val appConfig: FrontendAppConfig
                                    ) extends DashboardService {


  lazy val otrsUrl         = appConfig.getConfString("otrs.url", throw new Exception("Could not find config for key: otrs.url"))
  lazy val payeBaseUrl     = appConfig.getConfString("paye-registration-www.url-prefix", throw new Exception("Could not find config for key: paye-registration-www.url-prefix"))
  lazy val payeUri         = appConfig.getConfString("paye-registration-www.start-url", throw new Exception("Could not find config for key: paye-registration-www.start-url"))
  lazy val vatBaseUrl      = appConfig.getConfString("vat-registration-www.url-prefix", throw new Exception("Could not find config for key: vat-registration-www.url-prefix"))
  lazy val vatUri          = appConfig.getConfString("vat-registration-www.start-url", throw new Exception("Could not find config for key: vat-registration-www.start-url"))

  lazy val loggingDays     = appConfig.getConfString("alert-config.logging-day", throw new Exception("Could not find config key: LoggingDay"))
  lazy val loggingTimes    = appConfig.getConfString("alert-config.logging-time", throw new Exception("Could not find config key: LoggingTime"))
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
  val appConfig: FrontendAppConfig

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
      hasVatCred = hasEnrolment(enrolments, List("HMCE-VATDEC-ORG", "HMCE-VATVAR-ORG"))
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
    authEnrolments.enrolments.exists(e => appConfig.restrictedEnrolments.contains(e.key))
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
    implicit val cancelURL: Call = controllers.dashboard.routes.CancelRegistrationController.showCancelPAYE()

    if (featureFlag.paye.enabled) {
      statusToServiceDashboard(payeConnector.getStatus(regId), enrolments, List("IR-PAYE"), Some(getCurrentPayeThresholds))
    } else {
      Future.successful(toDashboard(OtherRegStatus(Statuses.NOT_ENABLED, None, None, None, None), None))
    }
  }

  def getCurrentPayeThresholds: Map[String, Int] = {
    val now = SystemDate.getSystemDate
    val taxYearStart = LocalDate.parse("2018-04-06")
    if (now.isEqual(taxYearStart) || now.isAfter(taxYearStart)) {
      Map("weekly" -> 116, "monthly" -> 503, "annually" -> 6032)
    } else {
      Map("weekly" -> 113, "monthly" -> 490, "annually" -> 5876)
    }
  }

  private[services] def buildVATDashComponent(regId: String, enrolments: Enrolments)(implicit hc: HeaderCarrier): Future[ServiceDashboard] = {
    implicit val startURL: String = s"$vatBaseUrl$vatUri"
    implicit val cancelURL: Call = controllers.dashboard.routes.CancelRegistrationController.showCancelVAT()

    getCurrentVatThreshold flatMap { threshold =>
      Try(threshold.toInt) match {
        case Success(intThreshold) => if (featureFlag.vat.enabled) {
        statusToServiceDashboard(vatConnector.getStatus(regId), enrolments, List("HMCE-VATDEC-ORG", "HMCE-VATVAR-ORG"), Some(Map("yearly" -> intThreshold)))
      } else {
          Future.successful(toDashboard(OtherRegStatus(Statuses.NOT_ENABLED, None, None, None, None), Some(Map("yearly" -> intThreshold))))
        }
        case Failure(ex) => throw new Exception(s"Value from Vat Reg Threshold not an int for regid: ${regId} Exception was $ex")
      }
    }
  }

  private[services] def getCurrentVatThreshold(implicit hc: HeaderCarrier): Future[String] = {
    thresholdService.fetchCurrentVatThreshold
  }

  private[services] def buildIncorpCTDashComponent(regId: String, enrolments : Enrolments)(implicit hc: HeaderCarrier): Future[IncorpAndCTDashboard] = {
    companyRegistrationConnector.retrieveCorporationTaxRegistration(regId) flatMap {
      ctReg =>
        (ctReg \ "status").as[String] match {
          case "held"         => buildHeld(regId, ctReg)
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
      submissionDate = extractSubmissionDate(heldSubmissionDate.get)
    } yield {
      ctReg.as[IncorpAndCTDashboard](IncorpAndCTDashboard.reads(Option(submissionDate)))
    }
  }

  private[services] def buildAcknowledged(regId: String, ctReg: JsValue, enrolments : Enrolments): IncorpAndCTDashboard = {
    val ctData = ctReg.as[IncorpAndCTDashboard](IncorpAndCTDashboard.reads(None))
    val matchCTUTR = for {
      ctutr     <- ctData.ctutr
      enrolment <- enrolments.getEnrolment("IR-CT")
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