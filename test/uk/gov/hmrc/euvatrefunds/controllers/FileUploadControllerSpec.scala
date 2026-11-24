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

import org.mockito.ArgumentMatchers.{any, eq as mockitoEq}
import org.mockito.Mockito.{reset, verify, when}
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
import uk.gov.hmrc.euvatrefunds.models.UploadSummary
import uk.gov.hmrc.euvatrefunds.models.requests.AuthenticatedRequest
import uk.gov.hmrc.euvatrefunds.models.responses.{CreateUploadTrackingResponse, UploadSummaryResponse}
import uk.gov.hmrc.euvatrefunds.models.upload.FileStatus
import uk.gov.hmrc.euvatrefunds.services.{CreateUploadTrackingError, CreateUploadTrackingService, EuVatFileUploadService}
import uk.gov.hmrc.http.SessionId

import scala.concurrent.{ExecutionContext, Future}

class FileUploadControllerSpec extends AnyWordSpec with Matchers with ScalaFutures with MockitoSugar with BeforeAndAfterEach {

  implicit val ec: ExecutionContext = ExecutionContext.global

  private val createUploadTrackingService = mock[CreateUploadTrackingService]
  private val uploadSummaryService = mock[EuVatFileUploadService]

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
    new FileUploadController(authAction, createUploadTrackingService, uploadSummaryService, stubControllerComponents())

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(createUploadTrackingService)
    reset(uploadSummaryService)
  }

  "FileUploadController.createUploadTracking" should {

    val applicationId = 133L
    val request = FakeRequest(POST, s"/$applicationId/create-upload-tracking")

    val response = CreateUploadTrackingResponse(
      uploadId  = "upload-123",
      reference = "ref-123",
      uploadUrl = "https://upscan.example/upload",
      fields    = Map("key" -> "ref-123")
    )

    "return 201 with the upload details when tracking is created" in {
      when(createUploadTrackingService.createUploadTracking(any[Long](), any(), any())(any()))
        .thenReturn(Future.successful(Right(response)))

      val result = controller.createUploadTracking(applicationId, None, None)(request)

      status(result)        shouldBe CREATED
      contentAsJson(result) shouldBe Json.toJson(response)
    }

    "return 404 when the application does not exist" in {
      when(createUploadTrackingService.createUploadTracking(any[Long](), any(), any())(any()))
        .thenReturn(Future.successful(Left(CreateUploadTrackingError.ApplicationNotFound)))

      val result = controller.createUploadTracking(applicationId, None, None)(request)

      status(result)          shouldBe NOT_FOUND
      contentAsString(result) shouldBe s"No application found with id $applicationId"
    }

    "return 500 when the service fails" in {
      when(createUploadTrackingService.createUploadTracking(any[Long](), any(), any())(any()))
        .thenReturn(Future.failed(new RuntimeException("Upscan unavailable")))

      val result = controller.createUploadTracking(applicationId, None, None)(request)

      status(result)          shouldBe INTERNAL_SERVER_ERROR
      contentAsString(result) shouldBe "Failed to create upload tracking"
    }

    "pass the redirect URLs to the service" in {
      when(createUploadTrackingService.createUploadTracking(any[Long](), any(), any())(any()))
        .thenReturn(Future.successful(Right(response)))

      val result = controller.createUploadTracking(
        applicationId,
        Some("http://success"),
        Some("http://error")
      )(request)

      status(result) shouldBe CREATED
      verify(createUploadTrackingService).createUploadTracking(
        mockitoEq(applicationId),
        mockitoEq(Some("http://success")),
        mockitoEq(Some("http://error"))
      )(any())
    }
  }

  "FileUploadController.getUploadSummary" should {

    val applicationId = 1234L
    val request = FakeRequest(GET, s"/$applicationId/get-upload-results")

    val uploadsResponse = UploadSummaryResponse(
      applicationId = applicationId,
      uploads = Seq(
        UploadSummary(
          uploadId   = "upload-1",
          reference  = "f5da5578-8393-4cd1-be0e-d8ef1b78d8e7",
          fileStatus = FileStatus.AwaitingUpload,
          uploadUrl  = Some("https://xxxx/upscan-upload-proxy/bucketName"),
          fields     = Some(Map("acl" -> "private", "x-amz-signature" -> "xxxx"))
        )
      )
    )

    "return 200 with the upload summary" in {
      when(uploadSummaryService.getUploadSummary(mockitoEq(applicationId)))
        .thenReturn(Future.successful(Some(uploadsResponse)))

      val result = controller.getUploadSummary(applicationId)(request)

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(uploadsResponse)
    }

    "return 404 when the application has no uploads" in {
      when(uploadSummaryService.getUploadSummary(any())).thenReturn(Future.successful(None))

      val result = controller.getUploadSummary(applicationId)(request)

      status(result)        shouldBe NOT_FOUND
      contentAsString(result) should include(s"No upload records found for applicationId: $applicationId")
    }

    "return 500 when the service fails" in {
      when(uploadSummaryService.getUploadSummary(any())).thenReturn(Future.failed(new RuntimeException("Service error")))

      val result = controller.getUploadSummary(applicationId)(request)

      status(result)        shouldBe INTERNAL_SERVER_ERROR
      contentAsString(result) should include("Currently experiencing issues with our service")
    }
  }
}
