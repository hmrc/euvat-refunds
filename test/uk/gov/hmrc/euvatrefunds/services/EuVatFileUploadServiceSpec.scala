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
import uk.gov.hmrc.euvatrefunds.models.UploadSummary
import uk.gov.hmrc.euvatrefunds.models.responses.UploadSummaryResponse
import uk.gov.hmrc.euvatrefunds.models.upload.{FileStatus, UploadRecord}
import uk.gov.hmrc.euvatrefunds.repositories.UploadRepository

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

class EuVatFileUploadServiceSpec extends AnyWordSpec with Matchers with MockitoSugar with ScalaFutures with BeforeAndAfterEach:

  implicit private val ec: ExecutionContext =
    ExecutionContext.global

  private val repository =
    mock[UploadRepository]

  private val service =
    new EuVatFileUploadService(repository)

  private val applicationId =
    1234L

  private val timestamp =
    Instant.parse("2026-11-24T10:00:00Z")

  private val reference =
    "f5da5578-8393-4cd1-be0e-d8ef1b78d8e7"

  private val uploadUrl =
    "https://xxxx/upscan-upload-proxy/bucketName"

  private val fields =
    Map(
      "acl"             -> "private",
      "x-amz-signature" -> "xxxx"
    )

  private val storedUpload =
    UploadRecord(
      uploadId          = "upload-1",
      applicationId     = applicationId,
      reference         = reference,
      fileStatus        = FileStatus.AwaitingUpload,
      uploadUrl         = uploadUrl,
      initiateTimestamp = timestamp,
      fields            = fields,
      downloadUrl       = None,
      uploadDetails     = None,
      failureDetails    = None,
      createdAt         = timestamp,
      updatedAt         = timestamp
    )

  private val expectedSummary =
    UploadSummary(
      uploadId   = "upload-1",
      reference  = reference,
      fileStatus = FileStatus.AwaitingUpload,
      uploadUrl  = uploadUrl,
      fields     = Some(fields)
    )

  override protected def beforeEach(): Unit =
    super.beforeEach()
    reset(repository)

  "EuVatFileUploadService.getUploadSummary" should {

    "return every upload for the application as a summary" in {
      val secondUpload =
        storedUpload.copy(
          uploadId  = "upload-2",
          reference = "ref-2"
        )

      val secondSummary =
        expectedSummary.copy(
          uploadId  = "upload-2",
          reference = "ref-2"
        )

      when(
        repository.findAll(
          mockitoEq(applicationId)
        )
      ).thenReturn(
        Future.successful(
          Seq(
            storedUpload,
            secondUpload
          )
        )
      )

      val result =
        service
          .getUploadSummary(applicationId)
          .futureValue

      result shouldBe Some(
        UploadSummaryResponse(
          applicationId = applicationId,
          uploads = Seq(
            expectedSummary,
            secondSummary
          )
        )
      )
    }

    "include uploads regardless of their file status" in {
      val readyUpload =
        storedUpload.copy(
          uploadId   = "upload-2",
          reference  = "ref-ready",
          fileStatus = FileStatus.Ready
        )

      val failedUpload =
        storedUpload.copy(
          uploadId   = "upload-3",
          reference  = "ref-failed",
          fileStatus = FileStatus.Failed
        )

      when(
        repository.findAll(
          mockitoEq(applicationId)
        )
      ).thenReturn(
        Future.successful(
          Seq(
            storedUpload,
            readyUpload,
            failedUpload
          )
        )
      )

      val result =
        service
          .getUploadSummary(applicationId)
          .futureValue

      result shouldBe Some(
        UploadSummaryResponse(
          applicationId = applicationId,
          uploads = Seq(
            expectedSummary,
            expectedSummary.copy(
              uploadId   = "upload-2",
              reference  = "ref-ready",
              fileStatus = FileStatus.Ready
            ),
            expectedSummary.copy(
              uploadId   = "upload-3",
              reference  = "ref-failed",
              fileStatus = FileStatus.Failed
            )
          )
        )
      )
    }

    "return None when the application has no uploads" in {
      when(
        repository.findAll(
          mockitoEq(applicationId)
        )
      ).thenReturn(
        Future.successful(Seq.empty)
      )

      val result =
        service
          .getUploadSummary(applicationId)
          .futureValue

      result shouldBe None
    }

    "propagate an error from the repository" in {
      val exception =
        new RuntimeException("Repository error")

      when(
        repository.findAll(
          mockitoEq(applicationId)
        )
      ).thenReturn(
        Future.failed(exception)
      )

      val thrown =
        service
          .getUploadSummary(applicationId)
          .failed
          .futureValue

      thrown shouldBe exception
    }
  }
