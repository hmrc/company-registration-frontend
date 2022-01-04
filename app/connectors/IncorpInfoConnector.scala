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

package connectors

import javax.inject.Inject

import config.{FrontendAppConfig, WSHttp}
import models.Shareholder
import play.api.Logging
import play.api.libs.json.JsValue
import services.MetricsService
import uk.gov.hmrc.http.{CoreGet, HeaderCarrier, HttpException, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class IncorpInfoConnectorImpl @Inject()(appConfig: FrontendAppConfig, val wSHttp: WSHttp, val metricsService: MetricsService) extends IncorpInfoConnector {

 lazy val incorpInfoUrl = s"${appConfig.servicesConfig.baseUrl("incorp-info")}/incorporation-information"
}

trait IncorpInfoConnector extends Logging {
  val wSHttp: CoreGet
  val incorpInfoUrl: String

  val metricsService: MetricsService

  def getCompanyName(transId: String)(implicit hc: HeaderCarrier): Future[String] = {
    getCompanyProfile(transId).map(js => (js \ "company_name").as[String])
  }

  def getCompanyProfile(transId: String)(implicit hc: HeaderCarrier): Future[JsValue] = {
    metricsService.processDataResponseWithMetrics[JsValue](metricsService.retrieveCompanyProfileIITimer.time()) {
      wSHttp.GET[JsValue](s"$incorpInfoUrl/$transId/company-profile") recover handleError(transId, "getCompanyProfile")
    }
  }

  def returnListOfShareholdersFromTxApi(transId: String)(implicit hc: HeaderCarrier): Future[Either[Exception,List[Shareholder]]] = {
    wSHttp.GET[HttpResponse](s"$incorpInfoUrl/shareholders/$transId").map { res =>
      if(res.status == 200) {
        res.json.validate[List[Shareholder]].asEither match {
          case Right(l) => Right(l)
          case Left(l) => logger.error(s"[returnListOfShareholdersFromTxApi] II returned list of shareholders but data was unparseable, returning empty list to user: $transId")
            Right(List.empty[Shareholder])
        }
      } else {
        logger.error(s"[returnListOfShareholdersFromTxApi] II returned NO shareholders -  This is a problem with the data on the transactional API $transId")
        Right(List.empty[Shareholder])
      }
    }.recover {
      case e :Exception =>
          logger.error(s"[returnListOfShareholdersFromTxApi] Something went wrong when calling II: $transId, ${e.getMessage}")
        Left(e)
    }
  }

  private def handleError(transId: String, funcName: String):PartialFunction[Throwable, JsValue] = {
    case ex: HttpException =>
      throw new Exception(s"[IncorpInfoConnector] [$funcName] - An exception was caught. Response code : ${ex.responseCode} reason : ${ex.message}")
    case ex: Throwable =>
      throw new Exception
  }

  def injectTestIncorporationUpdate(transId: String, isSuccess: Boolean)(implicit hc: HeaderCarrier): Future[Boolean] = {
    val queryString = s"txId=$transId&date=2018-1-1${if(isSuccess) "&crn=12345678" else ""}&success=$isSuccess"

    wSHttp.GET[HttpResponse](s"$incorpInfoUrl/test-only/add-incorp-update/?$queryString") map (_ => true) recover { case _ =>
        logger.error(s"[IncorpInfoConnector] [injectTestIncorporationUpdate] Failed to inject a test incorporation update into II for $transId")
        false
    }
  }

  def manuallyTriggerIncorporationUpdate(implicit hc:HeaderCarrier): Future[Boolean] = {
    wSHttp.GET[HttpResponse](s"$incorpInfoUrl/test-only/manual-trigger/fireSubs") map (_ => true) recover { case _ =>
        logger.error(s"[IncorpInfoConnector] [manuallyTriggerIncorporationUpdate] Failed to trigger subscription processing on II")
        false
    }
  }
}