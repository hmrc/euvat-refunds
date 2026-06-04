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

package uk.gov.hmrc.euvatrefunds.controllers

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json
import play.api.mvc.{AnyContent, BodyParser, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.euvatrefunds.actions.AuthAction
import uk.gov.hmrc.euvatrefunds.models.requests.AuthenticatedRequest
import uk.gov.hmrc.euvatrefunds.models.responses.TraderKnownFactsResponse
import uk.gov.hmrc.euvatrefunds.services.EuVatRefundService
import uk.gov.hmrc.http.{HeaderCarrier, SessionId}

import scala.concurrent.{ExecutionContext, Future}

class EuVatRefundControllerSpec extends AnyWordSpec with Matchers with ScalaFutures with MockitoSugar {

  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val service = mock[EuVatRefundService]

  // Mock AuthAction so it *invokes the block*
  private val authAction: AuthAction = new AuthAction {
    override def parser: BodyParser[AnyContent] = stubControllerComponents().parsers.defaultBodyParser
    override protected def executionContext: ExecutionContext = ec

    override def invokeBlock[A](
      request: Request[A],
      block: AuthenticatedRequest[A] => Future[Result]
    ): Future[Result] = {
      val fakeAuthReq = AuthenticatedRequest(
        request,
        credId    = "cred-123",
        sessionId = SessionId("session-123")
      )
      block(fakeAuthReq)
    }
  }

  private val controller =
    new EuVatRefundController(authAction, service, stubControllerComponents())

  private val facts = TraderKnownFactsResponse(
    vatRegNumber = 123456789,
    traderName   = Some("ABC GmbH"),
    tradeClass   = Some("8765")
  )

  private def callEndpoint() =
    controller.getKnownFacts()(FakeRequest(GET, "/traders/getKnownFacts"))

  "EuVatRefundController.getKnownFacts" should {

    "return 200 with JSON when service returns known facts" in {
      when(service.retrieveDirectDebits()(any()))
        .thenReturn(Future.successful(facts))

      val result = callEndpoint()

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(facts)
    }

    "return 200 even if tradeClass is missing (controller does not throw)" in {
      when(service.retrieveDirectDebits()(any()))
        .thenReturn(Future.successful(facts.copy(tradeClass = None)))

      val result = callEndpoint()

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(facts.copy(tradeClass = None))
    }

    "return 200 even if VRN is missing (controller does not throw)" in {
      when(service.retrieveDirectDebits()(any()))
        .thenReturn(Future.successful(facts.copy(vatRegNumber = 0)))

      val result = callEndpoint()

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(facts.copy(vatRegNumber = 0))
    }
  }
}
