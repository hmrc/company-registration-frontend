
import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt._

private object AppDependencies {

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "bootstrap-frontend-play-28" % "5.16.0",
    "uk.gov.hmrc" %% "play-partials" % "8.3.0-play-28",
    "uk.gov.hmrc" %% "url-builder" % "3.5.0-play-28",
    "uk.gov.hmrc" %% "http-caching-client" % "9.6.0-play-28",
    "uk.gov.hmrc" %% "play-conditional-form-mapping" % "1.11.0-play-28",
    "org.bitbucket.b_c" % "jose4j" % "0.5.0",
    "uk.gov.hmrc" %% "time" % "3.25.0",
    "uk.gov.hmrc" %% "play-allowlist-filter" % "1.0.0-play-28",
    "commons-validator" % "commons-validator" % "1.6",
    "uk.gov.hmrc" %% "play-language" % "5.1.0-play-28",
    "uk.gov.hmrc" %% "simple-reactivemongo" % "8.0.0-play-28",
    "uk.gov.hmrc" %% "govuk-template" % "5.78.0-play-28",
    "uk.gov.hmrc" %% "play-ui" % "9.10.0-play-28",
    "uk.gov.hmrc" %% "play-frontend-hmrc" % "3.22.0-play-28",
    "uk.gov.hmrc" %% "play-frontend-govuk" % "2.0.0-play-28"
  )

  def defaultTest(scope: String) = Seq(
    "org.jsoup" % "jsoup" % "1.13.1" % scope,
    "org.mockito" % "mockito-core" % "4.1.0" % scope,
    "org.scalatestplus" %% "mockito-3-4" % "3.2.10.0" % scope,
    "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.3" % scope,
    "org.pegdown" % "pegdown" % "1.6.0" % scope,
    "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
    "uk.gov.hmrc" %% "reactivemongo-test" % "5.0.0-play-28" % scope,
    "com.vladsch.flexmark" % "flexmark-all" % "0.62.2" % scope
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
