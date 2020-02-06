import sbt._

object FrontendBuild extends Build with MicroService {

  import sbt.Keys._

  val appName = "company-registration-frontend"

  override lazy val appDependencies: Seq[ModuleID] = AppDependencies()

  override lazy val playSettings: Seq[Setting[_]] = Seq(
    dependencyOverrides += "org.scala-lang" % "scala-library" % "2.11.8",
    dependencyOverrides += "uk.gov.hmrc" %% "domain" % "5.6.0-play-25",
    dependencyOverrides += "uk.gov.hmrc" %% "secure" % "7.10.0",
    dependencyOverrides += "io.netty" % "netty" % "3.9.8.Final",
    dependencyOverrides += "com.typesafe.play" % "twirl-api_2.11" % "1.1.1"
  )
}

private object AppDependencies {

  import play.core.PlayVersion
  import play.sbt.PlayImport.ws

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "bootstrap-play-25" % "5.1.0",
    "uk.gov.hmrc" %% "auth-client" % "2.32.2-play-25",
    "uk.gov.hmrc" %% "play-partials" % "6.9.0-play-25",
    "uk.gov.hmrc" %% "url-builder" % "3.3.0-play-25",
    "uk.gov.hmrc" %% "http-caching-client" % "9.0.0-play-25",
    "uk.gov.hmrc" %% "play-conditional-form-mapping" % "1.2.0-play-25",
    "org.bitbucket.b_c" % "jose4j" % "0.5.0",
    "uk.gov.hmrc" %% "time" % "3.6.0",
    "uk.gov.hmrc" %% "play-whitelist-filter" % "3.1.0-play-25",
    "commons-validator" % "commons-validator" % "1.6",
    "uk.gov.hmrc" %% "play-language" % "4.2.0-play-25",
    "uk.gov.hmrc" %% "play-reactivemongo" % "6.8.0",
    "uk.gov.hmrc" %% "govuk-template" % "5.48.0-play-25",
    "uk.gov.hmrc" %% "play-ui" % "8.8.0-play-25"
  )

  def defaultTest(scope: String) = Seq(
    "org.scalatest" %% "scalatest" % "3.0.8" % scope,
    "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.0" % scope,
    "org.pegdown" % "pegdown" % "1.6.0" % scope,
    "org.jsoup" % "jsoup" % "1.12.1" % scope,
    "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
    "uk.gov.hmrc" %% "hmrctest" % "3.9.0-play-25" % scope,
    "org.mockito" % "mockito-all" % "2.0.2-beta" % scope
  )

  object Test {
    def apply() = defaultTest("test")
  }

  object IntegrationTest {
    def apply() = defaultTest("it") ++ Seq(
      "com.github.tomakehurst" % "wiremock" % "2.6.0" % "it"
    )
  }

  def apply() = compile ++ Test() ++ IntegrationTest()
}
