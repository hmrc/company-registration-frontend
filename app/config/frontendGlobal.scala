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

package config

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import play.api.Play.current
import play.api.i18n.Messages.Implicits._
import play.api.mvc.Request
import play.api.{Application, Configuration, Logger, Play}
import play.twirl.api.Html
import repositories.NavModelRepo
import uk.gov.hmrc.crypto.ApplicationCrypto
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.play.config.{AppName, ControllerConfig, RunMode}
import uk.gov.hmrc.play.frontend.bootstrap.DefaultFrontendGlobal
import uk.gov.hmrc.play.frontend.filters.{FrontendAuditFilter, FrontendLoggingFilter, MicroserviceFilterSupport}


object FrontendGlobal extends FrontendGlobal

abstract class FrontendGlobal
  extends DefaultFrontendGlobal
    with RunMode {

  override val auditConnector = FrontendAuditConnector
  override val loggingFilter = LoggingFilter
  override val frontendAuditFilter = AuditFilter

  override def onStart(app: Application) {
    super.onStart(app)
    ApplicationCrypto.verifyConfiguration()

    RepositoryEnsurer.ensureIndexes()
  }

//  override def onLoadConfig(config: Configuration, path: File, classloader: ClassLoader, mode: Mode): Configuration = {
//    super.onLoadConfig(config, path, classloader, mode)
//  }

  override def standardErrorTemplate(pageTitle: String, heading: String, message: String)(implicit rh: Request[_]): Html =
    views.html.error_template(pageTitle, heading, message)


  override def microserviceMetricsConfig(implicit app: Application): Option[Configuration] = app.configuration.getConfig(s"microservice.metrics")
}

object ControllerConfiguration extends ControllerConfig {
  lazy val controllerConfigs = Play.current.configuration.underlying.as[Config]("controllers")
}

object LoggingFilter extends FrontendLoggingFilter with MicroserviceFilterSupport {
  override def controllerNeedsLogging(controllerName: String) : Boolean = ControllerConfiguration.paramsForController(controllerName).needsLogging
}

object AuditFilter extends FrontendAuditFilter with RunMode with AppName with MicroserviceFilterSupport {

  override lazy val maskedFormFields = Seq("password")

  override lazy val applicationPort = None

  override lazy val auditConnector = FrontendAuditConnector

  override def controllerNeedsAuditing(controllerName: String) : Boolean = ControllerConfiguration.paramsForController(controllerName).needsAuditing
}

object RepositoryEnsurer {

  def ensureIndexes(): Unit = {
    ensureIndexes("NavModelRepo", NavModelRepo.repository)
  }

  def ensureIndexes(name: String, repo: ReactiveRepository[_,_]): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global
    repo.ensureIndexes map {
      r => {
        val indexes = repo.indexes
        Logger.info( s"Ensure Indexes for ${name} returned ${r}" )
        Logger.info( s"Repo ${name} has ${indexes.size} indexes" )
        indexes map { index =>
          val name = index.name.getOrElse("<no-name>")
          Logger.info(s"Repo:${name} Index:${name} Details:${index}")
        }
      }
    }
  }
}

object ProductionFrontendGlobal extends FrontendGlobal {

  override def filters = WhitelistFilter +: super.filters
}
