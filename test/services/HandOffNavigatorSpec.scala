/*
 * Copyright 2017 HM Revenue & Customs
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

package services

import connectors.KeystoreConnector
import mocks.NavModelRepoMock
import models.handoff.{HandOffNavModel, NavLinks, Receiver, Sender}
import org.mockito.Matchers
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import org.mockito.Mockito._

import scala.concurrent.Future

class HandOffNavigatorSpec extends UnitSpec with MockitoSugar with WithFakeApplication with BeforeAndAfterEach with NavModelRepoMock{

  val mockNavModelRepoObj = mockNavModelRepo
  val mockKeyStoreConnector = mock[KeystoreConnector]

  override def beforeEach() {
    System.clearProperty("feature.test")
    System.clearProperty("feature.cohoFirstHandOff")
    System.clearProperty("feature.businessActivitiesHandOff")
  }

  class Setup(existsInKeystore:Boolean = true) {

    val navigator = new HandOffNavigator with ServicesConfig{
      override val keystoreConnector = mockKeyStoreConnector
      override val navModelMongo = mockNavModelRepoObj
      override def fetchRegistrationID(implicit hc:HeaderCarrier) = Future.successful("foo")
    }
  }
  class SetupWithMongoRepo {
    val navigator = new HandOffNavigator with ServicesConfig{
      override val keystoreConnector = mockKeyStoreConnector
      override val navModelMongo = mockNavModelRepoObj
      override def fetchRegistrationID(implicit hc:HeaderCarrier) = Future.successful("foo")
    }
  }

  implicit val hc = HeaderCarrier()

  val initNavModel = HandOffNavModel(
    Sender(Map(
      "1" -> NavLinks("http://localhost:9970/register-your-company/corporation-tax-details", "http://localhost:9970/register-your-company/return-to-about-you"),
      "3" -> NavLinks("http://localhost:9970/register-your-company/corporation-tax-summary", "http://localhost:9970/register-your-company/business-activities-back"),
      "5" -> NavLinks("http://localhost:9970/register-your-company/registration-confirmation", "http://localhost:9970/register-your-company/return-to-corporation-tax-summary"),
      "5-2" -> NavLinks("http://localhost:9970/register-your-company/payment-complete",""))),
    Receiver(Map("0" -> NavLinks("http://localhost:9986/incorporation-frontend-stubs/basic-company-details", "")))
  )

  val handOffNavModel = HandOffNavModel(
    Sender(
      Map(
        "1"   -> NavLinks("testForwardLinkFromSender1", "testReverseLinkFromSender1"),
        "3"   -> NavLinks("testForwardLinkFromSender3", "testReverseLinkFromSender3"),
        "5-2" -> NavLinks("testForwardLinkFromSender5.2","")
      )
    ),
    Receiver(
      Map(
        "0"   -> NavLinks("testForwardLinkFromReceiver0", "testReverseLinkFromReceiver0"),
        "2"   -> NavLinks("testForwardLinkFromReceiver2", "testReverseLinkFromReceiver2"),
        "5-1" -> NavLinks("testForwardLinkFromReceiver5.1","")
      ),
      Map("testJumpKey" -> "testJumpLink"),
      Some(Json.parse("""{"testCHBagKey": "testValue"}""").as[JsObject])
    )
  )

  val handOffNavModelWithoutJumpLinks = HandOffNavModel(
    Sender(
      Map(
        "1" -> NavLinks("testForwardLinkFromSender1", "testReverseLinkFromSender1"),
        "3" -> NavLinks("testForwardLinkFromSender3", "testReverseLinkFromSender3")
      )
    ),
    Receiver(
      Map(
        "0" -> NavLinks("testForwardLinkFromReceiver0", "testReverseLinkFromReceiver0"),
        "2" -> NavLinks("testForwardLinkFromReceiver2", "testReverseLinkFromReceiver2")
      ),
      Map.empty,
      Some(Json.parse("""{"testCHBagKey": "testValue"}""").as[JsObject])
    )
  )

  "fetchNavModel" should {

    val cacheMap = CacheMap("testKey", Map("testkey" -> Json.obj()))

    "return the nav model found in Nav Model Repo" in new SetupWithMongoRepo {
      mockGetNavModel(handOffNavModel = Some(handOffNavModel))
      val res =  await(navigator.fetchNavModel())
      res shouldBe handOffNavModel
    }

    "return a new initialised nav model when a nav model cannot be found in keystore OR Mongo" in new SetupWithMongoRepo {
      when(mockKeyStoreConnector.fetchAndGet[HandOffNavModel](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(None))
      mockGetNavModel(handOffNavModel = None)
      mockInsertNavModel()

      await(navigator.fetchNavModel(canCreate = true)) shouldBe initNavModel
    }

  }

  "cacheNavModel" should {

    val cacheMap = CacheMap("HandOffNavigation", Map("testKey" -> Json.parse("""{"testKey":"testValue"}""")))

    "return a HandOffNavModel on a successful insert to mongo" in new SetupWithMongoRepo {
      mockInsertNavModel()
      mockGetNavModel()
      val result = await(navigator.cacheNavModel(handOffNavModel, hc))
      result.left.get shouldBe Some(handOffNavModel)
    }
  }

  "forwardTo" should {

    "return a forward link from the previous receiver" in new Setup {
      val result = await(navigator.forwardTo(1)(handOffNavModel, hc))
      result shouldBe "testForwardLinkFromReceiver0"
    }

    "return a forward link from the previous receiver if a string is passed in" in new Setup {
      val result = await(navigator.forwardTo("1")(handOffNavModel, hc))
      result shouldBe "testForwardLinkFromReceiver0"
    }

    "return a forward link from receiver 5-1 if 5-2 is passed in" in new Setup {
      val result = await(navigator.forwardTo("5-2")(handOffNavModel, hc))
      result shouldBe "testForwardLinkFromReceiver5.1"
    }

    "return a NoSuchElementException if an unknown key is passed" in new Setup {
      val ex = intercept[NoSuchElementException](await(navigator.forwardTo(-1)(handOffNavModel, hc)))
      ex.getMessage shouldBe "key not found: -2"
    }
  }

  "reverseFrom" should {

    "return a reverse link from the current hand off receiver" in new Setup {
      val result = await(navigator.reverseFrom(0)(handOffNavModel, hc))

      result shouldBe "testReverseLinkFromReceiver0"
    }
  }

  "jumpTo" should {

    "return a jump link from the current hand off receiver" in new Setup {
      val result = await(navigator.jumpTo("testJumpKey")(handOffNavModel, hc))

      result shouldBe "testJumpLink"
    }

    "return a NoSuchElementException when a nav model is passed implicitly but does not contain jump links" in new Setup {
      val ex = intercept[NoSuchElementException](await(navigator.jumpTo("testJumpKey")(handOffNavModelWithoutJumpLinks, hc)))

      ex.getMessage shouldBe "key not found: testJumpKey"
    }
  }

  "hmrcLinks" should {
    "return a HandOffNavModel with a forward and reverse link" in new Setup {
      val result = await(navigator.hmrcLinks(1)(handOffNavModel, hc))
      result shouldBe NavLinks("testForwardLinkFromSender1", "testReverseLinkFromSender1")
    }

    "return a HandOffNavModel with a forward and reverse link if a string is passed in" in new Setup {
      val result = await(navigator.hmrcLinks("1")(handOffNavModel, hc))
      result shouldBe NavLinks("testForwardLinkFromSender1", "testReverseLinkFromSender1")
    }
    "return the HO5.2 links if '5.2' is passed in" in new Setup {
      val result = await(navigator.hmrcLinks("5-2")(initNavModel, hc))
      result shouldBe NavLinks("http://localhost:9970/register-your-company/payment-complete","")
    }
  }

  "firstHandOffUrl" should {
    "return a stub url when the feature is disabled" in new Setup {
      System.setProperty("feature.cohoFirstHandOff","false")
      navigator.firstHandoffURL shouldBe "http://localhost:9986/incorporation-frontend-stubs/basic-company-details"
    }

    "return a coho url when the feature is enabled" in new Setup{
      System.setProperty("feature.cohoFirstHandOff","true")
      navigator.firstHandoffURL shouldBe "https://ewfgonzo.companieshouse.gov.uk/incorporation"
    }

    "return a stub url when the feature doesn't exist" in new Setup{
      navigator.firstHandoffURL shouldBe "http://localhost:9986/incorporation-frontend-stubs/basic-company-details"
    }
  }
}
