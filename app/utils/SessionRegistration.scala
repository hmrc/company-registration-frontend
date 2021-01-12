/*
 * Copyright 2021 HM Revenue & Customs
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

import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import models.Groups
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.mvc.Result
import play.api.mvc.Results.Redirect
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait SessionRegistration {

  val keystoreConnector: KeystoreConnector
  val compRegConnector: CompanyRegistrationConnector

  def registered(f: => String => Future[Result])(implicit hc: HeaderCarrier): Future[Result] = registered()(f)

  def registeredHandOff(handOff: String, payload: String)(f: => String => Future[Result])(implicit hc: HeaderCarrier): Future[Result] = {
    registered(Some(handOff), Some(payload))(f)
  }

  private def registered(handOff: Option[String] = None, payload: Option[String] = None)(f: => String => Future[Result])(implicit hc: HeaderCarrier): Future[Result] = {
    keystoreConnector.fetchAndGet[String]("registrationID") flatMap {
      case Some(regId) => f(regId)
      case None => Logger.error("[SessionRegistration] [registered] returned None from keystore when fetching a registrationID")
        Future.successful(Redirect(controllers.reg.routes.SignInOutController.postSignIn(None, handOff, payload)))
    }
  }

  def checkStatuses(f: => String => Future[Result], statuses: Seq[String] = Seq("draft", "rejected"))(implicit hc: HeaderCarrier): Future[Result] = {
    registered { regId =>
      compRegConnector.retrieveCorporationTaxRegistration(regId) flatMap {
        ctReg =>
          if (!checkSCRSVerified(ctReg)) {
            Future.successful(Redirect(controllers.reg.routes.SignInOutController.postSignIn(None)))
          } else {
            if (statuses.contains((ctReg \ "status").as[String])) {
              Future.successful(Redirect(controllers.reg.routes.SignInOutController.postSignIn(None)))
            } else {
              f(regId)
            }
          }
      }
    }
  }

    def checkSCRSVerified(fullCorpModel: JsValue): Boolean = {
      (fullCorpModel \ "verifiedEmail" \ "verified").asOpt[Boolean].fold {
        Logger.info("[SessionRegistration] User does not have an Email Block redirecting to post sign in")
        false
      }(e => e)

    }

    def checkStatus(f: => String => Future[Result])(implicit hc: HeaderCarrier): Future[Result] = {
      registered { regId =>
        compRegConnector.retrieveCorporationTaxRegistration(regId) flatMap {
          ctReg =>
            if (!checkSCRSVerified(ctReg)) {
              Future.successful(Redirect(controllers.reg.routes.SignInOutController.postSignIn(None)))
            } else {
              (ctReg \ "status").as[String] match {
                case "draft" => f(regId)
                case "locked" | "held" => Future.successful(Redirect(controllers.reg.routes.SignInOutController.postSignIn(None)))
                case _ => Future.successful(Redirect(controllers.dashboard.routes.DashboardController.show()))
              }
            }
        }
      }
    }
  }
