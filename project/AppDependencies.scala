
import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt._

private object AppDependencies {

  val playVersion                 = "-play-29"
  val bootsrapVersion             = "8.5.0"
  val hmrcMongoVersion            = "1.7.0"
  val scalaTestVersion            = "3.2.18"


  val compile = Seq(
    ws,
    "uk.gov.hmrc"             %% s"bootstrap-frontend$playVersion"  %  bootsrapVersion,
    "uk.gov.hmrc.mongo"       %% s"hmrc-mongo$playVersion"          %  hmrcMongoVersion,
    "uk.gov.hmrc"             %% s"play-partials$playVersion"                 % "9.1.0",
    "uk.gov.hmrc"             %% s"http-caching-client$playVersion"           % "11.2.0",
    "uk.gov.hmrc"             %% s"play-conditional-form-mapping$playVersion" % "2.0.0",
    "org.bitbucket.b_c"       %   "jose4j"                                    %  "0.9.6",
    "commons-validator"       %   "commons-validator"                         %  "1.8.0",
    "uk.gov.hmrc"             %%  "play-language"                             %  "7.0.0",
    "uk.gov.hmrc"             %%  s"play-frontend-hmrc$playVersion"           % bootsrapVersion
  )

  val test = Seq(
    "uk.gov.hmrc"             %% s"bootstrap-test$playVersion"      %  bootsrapVersion          % Test,
    "uk.gov.hmrc.mongo"       %% s"hmrc-mongo-test$playVersion"     %  hmrcMongoVersion         % Test,
    "org.jsoup"               %   "jsoup"                           %  "1.17.2"                 % Test
  )

  def apply() = compile ++ test
}
