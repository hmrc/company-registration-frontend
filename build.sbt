
import play.sbt.PlayImport.PlayKeys
import play.sbt.routes.RoutesCompiler.autoImport._
import sbt.Keys._
import sbt._
import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings.{addTestReportOption, defaultSettings, scalaSettings}
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.versioning.SbtGitVersioning.autoImport.majorVersion
import uk.gov.hmrc.DefaultBuildSettings

val appName = "company-registration-frontend"

lazy val appDependencies: Seq[ModuleID] = AppDependencies()

ThisBuild / majorVersion := 3
ThisBuild / scalaVersion := "2.13.13"

lazy val scoverageSettings = {
  // Semicolon-separated list of regexs matching classes to exclude
  Seq(
    ScoverageKeys.coverageExcludedPackages := "<empty>;Reverse.*;models/.data/..*;view.*;config.*;poc.view.*;poc.config.*;.*(AuthService|BuildInfo|Routes).*",
    ScoverageKeys.coverageMinimumStmtTotal := 80,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true
  )
}

lazy val microservice = Project(appName, file("."))
  .enablePlugins(Seq(PlayScala, SbtDistributablesPlugin) *)
  .settings(scoverageSettings: _*)
  .settings(scalaSettings *)
  .settings(scalacOptions ++= Seq("-feature", "-unchecked", "-deprecation", "-language:reflectiveCalls"))
  .settings(defaultSettings() *)
  .settings(
    scalacOptions += "-Xlint:-unused",
    libraryDependencies ++= appDependencies,
    Test / fork := false,
    retrieveManaged := true,
    routesImport ++= Seq("uk.gov.hmrc.play.bootstrap.binders._"),
  )
  .settings(PlayKeys.devSettings := Seq(
    "pekko.http.parsing.max-uri-length" -> "16k")
  )
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427

lazy val it = project.in(file("it"))
  .enablePlugins(PlayScala)
  .dependsOn(microservice % "test->test") // the "test->test" allows reusing test code and test dependencies
  .settings(DefaultBuildSettings.itSettings(true))
  .settings(
    libraryDependencies ++= appDependencies,
    addTestReportOption(Test, "int-test-reports")
  )