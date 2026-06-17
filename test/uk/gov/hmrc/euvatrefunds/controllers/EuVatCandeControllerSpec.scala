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
import uk.gov.hmrc.euvatrefunds.models.requests.{AuthenticatedRequest, LatestApplicationRequest}
import uk.gov.hmrc.euvatrefunds.models.responses.{LatestApplicationResponse, TraderKnownFactsResponse}
import uk.gov.hmrc.euvatrefunds.services.EuVatCandeService
import uk.gov.hmrc.http.{HeaderCarrier, SessionId}

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

class EuVatCandeControllerSpec extends AnyWordSpec with Matchers with ScalaFutures with MockitoSugar {

  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val service = mock[EuVatCandeService]

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
        credId          = "cred-123",
        sessionId       = SessionId("session-123"),
        identifierName  = "VatRegNo",
        identifierValue = "999108"
      )
      block(fakeAuthReq)
    }
  }

  private val controller =
    new EuVatCandeController(authAction, service, stubControllerComponents())

  private val facts = TraderKnownFactsResponse(
    vatRegNumber = 123456789,
    traderName   = Some("ABC GmbH"),
    tradeClass   = Some("8765")
  )

  private val sampleRequest = LatestApplicationRequest(
    applicantVatRegNumber = "123456789",
    refundingCountry      = Some("LV"),
    startDate             = Some(LocalDateTime.of(2025, 2, 1, 0, 0)),
    endDate               = Some(LocalDateTime.of(2025, 5, 31, 0, 0)),
    representativeId      = Some("rep123"),
    maxNumber             = 10,
    orderBy               = None,
    sortOrder             = None,
    startAt               = None
  )

  private val sampleResponse = LatestApplicationResponse(
    applications     = List.empty,
    totalApplication = 0
  )

  private def callEndpoint() =
    controller.getKnownFacts()(FakeRequest(GET, "/traders/getKnownFacts"))

  "EuVatCacheController.getKnownFacts" should {

    "return 200 with JSON when service returns known facts" in {
      when(service.retrieveKnownFacts(any())(any()))
        .thenReturn(Future.successful(facts))

      val result = callEndpoint()

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(facts)
    }

    "return 200 even if tradeClass is missing (controller does not throw)" in {
      when(service.retrieveKnownFacts(any())(any()))
        .thenReturn(Future.successful(facts.copy(tradeClass = None)))

      val result = callEndpoint()

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(facts.copy(tradeClass = None))
    }

    "return 200 even if VRN is missing (controller does not throw)" in {
      when(service.retrieveKnownFacts(any())(any()))
        .thenReturn(Future.successful(facts.copy(vatRegNumber = 0)))

      val result = callEndpoint()

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(facts.copy(vatRegNumber = 0))
    }
  }

  "EuVatCandeController.getLatestApplications" should {

    "return 200 with JSON when service returns latest applications" in {
      when(service.getLatestApplications(any())(any()))
        .thenReturn(Future.successful(sampleResponse))

      val result = controller.getLatestApplications()(
        FakeRequest(POST, "/get-latest-application")
          .withJsonBody(Json.toJson(sampleRequest))
      )

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(sampleResponse)
    }

    "return 400 when request body is invalid" in {
      val result = controller.getLatestApplications()(
        FakeRequest(POST, "/get-latest-application")
          .withJsonBody(Json.obj("invalid" -> "body"))
      )

      status(result) shouldBe BAD_REQUEST
    }
  }
}
