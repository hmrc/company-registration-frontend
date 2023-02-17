/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import builders.AuthBuilder
import config.LangConstants
import helpers.SCRSSpec
import models.Language
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.when
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.HeaderNames
import play.api.http.Status.SEE_OTHER
import play.api.i18n.Lang
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.language.LanguageUtils

import scala.concurrent.{ExecutionContext, Future}

class LanguageSwitchControllerSpec extends SCRSSpec with GuiceOneAppPerSuite with AuthBuilder {

  implicit val ec = ExecutionContext.Implicits.global

  object TestLanguageSwitchController extends LanguageSwitchController(
    mockAppConfig,
    app.injector.instanceOf[LanguageUtils],
    mockAuthConnector,
    app.injector.instanceOf[MessagesControllerComponents],
    mockKeystoreConnector,
    mockCompanyRegistrationConnector,
    mockLanguageService
  )

  val regId = "reg12345"

  "LanguageSwitchController" when {

    "calling.setLanguage(language: String)" when {

      "the language feature is enabled" when {

        "the language exists as part of the valid languages in the map" when {

          "user is authorised" when {

            "when registrationID exists" when {

              "a referer is extracted from the request" must {

                "call the backend to update the Language, redirect to the URL of the referer and set the language cookie" in {

                  mockAuthorisedUser(Future.successful({}))
                  mockKeystoreFetchAndGet("registrationID", Some(regId))

                  when(mockAppConfig.languageMap).thenReturn(Map(
                    LangConstants.english -> Lang(LangConstants.english),
                    LangConstants.welsh -> Lang(LangConstants.welsh)
                  ))

                  when(mockAppConfig.languageTranslationEnabled).thenReturn(true)

                  when(mockLanguageService.updateLanguage(eqTo(regId), eqTo(Lang(LangConstants.english)))(any[HeaderCarrier], any[ExecutionContext]))
                    .thenReturn(Future.successful(true))

                  val fakeRequest = FakeRequest("GET", s"/language/${LangConstants.english}").withHeaders(HeaderNames.REFERER -> "/foo/bar/redirect")

                  val result = TestLanguageSwitchController.setLanguage(LangConstants.english)(fakeRequest)

                  status(result) mustBe SEE_OTHER
                  redirectLocation(result) mustBe Some("/foo/bar/redirect")

                  cookies(result).get("PLAY_LANG") match {
                    case Some(cookie) => cookie.value mustBe LangConstants.english
                    case None => fail("No PLAY_LANG cookie was set in the result")
                  }
                }
              }

              "a referer is NOT extracted from the request" must {

                "call the backend to update the Language, redirect to the fallback URL and set the language cookie" in {

                  mockAuthorisedUser(Future.successful({}))
                  mockKeystoreFetchAndGet("registrationID", Some(regId))

                  when(mockAppConfig.languageMap).thenReturn(Map(
                    LangConstants.english -> Lang(LangConstants.english),
                    LangConstants.welsh -> Lang(LangConstants.welsh)
                  ))

                  when(mockAppConfig.languageTranslationEnabled).thenReturn(true)

                  when(mockCompanyRegistrationConnector.updateLanguage(eqTo(regId), eqTo(Language(LangConstants.english)))(any[HeaderCarrier], any[ExecutionContext]))
                    .thenReturn(Future.successful(true))

                  val fakeRequest = FakeRequest("GET", s"/language/${LangConstants.english}")

                  val result = TestLanguageSwitchController.setLanguage(LangConstants.english)(fakeRequest)

                  status(result) mustBe SEE_OTHER
                  redirectLocation(result) mustBe Some(controllers.reg.routes.WelcomeController.show.url)

                  cookies(result).get("PLAY_LANG") match {
                    case Some(cookie) => cookie.value mustBe LangConstants.english
                    case None => fail("No PLAY_LANG cookie was set in the result")
                  }
                }
              }
            }

            "when registrationID does NOT exist" must {

              "redirect to the URL and set the language cookie without calling the backend to update" in {

                mockAuthorisedUser(Future.successful({}))
                mockKeystoreFetchAndGet("registrationID", None)

                when(mockAppConfig.languageMap).thenReturn(Map(
                  LangConstants.english -> Lang(LangConstants.english),
                  LangConstants.welsh -> Lang(LangConstants.welsh)
                ))

                when(mockAppConfig.languageTranslationEnabled).thenReturn(true)

                val fakeRequest = FakeRequest("GET", s"/language/${LangConstants.english}").withHeaders(HeaderNames.REFERER -> "/foo/bar/redirect")

                val result = TestLanguageSwitchController.setLanguage(LangConstants.english)(fakeRequest)

                status(result) mustBe SEE_OTHER
                redirectLocation(result) mustBe Some("/foo/bar/redirect")

                cookies(result).get("PLAY_LANG") match {
                  case Some(cookie) => cookie.value mustBe LangConstants.english
                  case None => fail("No PLAY_LANG cookie was set in the result")
                }
              }
            }
          }

          "use is NOT authorised" must {

            "redirect to the URL and set the language cookie without calling the backend to update" in {

              mockUnauthorisedUser()

              when(mockAppConfig.languageMap).thenReturn(Map(
                LangConstants.english -> Lang(LangConstants.english),
                LangConstants.welsh -> Lang(LangConstants.welsh)
              ))

              when(mockAppConfig.languageTranslationEnabled).thenReturn(true)

              val fakeRequest = FakeRequest("GET", s"/language/${LangConstants.english}").withHeaders(HeaderNames.REFERER -> "/foo/bar/redirect")

              val result = TestLanguageSwitchController.setLanguage(LangConstants.english)(fakeRequest)

              status(result) mustBe SEE_OTHER
              redirectLocation(result) mustBe Some("/foo/bar/redirect")

              cookies(result).get("PLAY_LANG") match {
                case Some(cookie) => cookie.value mustBe LangConstants.english
                case None => fail("No PLAY_LANG cookie was set in the result")
              }
            }
          }
        }

        "the language DOES NOT exist as part of the valid languages in the map" must {

          "not change the language and instead, keep english" in {

            when(mockAppConfig.languageMap).thenReturn(Map(
              LangConstants.english -> Lang(LangConstants.english)
            ))

            when(mockAppConfig.languageTranslationEnabled).thenReturn(true)

            val fakeRequest = FakeRequest("GET", s"/language/${LangConstants.welsh}").withHeaders(HeaderNames.REFERER -> "/foo/bar/redirect")

            val result = TestLanguageSwitchController.setLanguage(LangConstants.welsh)(fakeRequest)

            status(result) mustBe SEE_OTHER
            redirectLocation(result) mustBe Some("/foo/bar/redirect")

            cookies(result).get("PLAY_LANG") match {
              case Some(cookie) => cookie.value mustBe LangConstants.english
              case None => fail("No PLAY_LANG cookie was set in the result")
            }
          }
        }
      }

      "the language switch feature is disabled" must {

        "not change the language and instead, keep english" in {

          when(mockAppConfig.languageMap).thenReturn(Map(
            LangConstants.welsh -> Lang(LangConstants.welsh),
            LangConstants.english -> Lang(LangConstants.english)
          ))

          when(mockAppConfig.languageTranslationEnabled).thenReturn(false)

          val fakeRequest = FakeRequest("GET", s"/language/${LangConstants.welsh}").withHeaders(HeaderNames.REFERER -> "/foo/bar/redirect")

          val result = TestLanguageSwitchController.setLanguage(LangConstants.welsh)(fakeRequest)

          status(result) mustBe SEE_OTHER
          redirectLocation(result) mustBe Some("/foo/bar/redirect")

          cookies(result).get("PLAY_LANG") match {
            case Some(cookie) => cookie.value mustBe LangConstants.english
            case None => fail("No PLAY_LANG cookie was set in the result")
          }
        }
      }
    }
  }
}
