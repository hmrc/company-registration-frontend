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

package controllers.feedback

import java.net.URLEncoder
import javax.inject.Inject

import config._
import controllers.auth._
import play.api.Logger
import play.api.http.{Status => HttpStatus}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, Request, RequestHeader}
import play.twirl.api.Html
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.crypto.PlainText
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.controller.{FrontendController, UnauthorisedAction}
import uk.gov.hmrc.play.partials._
import views.html.{feedback, feedback_thankyou}

import scala.concurrent.Future

class FeedbackControllerImpl @Inject()(val wsHttp: WSHttp,
                                       val authConnector: PlayAuthConnector,
                                       cryptoInitialiser: CryptoInitialiser,
                                       val appConfig: FrontendAppConfig,
                                       val messagesApi: MessagesApi) extends FeedbackController with PartialRetriever {
  override val httpPost = wsHttp
  override val httpGet = wsHttp
  override def contactFormReferer(implicit request: Request[AnyContent]): String = request.headers.get(REFERER).getOrElse("")

  override def localSubmitUrl(implicit request: Request[AnyContent]): String = routes.FeedbackController.submit().url

  protected def loadPartial(url : String)(implicit request : RequestHeader) : HtmlPartial = ???
  implicit val cachedStaticHtmlPartialRetriever: CachedStaticHtmlPartialRetriever = new CachedStaticHtmlPartialRetriever {
    override val httpGet: HttpGet = wsHttp
  }

  override implicit val formPartialRetriever: FormPartialRetriever = new FormPartialRetriever {
    override def httpGet: HttpGet = wsHttp
    override def crypto: (String) => String = cookie => cryptoInitialiser.cryptoInstance.SessionCookieCrypto.encrypt(PlainText(cookie)).value
  }
}

trait FeedbackController extends FrontendController with AuthFunction with I18nSupport {

  implicit val formPartialRetriever: FormPartialRetriever
  implicit val cachedStaticHtmlPartialRetriever: CachedStaticHtmlPartialRetriever

  def httpPost: HttpPost
  def contactFormReferer(implicit request: Request[AnyContent]): String
  def localSubmitUrl(implicit request: Request[AnyContent]): String

  implicit val appConfig: FrontendAppConfig
  lazy val contactFrontendBase = appConfig.contactFrontendPartialBaseUrl
  lazy val serviceId = appConfig.serviceId

  private val TICKET_ID = "ticketId"


  private def feedbackFormPartialUrl(implicit request: Request[AnyContent]) =
    s"${contactFrontendBase}/contact/beta-feedback/form/?submitUrl=${urlEncode(localSubmitUrl)}" +
      s"&service=${urlEncode(serviceId)}&referer=${urlEncode(contactFormReferer)}"


  private def feedbackHmrcSubmitPartialUrl(implicit request: Request[AnyContent]) =
    s"${contactFrontendBase}/contact/beta-feedback/form?resubmitUrl=${urlEncode(localSubmitUrl)}"


  private def feedbackThankYouPartialUrl(ticketId: String)(implicit request: Request[AnyContent]) =
    s"${contactFrontendBase}/contact/beta-feedback/form/confirmation?ticketId=${urlEncode(ticketId)}"

  def show: Action[AnyContent] = UnauthorisedAction {
    implicit request =>
      (request.session.get(REFERER), request.headers.get(REFERER)) match {
        case (None, Some(ref)) => Ok(feedback(feedbackFormPartialUrl, None)).withSession(request.session + (REFERER -> ref))
        case _ => Ok(feedback(feedbackFormPartialUrl, None))
      }
  }

  def submit: Action[AnyContent] = UnauthorisedAction.async {
    implicit request =>
      request.body.asFormUrlEncoded.map { formData =>
        httpPost.POSTForm[HttpResponse](feedbackHmrcSubmitPartialUrl, formData)(rds = readPartialsForm, hc = partialsReadyHeaderCarrier, implicitly).map {
          resp =>
            resp.status match {
              case HttpStatus.OK => Redirect(routes.FeedbackController.thankyou()).withSession(request.session + (TICKET_ID -> resp.body))
              case HttpStatus.BAD_REQUEST => BadRequest(feedback(feedbackFormPartialUrl, Some(Html(resp.body))))
              case status => Logger.warn(s"Unexpected status code from feedback form: $status"); InternalServerError
            }
        }
      }.getOrElse {
        Logger.warn("Trying to submit an empty feedback form")
        Future.successful(InternalServerError)
      }
  }

  def thankyou: Action[AnyContent] = UnauthorisedAction {
    implicit request =>
      val ticketId = request.session.get(TICKET_ID).getOrElse("N/A")
      val referer = request.session.get(REFERER).getOrElse("/")
      Ok(feedback_thankyou(feedbackThankYouPartialUrl(ticketId), referer)).withSession(request.session - REFERER)
  }

  private def urlEncode(value: String) = URLEncoder.encode(value, "UTF-8")

  private def partialsReadyHeaderCarrier(implicit request: Request[_]): HeaderCarrier = {
    val hc1 = SCRSHeaderCarrierForPartialsConverter.headerCarrierEncryptingSessionCookieFromRequest(request)
    SCRSHeaderCarrierForPartialsConverter.headerCarrierForPartialsToHeaderCarrier(hc1)
  }

  object SCRSHeaderCarrierForPartialsConverter extends HeaderCarrierForPartialsConverter {
    override val crypto = encryptCookieString _

    def encryptCookieString(cookie: String) : String = {
      formPartialRetriever.crypto(cookie)
    }
  }

  implicit val readPartialsForm: HttpReads[HttpResponse] = new HttpReads[HttpResponse] {
    def read(method: String, url: String, response: HttpResponse): HttpResponse = response
  }
}
