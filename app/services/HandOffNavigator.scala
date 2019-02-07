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

import java.util.NoSuchElementException

import config.FrontendAppConfig
import controllers.handoff._
import models.handoff.{HandOffNavModel, NavLinks, Receiver, Sender}
import play.api.Logger
import repositories._
import uk.gov.hmrc.http.HeaderCarrier
import utils.{SCRSExceptions, SCRSFeatureSwitches}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try
import scala.util.control.NoStackTrace

class NavModelNotFoundException extends NoStackTrace

trait HandOffNavigator extends CommonService with SCRSExceptions {

  val scrsFeatureSwitches: SCRSFeatureSwitches
  val appConfig: FrontendAppConfig

  lazy val leg = appConfig.selfFullLegacy
  lazy val full = appConfig.selfFull
  val navModelMongo : NavModelRepository
  def compRegFrontendUrl: String = if (scrsFeatureSwitches.legacyEnv.enabled) {
    leg
  } else {
    full
  }

  private def buildUrl(url: String) = {
    s"$compRegFrontendUrl${url}"
  }

  lazy val corporationTaxDetails   = buildUrl(routes.CorporationTaxDetailsController.corporationTaxDetails().url)
  lazy val aboutYouUrl             = buildUrl(routes.BasicCompanyDetailsController.returnToAboutYou().url)

  lazy val regularPaymentsBackUrl  = buildUrl(routes.BusinessActivitiesController.businessActivitiesBack().url)
  lazy val summaryUrl              = buildUrl(routes.CorporationTaxSummaryController.corporationTaxSummary().url)
  lazy val groupHandBackUrl        = buildUrl(routes.GroupController.groupHandBack().url)

  lazy val returnSummaryUrl        = buildUrl(routes.IncorporationSummaryController.returnToCorporationTaxSummary().url)
  lazy val confirmationURL         = buildUrl(routes.RegistrationConfirmationController.registrationConfirmation().url)

  lazy val forwardConfirmationUrl  = buildUrl(routes.RegistrationConfirmationController.paymentConfirmation().url)

   val postSignInCall = controllers.reg.routes.SignInOutController.postSignIn(None)
  val postSignInUrl = postSignInCall.url

  private val HANDOFF_NAV_KEY: String = "HandOffNavigation"

  def fetchNavModel(canCreate: Boolean = false)(implicit hc: HeaderCarrier): Future[HandOffNavModel] = {
    fetchRegistrationID flatMap  { reg =>
      navModelMongo.getNavModel(reg).flatMap {
        case Some(s) => Future.successful(s)
        case None if canCreate => initNavModel
        case _ => throw new NavModelNotFoundException
      }
    }
  }

  private def initNavModel(implicit hc: HeaderCarrier): Future[HandOffNavModel] = {
    val model = HandOffNavModel(
      Sender(Map(
        "1"   -> NavLinks(corporationTaxDetails, aboutYouUrl),
        "3"   -> NavLinks(groupHandBackUrl, regularPaymentsBackUrl),
        "3-2" -> NavLinks(summaryUrl, regularPaymentsBackUrl),
        "5"   -> NavLinks(confirmationURL, returnSummaryUrl),
        "5-2" -> NavLinks(forwardConfirmationUrl,""))),
      Receiver(Map("0" -> NavLinks(firstHandoffURL, "")).withDefaultValue(NavLinks(postSignInUrl,postSignInUrl))))

    cacheNavModel(model, hc) map (_ => model)
  }

  def cacheNavModel(implicit navModel: HandOffNavModel, hc: HeaderCarrier): Future[Option[HandOffNavModel]] = {
    fetchRegistrationID flatMap  { reg =>
      navModelMongo.insertNavModel(reg, navModel)
    }
  }

  private def previous(nav: String) = nav match {
    case s if s.contains("-") => val numbers = s.split('-').map(_.toInt)
      (numbers.dropRight(1):+numbers.last-1).mkString("-")
    case s => (s.toInt - 1).toString
  }

  def forwardTo(navPosition: String)(implicit navModel: HandOffNavModel, hc: HeaderCarrier): String = {
    Try(navModel.receiver.nav(previous(navPosition)).forward)
      .getOrElse(noNavModelPosition(navPosition))
  }

  def forwardTo(navPosition: Int)(implicit navModel: HandOffNavModel, hc: HeaderCarrier): String = {
    val getPreviousHandBackForwardLinks = navPosition - 1
    Try(navModel.receiver.nav((navPosition - 1).toString).forward)
      .getOrElse(noNavModelPosition(getPreviousHandBackForwardLinks))
  }

  def hmrcLinks(navPosition: String)(implicit navModel: HandOffNavModel, hc: HeaderCarrier): NavLinks = {
    Try(navModel.sender.nav(navPosition))
      .getOrElse(noNavModelPosition(navPosition))
  }

  private def noNavModelPosition(pos: Any)(implicit n: HandOffNavModel) =  {
    Logger.warn(s"[HandOffNavigator] failed to find navLinks with pos: $pos - current NavModelLinks: ${n.sender.nav} ${n.receiver.nav} ${n.receiver.jump}")
    throw new NoSuchElementException(s"key not found: $pos")
  }

  private[services] def firstHandoffURL: String = {
    scrsFeatureSwitches.cohoFirstHandOff.enabled match {
      case false => appConfig.getConfString("coho-service.basic-company-details-stub", throw new Exception("could not find first handoff incorp stub"))
      case true => appConfig.getConfString("coho-service.basic-company-details", throw new Exception("could not find first handoff coho-service.url"))
    }
  }
}