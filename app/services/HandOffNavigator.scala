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

package services

import config.FrontendConfig
import models.handoff.{HandOffNavModel, NavLinks, Receiver, Sender}
import play.api.mvc.{Call, Result}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.HeaderCarrier
import utils.{SCRSExceptions, SCRSFeatureSwitches}
import controllers.handoff._
import repositories._
import uk.gov.hmrc.http.cache.client.CacheMap
import play.api.mvc.Results.Redirect

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NoStackTrace

class NavModelNotFoundException extends NoStackTrace

trait HandOffNavigator extends CommonService with SCRSExceptions {
  _: ServicesConfig =>

  val navModelMongo : NavModelRepository
  def compRegFrontendUrl = SCRSFeatureSwitches.legacyEnv.enabled match {
    case false => FrontendConfig.selfFull
    case true => FrontendConfig.selfFullLegacy
  }

  private def buildUrl(call: Call) = {
    val url = call.url
    val stripLen = "?request=".length
    s"$compRegFrontendUrl${url.substring(0, url.length - stripLen)}"
  }

  def corporationTaxDetails = buildUrl(routes.CorporationTaxDetailsController.corporationTaxDetails(""))
  def aboutYouUrl = buildUrl(routes.BasicCompanyDetailsController.returnToAboutYou(""))

  def regularPaymentsBackUrl = buildUrl(routes.BusinessActivitiesController.businessActivitiesBack(""))
  def summaryUrl = buildUrl(routes.CorporationTaxSummaryController.corporationTaxSummary(""))

  def returnSummaryUrl = buildUrl(routes.IncorporationSummaryController.returnToCorporationTaxSummary(""))
  def confirmationURL = buildUrl(routes.RegistrationConfirmationController.registrationConfirmation(""))

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
        "1" -> NavLinks(corporationTaxDetails, aboutYouUrl),
        "3" -> NavLinks(summaryUrl, regularPaymentsBackUrl),
        "5" -> NavLinks(confirmationURL, returnSummaryUrl))),
      Receiver(Map("0" -> NavLinks(firstHandoffURL, "")).withDefaultValue(NavLinks(postSignInUrl,postSignInUrl))))

    cacheNavModel(model, hc) map (_ => model)
  }

  def cacheNavModel(implicit navModel: HandOffNavModel, hc: HeaderCarrier): Future[Either[Option[HandOffNavModel], CacheMap]] = {
    fetchRegistrationID flatMap  { reg =>
      navModelMongo.insertNavModel(reg, navModel).map(Left(_))
    }
  }

  def forwardTo(navPosition: Int)(implicit navModel: HandOffNavModel, hc: HeaderCarrier) = {
    navModel.receiver.nav((navPosition - 1).toString).forward
  }

  def reverseFrom(navPosition: Int)(implicit navModel: HandOffNavModel, hc: HeaderCarrier) = {
    navModel.receiver.nav(navPosition.toString).reverse
  }

  def jumpTo(jumpTarget: String)(implicit navModel: HandOffNavModel, hc: HeaderCarrier) = {
    navModel.receiver.jump(jumpTarget)
  }

  def hmrcLinks(navPosition: Int)(implicit navModel: HandOffNavModel, hc: HeaderCarrier) = {
    navModel.sender.nav(navPosition.toString)
  }

  def handleNotFound: PartialFunction[Throwable, Result] = {
    case ex: NavModelNotFoundException => Redirect(postSignInCall)
  }

  private[services] def firstHandoffURL: String = {
    SCRSFeatureSwitches.cohoFirstHandOff.enabled match {
      case false => getConfString("coho-service.basic-company-details-stub", throw new Exception("could not find first handoff incorp stub"))
      case true => getConfString("coho-service.basic-company-details", throw new Exception("could not find first handoff coho-service.url"))
    }
  }
}
