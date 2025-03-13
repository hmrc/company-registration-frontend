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

package connectors

import javax.inject.Inject
import config.AppConfig
import models.Shareholder
import utils.Logging
import play.api.libs.json.JsValue
import services.MetricsService
import uk.gov.hmrc.http.{HeaderCarrier, HttpException, HttpResponse, StringContextOps}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2

import scala.concurrent.{ExecutionContext, Future}

class IncorpInfoConnectorImpl @Inject()(appConfig: AppConfig, val httpClientV2: HttpClientV2, val metricsService: MetricsService) extends IncorpInfoConnector {

 lazy val incorpInfoUrl = s"${appConfig.servicesConfig.baseUrl("incorp-info")}/incorporation-information"
}

trait IncorpInfoConnector extends Logging {
  val httpClientV2: HttpClientV2
  val incorpInfoUrl: String

  val metricsService: MetricsService

  def getCompanyName(transId: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[String] = {
    getCompanyProfile(transId).map(js => (js \ "company_name").as[String]) recover { case _ =>
      logger.info(s"[getCompany Name] - Couldn't find company name")
      ""
    }
  }

  def getCompanyProfile(transId: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[JsValue] = {
    metricsService.processDataResponseWithMetrics[JsValue](metricsService.retrieveCompanyProfileIITimer.time()) {
      httpClientV2
        .get(url"$incorpInfoUrl/$transId/company-profile")
        .execute[JsValue]
        .recover {
          handleError(transId, "getCompanyProfile")
        }
    }
  }

  def returnListOfShareholdersFromTxApi(transId: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[Exception,List[Shareholder]]] = {
    httpClientV2
      .get(url"$incorpInfoUrl/shareholders/$transId")
      .execute[HttpResponse]
      .map { res =>
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
      throw new Exception(s"[$funcName] - An exception was caught. Response code : ${ex.responseCode} reason : ${ex.message}")
    case ex: Throwable =>
      throw new Exception
  }

  def injectTestIncorporationUpdate(transId: String, isSuccess: Boolean)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] = {
    val queryString = s"txId=$transId&date=2018-01-01${if(isSuccess) "&crn=12345678" else ""}&success=$isSuccess"
    val url = url"$incorpInfoUrl/test-only/add-incorp-update/?$queryString"

    httpClientV2
      .get(url)
      .execute[HttpResponse]
      .map (_ => true)
      .recover { case _ =>
          logger.error(s"[injectTestIncorporationUpdate] Failed to inject a test incorporation update into II for $transId")
          false
      }
  }

  def manuallyTriggerIncorporationUpdate()(implicit hc:HeaderCarrier, ec: ExecutionContext): Future[Boolean] = {
    httpClientV2
      .get(url"$incorpInfoUrl/test-only/manual-trigger/fireSubs")
      .execute[HttpResponse]
      .map (_ => true)
      .recover { case _ =>
          logger.error(s"[manuallyTriggerIncorporationUpdate] Failed to trigger subscription processing on II")
          false
      }
  }
}