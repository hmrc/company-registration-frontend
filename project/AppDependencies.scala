
import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt._

private object AppDependencies {

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "bootstrap-frontend-play-26" % "5.4.0",
    "uk.gov.hmrc" %% "play-partials" % "8.1.0-play-26",
    "uk.gov.hmrc" %% "url-builder" % "3.5.0-play-26",
    "uk.gov.hmrc" %% "http-caching-client" % "9.5.0-play-26",
    "uk.gov.hmrc" %% "play-conditional-form-mapping" % "1.9.0-play-26",
    "org.bitbucket.b_c" % "jose4j" % "0.5.0",
    "uk.gov.hmrc" %% "time" % "3.25.0",
    "uk.gov.hmrc" %% "play-allowlist-filter" % "1.0.0-play-26",
    "commons-validator" % "commons-validator" % "1.6",
    "uk.gov.hmrc" %% "play-language" % "5.1.0-play-26",
    "uk.gov.hmrc" %% "simple-reactivemongo" % "8.0.0-play-26",
    "uk.gov.hmrc" %% "govuk-template" % "5.68.0-play-26",
    "uk.gov.hmrc" %% "play-ui" % "9.5.0-play-26",
    "com.typesafe.play" %% "play-json-joda" % "2.9.2"
  )

  def defaultTest(scope: String) = Seq(
    "org.scalatest" %% "scalatest" % "3.0.8" % scope,
    "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.3" % scope,
    "org.pegdown" % "pegdown" % "1.6.0" % scope,
    "org.jsoup" % "jsoup" % "1.12.1" % scope,
    "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
    "uk.gov.hmrc" %% "hmrctest" % "3.9.0-play-26" % scope,
    "org.mockito" % "mockito-core" % "3.9.0" % scope
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
