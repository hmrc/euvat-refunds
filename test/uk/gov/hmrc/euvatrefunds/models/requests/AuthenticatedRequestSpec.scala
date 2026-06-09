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

package uk.gov.hmrc.euvatrefunds.models.requests

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.test.FakeRequest
import uk.gov.hmrc.http.SessionId

class AuthenticatedRequestSpec extends AnyWordSpec with Matchers {

  "AuthenticatedRequest" should {

    "wrap the underlying request" in {
      val underlying = FakeRequest("GET", "/test")
      val authReq = AuthenticatedRequest(
        underlying,
        credId    = "cred-123",
        sessionId = SessionId("session-xyz")
      )

      authReq.method shouldBe "GET"
      authReq.uri    shouldBe "/test"
    }

    "expose the provided credId and sessionId" in {
      val authReq = AuthenticatedRequest(
        FakeRequest(),
        credId    = "cred-999",
        sessionId = SessionId("session-abc")
      )

      authReq.credId          shouldBe "cred-999"
      authReq.sessionId.value shouldBe "session-abc"
    }

    "produce the correct sessionData string" in {
      val authReq = AuthenticatedRequest(
        FakeRequest(),
        credId    = "cred-111",
        sessionId = SessionId("session-222")
      )

      authReq.sessionData shouldBe s"credId = cred-111, sessionId = $SessionId(session-222)"
    }
  }
}
