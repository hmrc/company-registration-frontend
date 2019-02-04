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

package builders

import org.mockito.Matchers
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import play.api.mvc._
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.{IncorrectCredentialStrength, MissingBearerToken, PlayAuthConnector}

import scala.concurrent.Future

trait AuthBuilder extends MockitoSugar {

  val mockAuthConnector: PlayAuthConnector = mock[PlayAuthConnector]
  val userId = "testUserId"

  def mockAuthorisedUser[A](future: Future[A]) {
    when(mockAuthConnector.authorise[A](Matchers.any[Predicate](), Matchers.any[Retrieval[A]]())(Matchers.any(), Matchers.any())) thenReturn {
      future
    }
  }

  def mockUnauthorisedUser() {
    when(mockAuthConnector.authorise[Unit](Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any())) thenReturn {
      Future.failed(MissingBearerToken(""))
    }
  }

  def mockAuthFailure() {
    when(mockAuthConnector.authorise[Unit](Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any())) thenReturn {
      Future.failed(IncorrectCredentialStrength(""))
    }
  }

  def showWithUnauthorisedUser(action: Action[AnyContent])(test: Future[Result] => Any) {
    mockAuthorisedUser(Future.failed(MissingBearerToken("")))
    val result = action.apply()(FakeRequest())
    test(result)
  }

  def showWithAuthorisedUser(action: Action[AnyContent])(test: Future[Result] => Any) {
    mockAuthorisedUser(Future.successful({}))
    val result = action.apply(SessionBuilder.buildRequestWithSession(userId))
    test(result)
  }


  def showWithAuthorisedUserRetrieval[A](action: Action[AnyContent], returnValue : A)(test: Future[Result] => Any) {
    mockAuthorisedUser(Future.successful(returnValue))
    val result = action.apply(SessionBuilder.buildRequestWithSession(userId))
    test(result)
  }

  def submitWithUnauthorisedUser(action: Action[AnyContent], request: FakeRequest[AnyContentAsFormUrlEncoded])(test: Future[Result] => Any) {
    mockAuthorisedUser(Future.failed(MissingBearerToken("")))
    val result = action.apply(SessionBuilder.updateRequestFormWithSession(request, ""))
    test(result)
  }

  def submitWithAuthorisedUser(action: Action[AnyContent], request: FakeRequest[AnyContentAsFormUrlEncoded])(test: Future[Result] => Any) {
    mockAuthorisedUser(Future.successful({}))
    val result = action.apply(SessionBuilder.updateRequestFormWithSession(request, userId))
    test(result)
  }

  def submitWithAuthorisedUserRetrieval[A](action: Action[AnyContent], request: FakeRequest[AnyContentAsFormUrlEncoded], returnValue : A)
                                          (test: Future[Result] => Any) {
    mockAuthorisedUser(Future.successful(returnValue))
    val result = action.apply(SessionBuilder.updateRequestFormWithSession(request, userId))
    test(result)
  }
}
