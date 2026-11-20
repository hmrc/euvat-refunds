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

package uk.gov.hmrc.euvatrefunds.services

import org.mockito.ArgumentMatchers.{any, eq as mockitoEq}
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.euvatrefunds.config.AppConfig
import uk.gov.hmrc.euvatrefunds.models.responses.{DeleteUploadTrackingResponse, UploadSummaryResponse}
import uk.gov.hmrc.euvatrefunds.models.{FileStatus, UploadSummary, UploadSummaryItem}
import uk.gov.hmrc.euvatrefunds.repositories.EuVatFileUploadRepository
import uk.gov.hmrc.http.HeaderCarrier

import java.time.temporal.ChronoUnit
import java.time.*
import scala.concurrent.{ExecutionContext, Future}

class EuVatFileUploadServiceSpec extends AnyWordSpec with Matchers with MockitoSugar with ScalaFutures {

  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val currentInstant = Instant.now()
  implicit val clock: Clock = Clock.fixed(currentInstant, ZoneId.from(ZoneOffset.UTC))

  private val mockAppConfig = mock[AppConfig]
  private val mockRepository = mock[EuVatFileUploadRepository]

  private val service = new EuVatFileUploadService(mockRepository, mockAppConfig)
  private val applicationId = 1234L

  private val expiryDuration: Duration = Duration.of(3L, ChronoUnit.DAYS)
  private val expiredTimestamp = currentInstant.minus(expiryDuration).minus(1, ChronoUnit.NANOS)
  private val unexpiredTimestamp = currentInstant.minus(expiryDuration)

  when(mockAppConfig.uploadTrackingExpiryPeriod).thenReturn(expiryDuration)

  "EuVatFileUploadServiceS.getUploadSummary" should {

    val emptyUploadsResponse = UploadSummaryResponse(applicationId = applicationId, uploads = Nil)

    "return uploads response and delete items with fileStatus = AWAITING_UPLOAD and initiateTimestamp earlier than upload-tracking.expiry-period" in {

      val uploadItem1 = createUploadItem(applicationId, "1", FileStatus.VERIFYING, Option(expiredTimestamp))
      val uploadItem2 = createUploadItem(applicationId, "2", FileStatus.AWAITING_UPLOAD, Option(expiredTimestamp)) // Expired item will be deleted
      val uploadItem3 = createUploadItem(applicationId, "3", FileStatus.AWAITING_UPLOAD, Option(unexpiredTimestamp))
      val uploadItem4 = createUploadItem(applicationId, "4", FileStatus.VERIFICATION_FAILED, Option(expiredTimestamp))
      val uploadItem5 = createUploadItem(applicationId, "5", FileStatus.VALIDATING, Option(expiredTimestamp))
      val uploadItem6 = createUploadItem(applicationId, "6", FileStatus.VALIDATED, Option(expiredTimestamp))
      val uploadItem7 = createUploadItem(applicationId, "7", FileStatus.VALIDATION_FAILED, Option(expiredTimestamp))

      val uploadSummaryItems = Seq(uploadItem1, uploadItem2, uploadItem3, uploadItem4, uploadItem5, uploadItem6, uploadItem7)

      val expectedUploadsResponse = emptyUploadsResponse.copy(
        uploads = Seq(
          createUploadSummary(uploadItem1),
          createUploadSummary(uploadItem3),
          createUploadSummary(uploadItem4),
          createUploadSummary(uploadItem5),
          createUploadSummary(uploadItem6),
          createUploadSummary(uploadItem7)
        )
      )

      when(mockRepository.getUploadSummary(mockitoEq(applicationId))(any[ExecutionContext]()))
        .thenReturn(Future.successful(uploadSummaryItems))

      when(mockRepository.deleteUploadTrackingByReference(mockitoEq(applicationId), mockitoEq("2"))(any[ExecutionContext]()))
        .thenReturn(Future.successful(DeleteUploadTrackingResponse(success = false)))

      val result = service.getUploadSummary(applicationId).futureValue

      result shouldBe expectedUploadsResponse
    }

    "return empty uploads response when repository returns empty uploads" in {
      when(mockRepository.getUploadSummary(mockitoEq(applicationId))(any[ExecutionContext]()))
        .thenReturn(Future.successful(Nil))

      val result = service.getUploadSummary(applicationId).futureValue

      result shouldBe emptyUploadsResponse
    }

    "propagate error from repository" in {
      val errorMessage = "Repository error"
      when(mockRepository.getUploadSummary(mockitoEq(applicationId))(any[ExecutionContext]()))
        .thenReturn(Future.failed(Exception(errorMessage)))

      val result = service.getUploadSummary(applicationId).failed.futureValue

      result.getMessage shouldBe errorMessage
    }
  }

  "EuVatFileUploadServiceS.deleteUploadTracking" should {

    val successfulDeleteUploadTrackingResponse = DeleteUploadTrackingResponse(success = true)
    val failedDeleteUploadTrackingResponse = successfulDeleteUploadTrackingResponse.copy(success = false)

    "return success = true response when repository returns success = true" in {
      when(mockRepository.deleteUploadTracking(mockitoEq(applicationId))(any[ExecutionContext]()))
        .thenReturn(Future.successful(successfulDeleteUploadTrackingResponse))

      val result = service.deleteUploadTracking(applicationId).futureValue

      result shouldBe successfulDeleteUploadTrackingResponse
    }

    "return success = false response when repository returns success = false" in {
      when(mockRepository.deleteUploadTracking(mockitoEq(applicationId))(any[ExecutionContext]()))
        .thenReturn(Future.successful(failedDeleteUploadTrackingResponse))

      val result = service.deleteUploadTracking(applicationId).futureValue

      result shouldBe failedDeleteUploadTrackingResponse
    }

    "propagate error from repository" in {
      val errorMessage = "Repository error"
      when(mockRepository.deleteUploadTracking(mockitoEq(applicationId))(any[ExecutionContext]()))
        .thenReturn(Future.failed(Exception(errorMessage)))

      val result = service.deleteUploadTracking(applicationId).failed.futureValue

      result.getMessage shouldBe errorMessage
    }
  }

  "EuVatFileUploadServiceS.deleteUploadTrackingByReference" should {
    val reference = "f5da5578-aaaa-bbbb-cccc-d8ef1b78d8e7"

    val successfulDeleteUploadTrackingResponse = DeleteUploadTrackingResponse(success = true)
    val failedDeleteUploadTrackingResponse = successfulDeleteUploadTrackingResponse.copy(success = false)

    "return success = true response when repository returns success = true" in {
      when(mockRepository.deleteUploadTrackingByReference(mockitoEq(applicationId), mockitoEq(reference))(any[ExecutionContext]()))
        .thenReturn(Future.successful(successfulDeleteUploadTrackingResponse))

      val result = service.deleteUploadTrackingByReference(applicationId, reference).futureValue

      result shouldBe successfulDeleteUploadTrackingResponse
    }

    "return success = false response when repository returns success = false" in {
      when(mockRepository.deleteUploadTrackingByReference(mockitoEq(applicationId), mockitoEq(reference))(any[ExecutionContext]()))
        .thenReturn(Future.successful(failedDeleteUploadTrackingResponse))

      val result = service.deleteUploadTrackingByReference(applicationId, reference).futureValue

      result shouldBe failedDeleteUploadTrackingResponse
    }

    "propagate error from repository" in {
      val errorMessage = "Repository error"
      when(mockRepository.deleteUploadTrackingByReference(mockitoEq(applicationId), mockitoEq(reference))(any[ExecutionContext]()))
        .thenReturn(Future.failed(Exception(errorMessage)))

      val result = service.deleteUploadTrackingByReference(applicationId, reference).failed.futureValue

      result.getMessage shouldBe errorMessage
    }
  }

  private def createUploadSummary(uploadSummaryItem: UploadSummaryItem) = {
    UploadSummary(
      reference  = uploadSummaryItem.reference,
      fileStatus = uploadSummaryItem.fileStatus,
      uploadUrl  = uploadSummaryItem.uploadUrl,
      fields     = uploadSummaryItem.fields
    )
  }

  private def createUploadItem(
    applicationId: Long,
    ref: String,
    fileStatus: FileStatus,
    initiateTime: Option[Instant]
  ) = {
    UploadSummaryItem(
      applicationId     = applicationId,
      reference         = ref,
      fileStatus        = fileStatus,
      initiateTimestamp = initiateTime,
      uploadUrl         = Option("https://xxxx/upscan-upload-proxy/bucketName"),
      fields            = Option(Map("acl" -> "private", "x-amz-signature" -> "xxxx", "x-amz-date" -> "yyyyMMddThhmmssZ"))
    )
  }

}
