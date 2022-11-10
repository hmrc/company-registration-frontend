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

package controllers

import config.AppConfig
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import controllers.auth.AuthenticatedController
import play.api.i18n.{I18nSupport, Lang}
import play.api.mvc.{Action, AnyContent, BaseController, ControllerComponents, Flash, MessagesControllerComponents, Request, Result}
import services.LanguageService
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.{AuthConnector, AuthProviders, ConfidenceLevel, PlayAuthConnector}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.play.language.{LanguageController, LanguageUtils}
import utils.SessionRegistration

import java.net.URI
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class LanguageSwitchController @Inject()(override val appConfig: AppConfig,
                                         languageUtils: LanguageUtils,
                                         override val authConnector: PlayAuthConnector,
                                         override val controllerComponents: MessagesControllerComponents,
                                         override val keystoreConnector: KeystoreConnector,
                                         override val compRegConnector: CompanyRegistrationConnector,
                                         languageService: LanguageService
                                        )(implicit val ec: ExecutionContext) extends AuthenticatedController with I18nSupport with SessionRegistration {

  def setLanguage(language: String): Action[AnyContent] = Action.async { implicit request =>

    val enabled: Boolean = appConfig.languageTranslationEnabled && appConfig.languageMap.get(language).exists(languageUtils.isLangAvailable)
    val lang: Lang = if (enabled) appConfig.languageMap.getOrElse(language, languageUtils.getCurrentLang) else languageUtils.getCurrentLang

    //If authorised and has regId in session then update BE language against CompanyReg record; else just change cookie value
    baseAuthFunction {
      withOptionalRegId {
        case Some(regId) =>
          languageService.updateLanguage(regId, lang).map(_ => redirect(lang))
        case _ =>
          Future.successful(redirect(lang))
      }
    } recover {
      case _ =>
        redirect(lang)
    }
  }

  private def redirect(lang: Lang)(implicit request: Request[AnyContent]): Result = {
    val redirectURL: String = request.headers
      .get(REFERER)
      .flatMap(asRelativeUrl)
      .getOrElse(fallbackURL)

    Redirect(redirectURL).withLang(lang).flashing(Flash(Map("switching-language" -> "true")))
  }

  private def asRelativeUrl(url: String): Option[String] =
    for {
      uri      <- Try(new URI(url)).toOption
      path     <- Option(uri.getRawPath).filterNot(_.isEmpty)
      query    <- Option(uri.getRawQuery).map("?" + _).orElse(Some(""))
      fragment <- Option(uri.getRawFragment).map("#" + _).orElse(Some(""))
    } yield s"$path$query$fragment"

  private def fallbackURL: String = controllers.reg.routes.WelcomeController.show.url
}