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

package controllers.test

import config.AppConfig
import connectors.{CompanyRegistrationConnector, DynamicStubConnector, KeystoreConnector}
import forms.test.ETMPPost
import javax.inject.{Inject, Singleton}
import models.test.{ETMPAcknowledgment, ETMPCTRecordUpdates, ETMPNotification}
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.CommonService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import utils.SCRSExceptions
import views.html.test.{EMTPPostView, CTUpdatesDisplay => CTUpdatesDisplayView}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ETMPNotificationTestController @Inject()(val brdsConnector: DynamicStubConnector,
                                               val crConnector: CompanyRegistrationConnector,
                                               val keystoreConnector: KeystoreConnector,
                                               mcc: MessagesControllerComponents,
                                               viewEMTPPostView: EMTPPostView,
                                               viewCTUpdatesDisplayView: CTUpdatesDisplayView)
                                              (implicit val appConfig: AppConfig, implicit val ec: ExecutionContext)
  extends FrontendController(mcc) with CommonService with SCRSExceptions with I18nSupport {

  def show: Action[AnyContent] = Action.async {
    implicit request =>
      Future.successful(Ok(viewEMTPPostView(ETMPPost.form.fill(ETMPNotification("", "", "", Some(""), "")))))
  }

  def submit: Action[AnyContent] = Action.async {
    implicit request =>
      ETMPPost.form.bindFromRequest().fold(
        errors => Future.successful(BadRequest(viewEMTPPostView(errors))),
        valid =>
          brdsConnector.postETMPNotificationData(valid) map {
            _ => Ok
          }
      )
  }

  def showCTRecordUpdates: Action[AnyContent] = Action.async {
    implicit request =>
      for {
        regId <- fetchRegistrationID
        ctRecord <- crConnector.retrieveCorporationTaxRegistration(regId)
      } yield {

        val acknowledgementRefs = (ctRecord \\ "acknowledgementReferences").head.as[ETMPAcknowledgment]
        val rootStatus = (ctRecord \ "status").as[String]
        val fetchedData = ETMPCTRecordUpdates(
          rootStatus,
          acknowledgementRefs.ctUtr,
          acknowledgementRefs.timestamp,
          acknowledgementRefs.status
        )
        Ok(viewCTUpdatesDisplayView(fetchedData))
      }
  }
}