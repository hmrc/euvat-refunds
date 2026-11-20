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
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.matchers.should.Matchers.*
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.euvatrefunds.TestUtils
import uk.gov.hmrc.euvatrefunds.actions.AuthAction
import uk.gov.hmrc.euvatrefunds.models.responses.{DeleteUploadTrackingResponse, UploadSummaryResponse}
import uk.gov.hmrc.euvatrefunds.models.{FileStatus, UploadSummary}
import uk.gov.hmrc.euvatrefunds.services.EuVatFileUploadService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class EuVatFileUploadControllerSpec extends AnyWordSpec with Matchers with ScalaFutures with MockitoSugar with BeforeAndAfterEach {

  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val authAction: AuthAction = TestUtils.createAuthAction()
  private val mockFileUploadService = mock[EuVatFileUploadService]
  private val controller = new EuVatFileUploadController(authAction, mockFileUploadService, stubControllerComponents())
  private val applicationId = 1234L
  private val reference = "f5da5578-8393-4cd1-be0e-d8ef1b78d8e7"

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockFileUploadService)
  }

  "EuVatFileUploadController.getUploadSummary" should {

    val emptyUploadsResponse = UploadSummaryResponse(applicationId = applicationId, uploads = Nil)
    val uploadsResponse = emptyUploadsResponse.copy(
      uploads = Seq(
        UploadSummary(
          reference  = reference,
          fileStatus = FileStatus.AWAITING_UPLOAD,
          uploadUrl  = Option("https://xxxx/upscan-upload-proxy/bucketName"),
          fields     = Option(Map("acl" -> "private", "x-amz-signature" -> "xxxx", "x-amz-date" -> "yyyyMMddThhmmssZ"))
        )
      )
    )

    "return 200 with JSON when service returns upload summary with uploads" in {
      when(mockFileUploadService.getUploadSummary(mockitoEq(applicationId)))
        .thenReturn(Future.successful(uploadsResponse))

      val result = controller.getUploadSummary(applicationId)(FakeRequest(GET, s"/$applicationId/get-upload-results"))

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(uploadsResponse)
    }

    "return 200 with JSON when service returns upload summary with empty uploads" in {
      when(mockFileUploadService.getUploadSummary(mockitoEq(applicationId)))
        .thenReturn(Future.successful(emptyUploadsResponse))

      val result = controller.getUploadSummary(applicationId)(FakeRequest(GET, s"/$applicationId/get-upload-results"))

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(emptyUploadsResponse)
    }

    "return 500 when service returns error" in {
      when(mockFileUploadService.getUploadSummary(any())).thenReturn(Future.failed(Exception("Service error")))

      val result = controller.getUploadSummary(applicationId)(FakeRequest(GET, s"/$applicationId/get-upload-results"))

      status(result)        shouldBe INTERNAL_SERVER_ERROR
      contentAsString(result) should include("Currently experiencing issues with our service")
    }
  }

  "EuVatFileUploadController.deleteUploadTracking" should {

    val successfulDeleteUploadTrackingResponse = DeleteUploadTrackingResponse(success = true)
    val failedDeleteUploadTrackingResponse = successfulDeleteUploadTrackingResponse.copy(success = false)

    "return 200 with JSON when service returns success = true" in {
      when(mockFileUploadService.deleteUploadTracking(mockitoEq(applicationId)))
        .thenReturn(Future.successful(successfulDeleteUploadTrackingResponse))

      val result = controller.deleteUploadTracking(applicationId)(FakeRequest(DELETE, s"/$applicationId/delete-upload-tracking"))

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(successfulDeleteUploadTrackingResponse)
    }

    "return 200 when service returns success = false" in {
      when(mockFileUploadService.deleteUploadTracking(mockitoEq(applicationId)))
        .thenReturn(Future.successful(failedDeleteUploadTrackingResponse))

      val result = controller.deleteUploadTracking(applicationId)(FakeRequest(DELETE, s"/$applicationId/delete-upload-tracking"))

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(failedDeleteUploadTrackingResponse)
    }

    "return 500 when service returns error" in {
      when(mockFileUploadService.deleteUploadTracking(any())).thenReturn(Future.failed(Exception("Service error")))

      val result = controller.deleteUploadTracking(applicationId)(FakeRequest(DELETE, s"/$applicationId/delete-upload-tracking"))

      status(result)        shouldBe INTERNAL_SERVER_ERROR
      contentAsString(result) should include("Currently experiencing issues with our service")
    }
  }

  "EuVatFileUploadController.deleteUploadTrackingByReference" should {

    val successfulDeleteUploadTrackingResponse = DeleteUploadTrackingResponse(success = true)
    val failedDeleteUploadTrackingResponse = successfulDeleteUploadTrackingResponse.copy(success = false)

    "return 200 with JSON when service returns success = true" in {
      when(mockFileUploadService.deleteUploadTrackingByReference(mockitoEq(applicationId), mockitoEq(reference)))
        .thenReturn(Future.successful(successfulDeleteUploadTrackingResponse))

      val result = controller.deleteUploadTrackingByReference(applicationId, reference)(
        FakeRequest(DELETE, s"/$applicationId/delete-upload-tracking/$reference")
      )

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(successfulDeleteUploadTrackingResponse)
    }

    "return 200 when service returns success = false" in {
      when(mockFileUploadService.deleteUploadTrackingByReference(mockitoEq(applicationId), mockitoEq(reference)))
        .thenReturn(Future.successful(failedDeleteUploadTrackingResponse))

      val result = controller.deleteUploadTrackingByReference(applicationId, reference)(
        FakeRequest(DELETE, s"/$applicationId/delete-upload-tracking/$reference")
      )

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(failedDeleteUploadTrackingResponse)
    }

    "return 500 when service returns error" in {
      when(mockFileUploadService.deleteUploadTrackingByReference(any(), any())).thenReturn(Future.failed(Exception("Service error")))

      val result = controller.deleteUploadTrackingByReference(applicationId, reference)(
        FakeRequest(DELETE, s"/$applicationId/delete-upload-tracking/$reference")
      )

      status(result)        shouldBe INTERNAL_SERVER_ERROR
      contentAsString(result) should include("Currently experiencing issues with our service")
    }
  }
}
