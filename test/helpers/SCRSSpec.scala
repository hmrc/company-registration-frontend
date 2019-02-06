/*
 * Copyright 2019 HM Revenue & Customs
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

package helpers

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import mocks.SCRSMocks
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.util.Random

trait SCRSSpec extends UnitSpec with MockitoSugar with SCRSMocks with BeforeAndAfterEach with JsonHelpers {
  implicit val hc = HeaderCarrier()

  //Until CRFE is fully DI
  when(mockAppConfig.piwikURL).thenReturn(None)

  override def beforeEach() {
    resetMocks()

  }

  private val alpha = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

  def randStr(n:Int) = (1 to n).map(x => alpha(Random.nextInt.abs % alpha.length)).mkString
}

trait TestActorSystem {
  implicit val system = ActorSystem("test")
  implicit val materializer: Materializer = ActorMaterializer()
}