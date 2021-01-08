

trait MicroService {

  import play.sbt.PlayImport.PlayKeys
  import sbt.Keys._
  import sbt._
  import scoverage.ScoverageKeys
  import uk.gov.hmrc.DefaultBuildSettings.{addTestReportOption, defaultSettings, integrationTestSettings, scalaSettings}
  import uk.gov.hmrc.{SbtArtifactory, SbtAutoBuildPlugin}
  import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
  import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._
  import uk.gov.hmrc.versioning.SbtGitVersioning
  import uk.gov.hmrc.versioning.SbtGitVersioning.autoImport.majorVersion
  import play.sbt.routes.RoutesCompiler.autoImport._

  val appName: String

  lazy val appDependencies: Seq[ModuleID] = ???
  lazy val plugins: Seq[Plugins] = Seq.empty
  lazy val playSettings: Seq[Setting[_]] = Seq.empty

  lazy val scoverageSettings = {
    // Semicolon-separated list of regexs matching classes to exclude
    import scoverage.ScoverageKeys
    Seq(
      ScoverageKeys.coverageExcludedPackages := "<empty>;Reverse.*;models/.data/..*;view.*;config.*;poc.view.*;poc.config.*;.*(AuthService|BuildInfo|Routes).*",
      ScoverageKeys.coverageMinimum := 80,
      ScoverageKeys.coverageFailOnMinimum := false,
      ScoverageKeys.coverageHighlighting := true
    )
  }

  lazy val microservice = Project(appName, file("."))
    .enablePlugins(Seq(play.sbt.PlayScala,
      SbtAutoBuildPlugin,
      SbtGitVersioning,
      SbtDistributablesPlugin,
      SbtArtifactory)
      ++ plugins: _*)
    .settings(playSettings ++ scoverageSettings: _*)
    .settings(scalaSettings: _*)
    .settings(scalacOptions ++= Seq("-feature", "-unchecked", "-deprecation", "-language:reflectiveCalls"))
    .settings(publishingSettings: _*)
    .settings(defaultSettings(): _*)
    .settings(
      libraryDependencies ++= appDependencies,
      fork in Test := true,
      retrieveManaged := true,
      routesImport ++= Seq("uk.gov.hmrc.play.binders._"),
      scalaVersion := "2.11.11",
      resolvers ++= Seq(Resolver.bintrayRepo("hmrc", "releases"), Resolver.jcenterRepo)
    )
    .settings(PlayKeys.devSettings := Seq(
      "akka.http.parsing.max-uri-length" -> "16k")
    )
    .configs(IntegrationTest)
    .settings(integrationTestSettings())
    .settings(majorVersion := 2)
    .settings(
      Keys.fork in IntegrationTest := true,
      javaOptions in IntegrationTest += "-Dlogger.resource=logback-test.xml",
      parallelExecution in IntegrationTest := false
    )
}