/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.euvatrefunds

import play.api.mvc.{AnyContent, BodyParser, Request, Result}
import play.api.test.Helpers.stubControllerComponents
import uk.gov.hmrc.euvatrefunds.actions.AuthAction
import uk.gov.hmrc.euvatrefunds.models.requests.AuthenticatedRequest
import uk.gov.hmrc.http.SessionId

import scala.concurrent.{ExecutionContext, Future}

object TestUtils {

  def createAuthAction(implicit ec: ExecutionContext): AuthAction = {
    new AuthAction {
      override def parser: BodyParser[AnyContent] = stubControllerComponents().parsers.defaultBodyParser

      override protected def executionContext: ExecutionContext = ec

      override def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[Result]): Future[Result] = {
        val fakeAuthReq = AuthenticatedRequest(
          request,
          credId          = "cred-123",
          sessionId       = SessionId("session-123"),
          identifierName  = "VatRegNo",
          identifierValue = "999108"
        )
        block(fakeAuthReq)
      }
    }
  }

}
