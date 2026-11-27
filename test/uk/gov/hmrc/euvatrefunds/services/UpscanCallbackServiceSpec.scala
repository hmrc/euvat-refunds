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

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{never, reset, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.euvatrefunds.models.upload.*
import uk.gov.hmrc.euvatrefunds.repositories.UploadRepository

import java.time.{Clock, Instant, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UpscanCallbackServiceSpec extends AnyWordSpec with Matchers with ScalaFutures with MockitoSugar with BeforeAndAfterEach:

  private val repository =
    mock[UploadRepository]

  private val uploadId =
    "upload-id-123"

  private val applicationId =
    12345L

  private val reference =
    "reference-123"

  private val uploadUrl =
    "https://example.com/upload"

  private val downloadUrl =
    "https://example.com/download"

  private val createdAt =
    Instant.parse("2026-01-01T10:00:00Z")

  private val uploadTimestamp =
    Instant.parse("2026-01-01T10:01:00Z")

  private val callbackHandledAt =
    Instant.parse("2026-01-01T10:05:00Z")

  private val fields =
    Map(
      "key"    -> reference,
      "policy" -> "test-policy"
    )

  private val clock =
    Clock.fixed(
      callbackHandledAt,
      ZoneOffset.UTC
    )

  private val service =
    new UpscanCallbackServiceImpl(
      repository = repository,
      clock      = clock
    )

  private val awaitingUpload =
    UploadRecord(
      uploadId          = uploadId,
      applicationId     = applicationId,
      reference         = reference,
      fileStatus        = FileStatus.AwaitingUpload,
      uploadUrl         = uploadUrl,
      initiateTimestamp = createdAt,
      fields            = fields,
      downloadUrl       = None,
      uploadDetails     = None,
      failureDetails    = None,
      createdAt         = createdAt,
      updatedAt         = createdAt
    )

  private val uploadDetails =
    UploadDetails(
      fileName        = "test.pdf",
      fileMimeType    = "application/pdf",
      uploadTimestamp = uploadTimestamp,
      checksum        = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
      size            = 987L
    )

  private val readyCallback =
    UpscanCallbackRequest.Ready(
      reference     = reference,
      downloadUrl   = downloadUrl,
      uploadDetails = uploadDetails
    )

  private val failureDetails =
    FailureDetails(
      failureReason = FailureReason.Rejected,
      message       = "MIME type is not allowed"
    )

  private val failedCallback =
    UpscanCallbackRequest.Failed(
      reference      = reference,
      failureDetails = failureDetails
    )

  override protected def beforeEach(): Unit =
    super.beforeEach()
    reset(repository)

  "handleCallback" should {

    "return StaleReference when no upload exists for the application and reference" in {
      when(
        repository.findByReference(
          applicationId,
          reference
        )
      ).thenReturn(Future.successful(None))

      val result =
        service
          .handleCallback(applicationId, readyCallback)
          .futureValue

      result shouldBe UpscanCallbackResult.StaleReference

      verify(repository).findByReference(
        applicationId,
        reference
      )

      verify(repository, never())
        .updateAwaitingUpload(any[UploadRecord])
    }

    "look up the upload using the callback reference" in {
      val callbackReference =
        "another-reference"

      val callback =
        readyCallback.copy(
          reference = callbackReference
        )

      when(
        repository.findByReference(
          applicationId,
          callbackReference
        )
      ).thenReturn(Future.successful(None))

      val result =
        service
          .handleCallback(applicationId, callback)
          .futureValue

      result shouldBe UpscanCallbackResult.StaleReference

      verify(repository).findByReference(
        applicationId,
        callbackReference
      )

      verify(repository, never())
        .updateAwaitingUpload(any[UploadRecord])
    }

    "change an awaiting upload to Ready for a valid READY callback" in {
      when(
        repository.findByReference(
          applicationId,
          reference
        )
      ).thenReturn(Future.successful(Some(awaitingUpload)))

      when(
        repository.updateAwaitingUpload(
          any[UploadRecord]
        )
      ).thenReturn(Future.successful(true))

      val result =
        service
          .handleCallback(applicationId, readyCallback)
          .futureValue

      result shouldBe UpscanCallbackResult.Accepted

      val uploadCaptor =
        ArgumentCaptor.forClass(classOf[UploadRecord])

      verify(repository).updateAwaitingUpload(
        uploadCaptor.capture()
      )

      uploadCaptor.getValue shouldBe awaitingUpload.copy(
        fileStatus     = FileStatus.Ready,
        downloadUrl    = Some(downloadUrl),
        uploadDetails  = Some(uploadDetails),
        failureDetails = None,
        updatedAt      = callbackHandledAt
      )
    }

    "preserve the original upload data for a READY callback" in {
      when(
        repository.findByReference(
          applicationId,
          reference
        )
      ).thenReturn(Future.successful(Some(awaitingUpload)))

      when(
        repository.updateAwaitingUpload(
          any[UploadRecord]
        )
      ).thenReturn(Future.successful(true))

      service
        .handleCallback(applicationId, readyCallback)
        .futureValue shouldBe UpscanCallbackResult.Accepted

      val uploadCaptor =
        ArgumentCaptor.forClass(classOf[UploadRecord])

      verify(repository).updateAwaitingUpload(
        uploadCaptor.capture()
      )

      val stored =
        uploadCaptor.getValue

      stored.uploadId          shouldBe uploadId
      stored.applicationId     shouldBe applicationId
      stored.reference         shouldBe reference
      stored.uploadUrl         shouldBe uploadUrl
      stored.initiateTimestamp shouldBe createdAt
      stored.fields            shouldBe fields
      stored.createdAt         shouldBe createdAt
      stored.updatedAt         shouldBe callbackHandledAt
    }

    "store the download URL and upload details for a READY callback" in {
      when(
        repository.findByReference(
          applicationId,
          reference
        )
      ).thenReturn(Future.successful(Some(awaitingUpload)))

      when(
        repository.updateAwaitingUpload(
          any[UploadRecord]
        )
      ).thenReturn(Future.successful(true))

      service
        .handleCallback(applicationId, readyCallback)
        .futureValue shouldBe UpscanCallbackResult.Accepted

      val uploadCaptor =
        ArgumentCaptor.forClass(classOf[UploadRecord])

      verify(repository).updateAwaitingUpload(
        uploadCaptor.capture()
      )

      val stored =
        uploadCaptor.getValue

      stored.fileStatus     shouldBe FileStatus.Ready
      stored.downloadUrl    shouldBe Some(downloadUrl)
      stored.uploadDetails  shouldBe Some(uploadDetails)
      stored.failureDetails shouldBe None
    }

    "change an awaiting upload to Failed for a valid FAILED callback" in {
      when(
        repository.findByReference(
          applicationId,
          reference
        )
      ).thenReturn(Future.successful(Some(awaitingUpload)))

      when(
        repository.updateAwaitingUpload(
          any[UploadRecord]
        )
      ).thenReturn(Future.successful(true))

      val result =
        service
          .handleCallback(applicationId, failedCallback)
          .futureValue

      result shouldBe UpscanCallbackResult.Accepted

      val uploadCaptor =
        ArgumentCaptor.forClass(classOf[UploadRecord])

      verify(repository).updateAwaitingUpload(
        uploadCaptor.capture()
      )

      uploadCaptor.getValue shouldBe awaitingUpload.copy(
        fileStatus     = FileStatus.Failed,
        downloadUrl    = None,
        uploadDetails  = None,
        failureDetails = Some(failureDetails),
        updatedAt      = callbackHandledAt
      )
    }

    "preserve the original upload data for a FAILED callback" in {
      when(
        repository.findByReference(
          applicationId,
          reference
        )
      ).thenReturn(Future.successful(Some(awaitingUpload)))

      when(
        repository.updateAwaitingUpload(
          any[UploadRecord]
        )
      ).thenReturn(Future.successful(true))

      service
        .handleCallback(applicationId, failedCallback)
        .futureValue shouldBe UpscanCallbackResult.Accepted

      val uploadCaptor =
        ArgumentCaptor.forClass(classOf[UploadRecord])

      verify(repository).updateAwaitingUpload(
        uploadCaptor.capture()
      )

      val stored =
        uploadCaptor.getValue

      stored.uploadId          shouldBe uploadId
      stored.applicationId     shouldBe applicationId
      stored.reference         shouldBe reference
      stored.uploadUrl         shouldBe uploadUrl
      stored.initiateTimestamp shouldBe createdAt
      stored.fields            shouldBe fields
      stored.createdAt         shouldBe createdAt
      stored.updatedAt         shouldBe callbackHandledAt
    }

    "store failure details for a FAILED callback" in {
      when(
        repository.findByReference(
          applicationId,
          reference
        )
      ).thenReturn(Future.successful(Some(awaitingUpload)))

      when(
        repository.updateAwaitingUpload(
          any[UploadRecord]
        )
      ).thenReturn(Future.successful(true))

      service
        .handleCallback(applicationId, failedCallback)
        .futureValue shouldBe UpscanCallbackResult.Accepted

      val uploadCaptor =
        ArgumentCaptor.forClass(classOf[UploadRecord])

      verify(repository).updateAwaitingUpload(
        uploadCaptor.capture()
      )

      val stored =
        uploadCaptor.getValue

      stored.fileStatus     shouldBe FileStatus.Failed
      stored.downloadUrl    shouldBe None
      stored.uploadDetails  shouldBe None
      stored.failureDetails shouldBe Some(failureDetails)
    }

    "return MissingDownloadUrl when a READY callback has an empty download URL" in {
      val callback =
        readyCallback.copy(
          downloadUrl = ""
        )

      when(
        repository.findByReference(
          applicationId,
          reference
        )
      ).thenReturn(Future.successful(Some(awaitingUpload)))

      val result =
        service
          .handleCallback(applicationId, callback)
          .futureValue

      result shouldBe UpscanCallbackResult.MissingDownloadUrl

      verify(repository).findByReference(
        applicationId,
        reference
      )

      verify(repository, never())
        .updateAwaitingUpload(any[UploadRecord])
    }

    "return MissingDownloadUrl when a READY callback has a blank download URL" in {
      val callback =
        readyCallback.copy(
          downloadUrl = "   "
        )

      when(
        repository.findByReference(
          applicationId,
          reference
        )
      ).thenReturn(Future.successful(Some(awaitingUpload)))

      val result =
        service
          .handleCallback(applicationId, callback)
          .futureValue

      result shouldBe UpscanCallbackResult.MissingDownloadUrl

      verify(repository).findByReference(
        applicationId,
        reference
      )

      verify(repository, never())
        .updateAwaitingUpload(any[UploadRecord])
    }

    "return AlreadyHandled when the stored upload is already Ready" in {
      val storedReady =
        awaitingUpload.copy(
          fileStatus     = FileStatus.Ready,
          downloadUrl    = Some(downloadUrl),
          uploadDetails  = Some(uploadDetails),
          failureDetails = None,
          updatedAt      = callbackHandledAt
        )

      when(
        repository.findByReference(
          applicationId,
          reference
        )
      ).thenReturn(Future.successful(Some(storedReady)))

      val result =
        service
          .handleCallback(applicationId, readyCallback)
          .futureValue

      result shouldBe UpscanCallbackResult.AlreadyHandled

      verify(repository).findByReference(
        applicationId,
        reference
      )

      verify(repository, never())
        .updateAwaitingUpload(any[UploadRecord])
    }

    "return AlreadyHandled when the stored upload is already Failed" in {
      val storedFailed =
        awaitingUpload.copy(
          fileStatus     = FileStatus.Failed,
          downloadUrl    = None,
          uploadDetails  = None,
          failureDetails = Some(failureDetails),
          updatedAt      = callbackHandledAt
        )

      when(
        repository.findByReference(
          applicationId,
          reference
        )
      ).thenReturn(Future.successful(Some(storedFailed)))

      val result =
        service
          .handleCallback(applicationId, failedCallback)
          .futureValue

      result shouldBe UpscanCallbackResult.AlreadyHandled

      verify(repository).findByReference(
        applicationId,
        reference
      )

      verify(repository, never())
        .updateAwaitingUpload(any[UploadRecord])
    }

    "return AlreadyHandled when another callback wins the atomic repository update" in {
      when(
        repository.findByReference(
          applicationId,
          reference
        )
      ).thenReturn(Future.successful(Some(awaitingUpload)))

      when(
        repository.updateAwaitingUpload(
          any[UploadRecord]
        )
      ).thenReturn(Future.successful(false))

      val result =
        service
          .handleCallback(applicationId, readyCallback)
          .futureValue

      result shouldBe UpscanCallbackResult.AlreadyHandled

      verify(repository).updateAwaitingUpload(
        any[UploadRecord]
      )
    }

    "propagate a failure from findByReference" in {
      val exception =
        new RuntimeException("Mongo lookup error")

      when(
        repository.findByReference(
          applicationId,
          reference
        )
      ).thenReturn(Future.failed(exception))

      val thrown =
        service
          .handleCallback(applicationId, readyCallback)
          .failed
          .futureValue

      thrown shouldBe exception

      verify(repository, never())
        .updateAwaitingUpload(any[UploadRecord])
    }

    "propagate a failure from updateAwaitingUpload" in {
      val exception =
        new RuntimeException("Mongo update error")

      when(
        repository.findByReference(
          applicationId,
          reference
        )
      ).thenReturn(Future.successful(Some(awaitingUpload)))

      when(
        repository.updateAwaitingUpload(
          any[UploadRecord]
        )
      ).thenReturn(Future.failed(exception))

      val thrown =
        service
          .handleCallback(applicationId, readyCallback)
          .failed
          .futureValue

      thrown shouldBe exception
    }
  }
