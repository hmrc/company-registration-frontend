
import play.sbt.PlayImport._
import sbt._

private object AppDependencies {

  val playVersion                 = "-play-30"
  val bootstrapVersion             = "9.7.0"
  val hmrcMongoVersion            = "2.5.0"
  val scalaTestVersion            = "3.2.18"


  val compile = Seq(
    ws,
    "uk.gov.hmrc"             %% s"bootstrap-frontend$playVersion"  %  bootstrapVersion,
    "uk.gov.hmrc.mongo"       %% s"hmrc-mongo$playVersion"          %  hmrcMongoVersion,
    "uk.gov.hmrc"             %% s"play-partials$playVersion"                 % "10.0.0",
    "uk.gov.hmrc"             %% s"http-caching-client$playVersion"           % "12.1.0",
    "uk.gov.hmrc"             %% s"play-conditional-form-mapping$playVersion" % "3.2.0",
    "org.bitbucket.b_c"       %   "jose4j"                                    %  "0.9.6",
    "commons-validator"       %   "commons-validator"                         %  "1.9.0",
    "uk.gov.hmrc"             %%  "play-language"                             %  "8.1.0",
    "uk.gov.hmrc"             %%  s"play-frontend-hmrc$playVersion"           % "11.12.0"
  )

  val test = Seq(
    "uk.gov.hmrc"             %% s"bootstrap-test$playVersion"      %  bootstrapVersion         % Test,
    "uk.gov.hmrc"             %% s"http-verbs-test$playVersion"     % "15.2.0"                  % Test,
    "uk.gov.hmrc.mongo"       %% s"hmrc-mongo-test$playVersion"     %  hmrcMongoVersion         % Test,
    "org.jsoup"               %   "jsoup"                           %  "1.18.3"                 % Test
  )

  def apply() = compile ++ test
}
