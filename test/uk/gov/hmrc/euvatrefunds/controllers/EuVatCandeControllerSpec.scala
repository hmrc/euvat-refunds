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
import uk.gov.hmrc.euvatrefunds.models.requests.{ApplicationRequest, AuthenticatedRequest}
import uk.gov.hmrc.euvatrefunds.models.responses.ApplicationResponse
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

  "EuVatCacheController.createApplication" should {
    val appRequest: ApplicationRequest = ApplicationRequest(
      refundingCountryCode          = Some("FR"),
      periodStartDate               = Some(LocalDateTime.of(2025, 1, 1, 0, 0, 0)),
      periodEndDate                 = Some(LocalDateTime.of(2025, 3, 31, 23, 59, 59)),
      applicantEmailAddress         = Some("test@email.com"),
      applicantTelephoneNumber      = Some("0123456789"),
      applicationLanguage           = Some("EN"),
      businessActivityCode1         = Some("7090"),
      businessActivityCode2         = Some("8903"),
      businessActivityCode3         = None,
      representativeId              = None,
      representativeCountryCode     = None,
      representativeEmailAddress    = None,
      representativeIdType          = None,
      representativeTelephoneNumber = None,
      bankAccountOwnerName          = None,
      bankAccountOwnerType          = None,
      iBanCode                      = None,
      bicCode                       = None,
      bankAccountCurrencyCode       = None
    )
    val response = ApplicationResponse(
      applicationId     = 123456789,
      applicationNumber = "GB123456789",
      updateSeqNumber   = 123
    )

    "return 200 to create refund application" in {
      when(service.createApplication(any(), any())(any()))
        .thenReturn(Future.successful(response))

      val result = controller.createApplication()(FakeRequest(POST, "/create-application").withJsonBody(Json.toJson(appRequest)))

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(response)
    }

    "return 400 when request body is invalid" in {
      val result = controller.createApplication()(
        FakeRequest(POST, "/create-application")
      )

      status(result) shouldBe BAD_REQUEST
    }

    "return 500 and log error when DB call fails" in {
      when(service.createApplication(any(), any())(any()))
        .thenReturn(Future.failed(new RuntimeException("DB error")))
      val result: Future[Result] = controller.createApplication()(
        FakeRequest(POST, "/create-application").withMethod("POST").withJsonBody(Json.toJson(appRequest))
      )

      status(result)        shouldBe INTERNAL_SERVER_ERROR
      contentAsString(result) should include("Failed to create refund application")
    }

  }
}
