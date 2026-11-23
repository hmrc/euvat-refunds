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

import org.mockito.ArgumentMatchers.eq as mockitoEq
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.euvatrefunds.config.AppConfig
import uk.gov.hmrc.euvatrefunds.models.UploadSummary
import uk.gov.hmrc.euvatrefunds.models.responses.{DeleteUploadTrackingResponse, UploadSummaryResponse}
import uk.gov.hmrc.euvatrefunds.models.upload.{FileStatus, ValidationStatus}
import uk.gov.hmrc.euvatrefunds.repositories.ValidationStatusRepository

import java.time.*
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

class FileUploadServiceSpec extends AnyWordSpec with Matchers with MockitoSugar with ScalaFutures with BeforeAndAfterEach {

  implicit val ec: ExecutionContext = ExecutionContext.global

  private val currentInstant = Instant.now()
  implicit val clock: Clock = Clock.fixed(currentInstant, ZoneId.from(ZoneOffset.UTC))

  private val mockRepository = mock[ValidationStatusRepository]
  private lazy val mockAppConfig = mock[AppConfig]

  private val applicationId = 1234L
  private val expiryDuration: FiniteDuration = FiniteDuration(3L, TimeUnit.DAYS)
  private val expiredTimestamp = currentInstant
    .minus(expiryDuration.length, expiryDuration.unit.toChronoUnit)
    .minus(1, ChronoUnit.NANOS)
  private val unexpiredTimestamp = currentInstant
    .minus(expiryDuration.length, expiryDuration.unit.toChronoUnit)
    .plus(1, ChronoUnit.NANOS)

  when(mockAppConfig.awaitingUploadExpiryPeriod).thenReturn(expiryDuration)

  private val service = new FileUploadService(mockRepository, mockAppConfig)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockRepository)
  }

  "FileUploadService.getUploadSummary" should {

    "return uploads for the application as a summary and delete AWAITING_UPLOAD items with initiateTimestamp before upload-tracking.expiry-period" in {

      val unexpiredUpload = createUploadItem("upload-1", applicationId, "ref-1", unexpiredTimestamp)

      val expectedSummary = UploadSummary(
        uploadId   = "upload-1",
        reference  = "ref-1",
        fileStatus = FileStatus.AwaitingUpload,
        uploadUrl  = Some("https://xxxx/upscan-upload-proxy/bucketName"),
        fields     = Some(Map("acl" -> "private", "x-amz-signature" -> "xxxx"))
      )

      val expiredUpload = unexpiredUpload.copy(uploadId = "upload-2", reference = "ref-2", initiateTimestamp = expiredTimestamp)

      when(mockRepository.findAll(mockitoEq(applicationId)))
        .thenReturn(Future.successful(Seq(unexpiredUpload, expiredUpload)))

      when(mockRepository.deleteUploadByReference(mockitoEq(applicationId), mockitoEq("ref-2")))
        .thenReturn(Future.successful(true))

      service.getUploadSummary(applicationId).futureValue shouldBe UploadSummaryResponse(
        applicationId = applicationId,
        uploads       = Seq(expectedSummary)
      )
    }

    "return Nil when the application has no uploads" in {
      when(mockRepository.findAll(mockitoEq(applicationId)))
        .thenReturn(Future.successful(Seq.empty))

      service.getUploadSummary(applicationId).futureValue shouldBe UploadSummaryResponse(applicationId, uploads = Nil)
    }

    "propagate an error from the repository" in {
      when(mockRepository.findAll(mockitoEq(applicationId)))
        .thenReturn(Future.failed(new RuntimeException("Repository error")))

      service.getUploadSummary(applicationId).failed.futureValue.getMessage shouldBe "Repository error"
    }
  }

  "FileUploadService.deleteUploadTracking" should {

    "return success = true response when repository returns success = true" in {
      when(mockRepository.deleteApplicationUploads(mockitoEq(applicationId)))
        .thenReturn(Future.successful(true))

      val result = service.deleteUploadTracking(applicationId).futureValue

      result shouldBe DeleteUploadTrackingResponse(success = true)
    }

    "return success = false response when repository returns success = false" in {
      when(mockRepository.deleteApplicationUploads(mockitoEq(applicationId)))
        .thenReturn(Future.successful(false))

      val result = service.deleteUploadTracking(applicationId).futureValue

      result shouldBe DeleteUploadTrackingResponse(success = false)
    }

    "propagate error from repository" in {
      val errorMessage = "Repository error"
      when(mockRepository.deleteApplicationUploads(mockitoEq(applicationId)))
        .thenReturn(Future.failed(Exception(errorMessage)))

      val result = service.deleteUploadTracking(applicationId).failed.futureValue

      result.getMessage shouldBe errorMessage
    }
  }

  private def createUploadItem(
    uploadId: String,
    applicationId: Long,
    ref: String,
    initiateTime: Instant
  ) = {
    ValidationStatus.AwaitingUpload(
      uploadId          = uploadId,
      applicationId     = applicationId,
      reference         = ref,
      uploadUrl         = "https://xxxx/upscan-upload-proxy/bucketName",
      initiateTimestamp = initiateTime,
      fields            = Map("acl" -> "private", "x-amz-signature" -> "xxxx"),
      createdAt         = initiateTime,
      updatedAt         = initiateTime
    )
  }
}
