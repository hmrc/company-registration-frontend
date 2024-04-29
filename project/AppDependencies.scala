
import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt._

private object AppDependencies {

  val playVersion                 = "-play-30"
  val bootsrapVersion             = "8.5.0"
  val hmrcMongoVersion            = "1.7.0"
  val scalaTestVersion            = "3.2.18"


  val compile = Seq(
    ws,
    "uk.gov.hmrc"             %% s"bootstrap-frontend$playVersion"            %  bootsrapVersion,
    "uk.gov.hmrc.mongo"       %% s"hmrc-mongo$playVersion"                    %  hmrcMongoVersion,
    "uk.gov.hmrc"             %% s"play-partials$playVersion"                 % "9.1.0",
    "uk.gov.hmrc"             %% s"http-caching-client$playVersion"           % "11.2.0",
    "uk.gov.hmrc"             %% s"play-conditional-form-mapping$playVersion" % "2.0.0",
    "org.bitbucket.b_c"       %   "jose4j"                                    %  "0.9.6",
    "commons-validator"       %   "commons-validator"                         %  "1.8.0",
    "uk.gov.hmrc"             %%  "play-language"                             %  "7.0.0",
    "uk.gov.hmrc"             %%  s"play-frontend-hmrc$playVersion"           % bootsrapVersion,
  )

  val test = Seq(
    "uk.gov.hmrc"             %% s"bootstrap-test$playVersion"      %  bootsrapVersion          % "test, it",
    "uk.gov.hmrc.mongo"       %% s"hmrc-mongo-test$playVersion"     %  hmrcMongoVersion         % "test, it",
    "org.scalatest"           %%  "scalatest"                       %  scalaTestVersion         % "test, it",
    "org.scalatestplus.play"  %%  "scalatestplus-play"              %  "7.0.1"                  % "test, it",
    "com.vladsch.flexmark"    %   "flexmark-all"                    %  "0.64.8"                 % "test, it",
    "org.scalatestplus"       %%  "mockito-5-10"                     %  "3.2.18.0"               % "test, it",
    "org.jsoup"               %   "jsoup"                           %  "1.17.2"                 % "test, it",
    "com.github.tomakehurst"  %   "wiremock-jre8-standalone"        %  "2.35.0"                 % "it"
  )

  def apply() = compile ++ test
}
