import sbt._

object FrontendBuild extends Build with MicroService {
  import scala.util.Properties.envOrElse
  import sbt.Keys._

  val appName = "company-registration-frontend"

  override lazy val appDependencies: Seq[ModuleID] = AppDependencies()

  override lazy val playSettings : Seq[Setting[_]] = Seq(
    dependencyOverrides += "org.scala-lang" % "scala-library" % "2.11.8",
    dependencyOverrides += "uk.gov.hmrc" %% "domain" % "5.0.0",
    dependencyOverrides += "uk.gov.hmrc" %% "secure" % "7.0.0",
    dependencyOverrides += "io.netty" % "netty" % "3.9.8.Final",
    dependencyOverrides += "com.typesafe.play" % "twirl-api_2.11" % "1.1.1"

  )
}

private object AppDependencies {
  import play.core.PlayVersion
  import play.sbt.PlayImport.ws

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "frontend-bootstrap" % "8.11.0",
    "uk.gov.hmrc" %% "play-partials" % "6.1.0",
    "uk.gov.hmrc" %% "url-builder" % "2.1.0",
    "uk.gov.hmrc" %% "http-caching-client" % "7.0.0",
    "uk.gov.hmrc" %% "play-conditional-form-mapping" % "0.2.0",
    "org.bitbucket.b_c" % "jose4j" % "0.5.0",
    "uk.gov.hmrc" %% "time" % "3.1.0",
    "uk.gov.hmrc" %% "play-whitelist-filter" % "2.0.0",
    "commons-validator" % "commons-validator" % "1.6",
    "uk.gov.hmrc" %% "play-language" % "3.4.0",
    "uk.gov.hmrc" %% "play-reactivemongo" % "5.2.0"
  )

  def defaultTest(scope: String) = Seq(
      "org.scalatest" %% "scalatest" % "3.0.1" % scope,
      "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.0" % scope,
      "org.pegdown" % "pegdown" % "1.6.0" % scope,
      "org.jsoup" % "jsoup" % "1.10.2" % scope,
      "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
      "uk.gov.hmrc" %% "reactivemongo-test" % "2.0.0" % scope,
      "uk.gov.hmrc" %% "hmrctest" % "2.3.0" % scope,
      "org.mockito" % "mockito-all" % "1.9.5" % scope
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
