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

package controllers.test

import play.api.mvc.Action
import services.internal.TestIncorporationService
import uk.gov.hmrc.play.frontend.controller.FrontendController

object TestIncorporateController extends TestIncorporateController {

  val checkIncorpService = TestIncorporationService
}

trait TestIncorporateController extends FrontendController {

  val checkIncorpService: TestIncorporationService

  def incorporate(txId: String, accepted: Boolean) = Action.async {
    implicit request =>
      checkIncorpService.incorporateTransactionId(txId, accepted) map { success =>
        Ok(if(success) s"[SUCCESS] incorporating $txId" else s"[FAILED] to incorporate $txId")
      }
  }
}
