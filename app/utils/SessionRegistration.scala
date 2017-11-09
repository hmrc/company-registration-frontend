/*
 * Copyright 2017 HM Revenue & Customs
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

package utils

import play.api.libs.json.JsValue
import play.api.mvc.{Result, Results}
import play.api.Logger
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import play.api.mvc.Results.Redirect
import uk.gov.hmrc.play.audit.http.connector.AuditResult.Success

import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

trait SessionRegistration {

  val keystoreConnector: KeystoreConnector
  val companyRegistrationConnector : CompanyRegistrationConnector

  def registered(f: => String => Future[Result])(implicit hc: HeaderCarrier): Future[Result] = {

    keystoreConnector.fetchAndGet[String]("registrationID") flatMap {
      case Some(regId) => f(regId)
      case None => Logger.error("[SessionRegistration] [registered] returned None from keystore when fetching a registrationID")
        Future.successful(Redirect(controllers.reg.routes.SignInOutController.postSignIn(None)))
    }
  }

  def checkStatuses(f: => String => Future[Result], statuses:Seq[String] = Seq("draft","rejected") )(implicit hc: HeaderCarrier): Future[Result] = {
    registered{ regId =>
      companyRegistrationConnector.retrieveCorporationTaxRegistration(regId) flatMap {
        ctReg =>
          if(statuses.contains((ctReg \ "status").as[String])) {
            Future.successful(Redirect(controllers.reg.routes.SignInOutController.postSignIn(None)))
          }
          else {
              f(regId)
            }
      }
      }
  }

  def checkStatus(f: => String => Future[Result])(implicit hc: HeaderCarrier): Future[Result] = {
    registered { regId =>
      companyRegistrationConnector.retrieveCorporationTaxRegistration(regId) flatMap {
        ctReg =>
          (ctReg \ "status").as[String] match {
            case "draft" => f(regId)
            case _ => Future.successful(Redirect(controllers.dashboard.routes.DashboardController.show()))
          }
      }
    }
  }
}
