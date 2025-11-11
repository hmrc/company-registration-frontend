import play.sbt.PlayImport.*
import sbt.*

private object AppDependencies {

  val playVersion      = "-play-30"
  val bootstrapVersion = "10.4.0"
  val hmrcMongoVersion = "2.10.0"

  val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc"       %% s"bootstrap-frontend$playVersion"            % bootstrapVersion,
    "uk.gov.hmrc.mongo" %% s"hmrc-mongo$playVersion"                    % hmrcMongoVersion,
    "uk.gov.hmrc"       %% s"play-partials$playVersion"                 % "10.2.0",
    "uk.gov.hmrc"       %% s"http-caching-client$playVersion"           % "12.2.0",
    "uk.gov.hmrc"       %% s"play-conditional-form-mapping$playVersion" % "3.3.0",
    "org.bitbucket.b_c"  % "jose4j"                                     % "0.9.6",
    "commons-validator"  % "commons-validator"                          % "1.10.0",
    "uk.gov.hmrc"       %% "play-language"                              % "8.1.0",
    "uk.gov.hmrc"       %% s"play-frontend-hmrc$playVersion"            % "12.20.0"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% s"bootstrap-test$playVersion"  % bootstrapVersion % Test,
    "uk.gov.hmrc.mongo" %% s"hmrc-mongo-test$playVersion" % hmrcMongoVersion % Test,
    "org.jsoup"          % "jsoup"                        % "1.21.2"         % Test
  )

  def apply(): Seq[ModuleID] = compile ++ test
}
