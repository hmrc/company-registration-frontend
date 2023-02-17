
import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt._

private object AppDependencies {

  val playVersion                 = "-play-28"
  val bootsrapVersion             = "7.13.0"
  val hmrcMongoVersion            = "0.74.0"
  val scalaTestVersion            = "3.2.15"


  val compile = Seq(
    ws,
    "uk.gov.hmrc"             %% s"bootstrap-frontend$playVersion"  %  bootsrapVersion,
    "uk.gov.hmrc.mongo"       %% s"hmrc-mongo$playVersion"          %  hmrcMongoVersion,
    "uk.gov.hmrc"             %%  "play-partials"                   % s"8.3.0$playVersion",
    "uk.gov.hmrc"             %%  "http-caching-client"             % s"10.0.0$playVersion",
    "uk.gov.hmrc"             %%  "play-conditional-form-mapping"   % s"1.12.0$playVersion",
    "org.bitbucket.b_c"       %   "jose4j"                          %  "0.9.2",
    "commons-validator"       %   "commons-validator"               %  "1.7",
    "uk.gov.hmrc"             %%  "play-language"                   % s"6.1.0$playVersion",
    "uk.gov.hmrc"             %%  "play-frontend-hmrc"              % s"6.3.0$playVersion",
  )

  val test = Seq(
    "uk.gov.hmrc"             %% s"bootstrap-test$playVersion"      %  bootsrapVersion          % "test, it",
    "uk.gov.hmrc.mongo"       %% s"hmrc-mongo-test$playVersion"     %  hmrcMongoVersion         % "test, it",
    "org.scalatest"           %%  "scalatest"                       %  scalaTestVersion         % "test, it",
    "org.scalatestplus.play"  %%  "scalatestplus-play"              %  "5.1.0"                  % "test, it",
    "com.vladsch.flexmark"    %   "flexmark-all"                    %  "0.64.0"                 % "test, it",
    "org.scalatestplus"       %%  "mockito-4-5"                     %  "3.2.12.0"               % "test, it",
    "org.jsoup"               %   "jsoup"                           %  "1.15.3"                 % "test, it",
    "com.typesafe.play"       %%  "play-test"                       %  PlayVersion.current      % "test, it",
    "com.github.tomakehurst"  %   "wiremock-jre8-standalone"        %  "2.35.0"                 % "it"
  )

  def apply() = compile ++ test
}
