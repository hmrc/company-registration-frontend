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

package services

import config.FrontendAuthConnector
import _root_.connectors._
import models._
import models.auth.Enrolment
import models.external.{OtherRegStatus, Statuses}
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.mvc.Call
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import utils.{SCRSExceptions, SCRSFeatureSwitches}

import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import scala.concurrent.Future
import scala.util.control.NoStackTrace
import uk.gov.hmrc.http.{ HeaderCarrier, HttpException }

object DashboardService extends DashboardService with ServicesConfig {
  val keystoreConnector = KeystoreConnector
  val companyRegistrationConnector = CompanyRegistrationConnector
  val incorpInfoConnector = IncorpInfoConnector
  val payeConnector = PAYEConnector
  val vatConnector = VATConnector
  val authConnector = FrontendAuthConnector
  val otrsUrl = getConfString("otrs.url", throw new Exception("Could not find config for key: otrs.url"))
  val payeBaseUrl = getConfString("paye-registration-www.url-prefix", throw new Exception("Could not find config for key: paye-registration-www.url-prefix"))
  val payeUri = getConfString("paye-registration-www.start-url", throw new Exception("Could not find config for key: paye-registration-www.start-url"))
  val vatBaseUrl = getConfString("vat-registration-www.url-prefix", throw new Exception("Could not find config for key: vat-registration-www.url-prefix"))
  val vatUri = getConfString("vat-registration-www.start-url", throw new Exception("Could not find config for key: vat-registration-www.start-url"))
  val featureFlag = SCRSFeatureSwitches
}

sealed trait DashboardStatus
case class DashboardBuilt(d: Dashboard) extends DashboardStatus
case object RejectedIncorp extends DashboardStatus
case object CouldNotBuild extends DashboardStatus

class ComfirmationRefsNotFoundException extends NoStackTrace

trait DashboardService extends SCRSExceptions with CommonService {
  import scala.language.implicitConversions

  val companyRegistrationConnector : CompanyRegistrationConnector
  val payeConnector, vatConnector: ServiceConnector
  val incorpInfoConnector: IncorpInfoConnector
  val authConnector: AuthConnector
  val otrsUrl: String
  val payeBaseUrl: String
  val payeUri: String
  val vatBaseUrl: String
  val vatUri: String
  val featureFlag: SCRSFeatureSwitches

  implicit def toDashboard(s: OtherRegStatus)(implicit startURL: String, cancelURL: Call): ServiceDashboard = {
    ServiceDashboard(s.status, s.lastUpdate.map(_.toString("d MMMM yyyy")), s.ackRef,
      ServiceLinks(startURL, otrsUrl, s.restartURL, s.cancelURL.map(_ => cancelURL.url)))
  }

  def buildDashboard(regId:String)(implicit hc: HeaderCarrier, auth: AuthContext): Future[DashboardStatus] = {
    for {
      incorpCTDash <- buildIncorpCTDashComponent(regId)
      payeDash <- buildPAYEDashComponent(regId)
      hasVatCred <- hasEnrolment(List("HMCE-VATDEC-ORG", "HMCE-VATVAR-ORG"))
      vatDash <- buildVATDashComponent(regId)
    } yield {
      incorpCTDash.status match {
        case "draft" => CouldNotBuild
        case "rejected" => RejectedIncorp
        //case _ => getCompanyName(regId) map(cN => DashboardBuilt(Dashboard(incorpCTDash, payeDash, cN)))
        case _ => DashboardBuilt(Dashboard("", incorpCTDash, payeDash, vatDash, hasVatCred)) //todo: leaving company name blank until story gets played to add it back
      }
    }
  }

  private[services] def hasEnrolment(enrolmentKeys: Seq[String])(implicit hc: HeaderCarrier, auth: AuthContext): Future[Boolean] = {
    authConnector.getEnrolments[Option[Seq[Enrolment]]](auth) map {
      case Some(enrolments) => enrolments exists (e => enrolmentKeys.contains(e.key))
      case None => false
    } recover {
      case ex: HttpException =>
        Logger.error(s"[AuthConnector] [getEnrolments] - ${ex.responseCode} was returned - reason : ${ex.message}", ex)
        false
      case ex: Throwable =>
        Logger.error(s"[AuthConnector] [getEnrolments] - An exception was thrown", ex)
        false
    }
  }

  private[services] def statusToServiceDashboard(res: Future[StatusResponse], enrolments: Seq[String])
                                                (implicit hc: HeaderCarrier, auth: AuthContext, startURL: String, cancelURL: Call): Future[ServiceDashboard] = {
    res flatMap {
      case SuccessfulResponse(status) => Future.successful(status)
      case ErrorResponse => Future.successful(OtherRegStatus(Statuses.UNAVAILABLE, None, None, None, None))
      case NotStarted =>
        hasEnrolment(enrolments) map {
          case true => OtherRegStatus(Statuses.NOT_ELIGIBLE, None, None, None, None)
          case false => OtherRegStatus(Statuses.NOT_STARTED, None, None, None, None)
        }
    }
  }

  private[services] def buildPAYEDashComponent(regId: String)(implicit hc: HeaderCarrier, auth: AuthContext): Future[ServiceDashboard] = {
    implicit val startURL: String = s"$payeBaseUrl$payeUri"
    implicit val cancelURL: Call = controllers.dashboard.routes.CancelRegistrationController.showCancelPAYE()

    if (featureFlag.paye.enabled) {
      statusToServiceDashboard(payeConnector.getStatus(regId), List("IR-PAYE"))
    } else {
      Future.successful(OtherRegStatus(Statuses.NOT_ENABLED, None, None, None, None))
    }
  }

  private[services] def buildVATDashComponent(regId: String)(implicit hc: HeaderCarrier, auth: AuthContext): Future[Option[ServiceDashboard]] = {
    implicit val startURL: String = s"$vatBaseUrl$vatUri"
    implicit val cancelURL: Call = controllers.dashboard.routes.CancelRegistrationController.showCancelVAT()

      if (featureFlag.vat.enabled) {
      statusToServiceDashboard(vatConnector.getStatus(regId), List("HMCE-VATDEC-ORG", "HMCE-VATVAR-ORG")).map(Some(_))
    } else {
      Future.successful(None)
    }
  }

  private[services] def buildIncorpCTDashComponent(regId: String)(implicit hc: HeaderCarrier): Future[IncorpAndCTDashboard] = {
    companyRegistrationConnector.retrieveCorporationTaxRegistration(regId) flatMap {
      ctReg =>
        (ctReg \ "status").as[String] match {
          case "held" => buildHeld(regId, ctReg)
          case _ => Future.successful(ctReg.as[IncorpAndCTDashboard](IncorpAndCTDashboard.reads(None)))
        }
    }
  }

  private[services] def getCompanyName(regId: String)(implicit hc: HeaderCarrier): Future[String] = {
    for {
      confRefs <- companyRegistrationConnector.fetchConfirmationReferences(regId) map {
        case ConfirmationReferencesSuccessResponse(refs) => refs
        case _ => throw new ComfirmationRefsNotFoundException
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

  private[services] def extractSubmissionDate(jsonDate: JsValue): String = {

    val dgdt : DateTime = jsonDate.as[DateTime]
    dgdt.toString("d MMMM yyyy")
  }
}
