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
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
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
import uk.gov.hmrc.euvatrefunds.models.responses.CreateUploadTrackingResponse
import uk.gov.hmrc.euvatrefunds.services.{CreateUploadTrackingError, CreateUploadTrackingService}
import uk.gov.hmrc.http.SessionId

import scala.concurrent.{ExecutionContext, Future}

class FileUploadControllerSpec extends AnyWordSpec with Matchers with ScalaFutures with MockitoSugar with BeforeAndAfterEach {

  implicit val ec: ExecutionContext = ExecutionContext.global
  private val service = mock[CreateUploadTrackingService]

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
    new FileUploadController(authAction, service, stubControllerComponents())

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(service)
  }

  "FileUploadController.createUploadTracking" should {
    val applicationId = 133L
    val request = FakeRequest(POST, s"/$applicationId/create-upload-tracking")

    val response = CreateUploadTrackingResponse(
      reference = "ref-123",
      uploadUrl = "https://upscan.example/upload",
      fields    = Map("key" -> "ref-123")
    )

    "return 201 with the upload details when tracking is created" in {
      when(service.createUploadTracking(any[Long]())(any()))
        .thenReturn(Future.successful(Right(response)))

      val result = controller.createUploadTracking(applicationId)(request)

      status(result)        shouldBe CREATED
      contentAsJson(result) shouldBe Json.toJson(response)
    }

    "return 404 when the application does not exist" in {
      when(service.createUploadTracking(any[Long]())(any()))
        .thenReturn(Future.successful(Left(CreateUploadTrackingError.ApplicationNotFound)))

      val result = controller.createUploadTracking(applicationId)(request)

      status(result)          shouldBe NOT_FOUND
      contentAsString(result) shouldBe s"No application found with id $applicationId"
    }

    "return 500 when the service fails" in {
      when(service.createUploadTracking(any[Long]())(any()))
        .thenReturn(Future.failed(new RuntimeException("Upscan unavailable")))

      val result = controller.createUploadTracking(applicationId)(request)

      status(result)          shouldBe INTERNAL_SERVER_ERROR
      contentAsString(result) shouldBe "Failed to create upload tracking"
    }
  }
}
