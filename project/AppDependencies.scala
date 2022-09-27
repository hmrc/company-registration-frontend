
import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt._

private object AppDependencies {

  val playVersion                 = "-play-28"
  val bootsrapVersion             = "5.16.0"
  val hmrcMongoVersion            = "0.73.0"
  val scalaTestVersion            = "3.2.12"

  val compile = Seq(
    ws,
    "uk.gov.hmrc"             %% s"bootstrap-frontend$playVersion"  %  bootsrapVersion,
    "uk.gov.hmrc.mongo"       %% s"hmrc-mongo$playVersion"          %  hmrcMongoVersion,
    "uk.gov.hmrc"             %%  "play-partials"                   % s"8.3.0$playVersion",
    "uk.gov.hmrc"             %%  "url-builder"                     % s"3.5.0$playVersion",
    "uk.gov.hmrc"             %%  "http-caching-client"             % s"9.6.0$playVersion",
    "uk.gov.hmrc"             %%  "play-conditional-form-mapping"   % s"1.11.0$playVersion",
    "org.bitbucket.b_c"       %   "jose4j"                          %  "0.5.0",
    "uk.gov.hmrc"             %%  "time"                            %  "3.25.0",
    "commons-validator"       %   "commons-validator"               %  "1.6",
    "uk.gov.hmrc"             %%  "play-language"                   % s"5.1.0$playVersion",
    "uk.gov.hmrc"             %%  "govuk-template"                  % s"5.78.0$playVersion",
    "uk.gov.hmrc"             %%  "play-ui"                         % s"9.10.0$playVersion",
    "uk.gov.hmrc"             %%  "play-frontend-hmrc"              % s"3.22.0$playVersion",
    "uk.gov.hmrc"             %%  "play-frontend-govuk"             % s"2.0.0$playVersion"
  )

  val test = Seq(
    "uk.gov.hmrc"             %% s"bootstrap-test$playVersion"      %  bootsrapVersion          % "test, it",
    "uk.gov.hmrc.mongo"       %% s"hmrc-mongo-test$playVersion"     %  hmrcMongoVersion         % "test, it",
    "org.scalatest"           %%  "scalatest"                       %  scalaTestVersion         % "test, it",
    "org.scalatestplus.play"  %%  "scalatestplus-play"              %  "5.1.0"                  % "test, it",
    "com.vladsch.flexmark"    %   "flexmark-all"                    %  "0.62.2"                 % "test, it",
    "org.scalatestplus"       %%  "mockito-4-5"                     %  s"$scalaTestVersion.0"   % "test, it",
    "org.jsoup"               %   "jsoup"                           %  "1.13.1"                 % "test, it",
    "com.typesafe.play"       %%  "play-test"                       %  PlayVersion.current      % "test, it",
    "com.github.tomakehurst"  %   "wiremock-jre8"                   %  "2.27.2"                 % "it"
  )

  def apply() = compile ++ test
}
