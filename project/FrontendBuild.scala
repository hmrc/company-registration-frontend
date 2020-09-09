import sbt._

object FrontendBuild extends Build with MicroService {

  import sbt.Keys._

  val appName = "company-registration-frontend"

  override lazy val appDependencies: Seq[ModuleID] = AppDependencies()

  override lazy val playSettings: Seq[Setting[_]] = Seq(
    dependencyOverrides += "org.scala-lang" % "scala-library" % "2.11.8",
    dependencyOverrides += "uk.gov.hmrc" %% "domain" % "5.6.0-play-26",
    dependencyOverrides += "uk.gov.hmrc" %% "secure" % "7.11.0",
    dependencyOverrides += "io.netty" % "netty" % "3.9.8.Final"
  )
}

private object AppDependencies {

  import play.core.PlayVersion
  import play.sbt.PlayImport.ws

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "bootstrap-play-26" % "1.14.0",
    "uk.gov.hmrc" %% "auth-client" % "3.0.0-play-26",
    "uk.gov.hmrc" %% "play-partials" % "6.11.0-play-26",
    "uk.gov.hmrc" %% "url-builder" % "3.4.0-play-26",
    "uk.gov.hmrc" %% "http-caching-client" % "9.1.0-play-26",
    "uk.gov.hmrc" %% "play-conditional-form-mapping" % "1.2.0-play-26",
    "org.bitbucket.b_c" % "jose4j" % "0.5.0",
    "uk.gov.hmrc" %% "time" % "3.8.0",
    "uk.gov.hmrc" %% "play-whitelist-filter" % "3.4.0-play-26",
    "commons-validator" % "commons-validator" % "1.6",
    "uk.gov.hmrc" %% "play-language" % "4.3.0-play-26",
    "uk.gov.hmrc" %% "simple-reactivemongo" % "7.30.0-play-26",
    "uk.gov.hmrc" %% "govuk-template" % "5.55.0-play-26",
    "uk.gov.hmrc" %% "play-ui" % "8.11.0-play-26",
    "com.typesafe.play" %% "play-json-joda" % "2.7.4",
    "com.typesafe.akka" %% "akka-stream" % "2.5.23" force(),
    "com.typesafe.akka" %% "akka-protobuf" % "2.5.23" force(),
    "com.typesafe.akka" %% "akka-slf4j" % "2.5.23" force(),
    "com.typesafe.akka" %% "akka-actor" % "2.5.23" force()
  )

  def defaultTest(scope: String) = Seq(
    "org.scalatest" %% "scalatest" % "3.0.8" % scope,
    "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.3" % scope,
    "org.pegdown" % "pegdown" % "1.6.0" % scope,
    "org.jsoup" % "jsoup" % "1.12.1" % scope,
    "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
    "uk.gov.hmrc" %% "hmrctest" % "3.9.0-play-26" % scope,
    "org.mockito" % "mockito-all" % "2.0.2-beta" % scope,
    "uk.gov.hmrc" %% "bootstrap-play-26" % "1.14.0" % scope
  )

  object Test {
    def apply() = defaultTest("test")
  }

  object IntegrationTest {
    def apply() = defaultTest("it") ++ Seq(
      "com.github.tomakehurst" % "wiremock-jre8" % "2.26.3" % "it"
    )
  }

  def apply() = compile ++ Test() ++ IntegrationTest()
}
