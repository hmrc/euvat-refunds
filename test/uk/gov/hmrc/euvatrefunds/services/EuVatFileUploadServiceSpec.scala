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
import uk.gov.hmrc.euvatrefunds.models.upload.{FileStatus, ValidationStatus}
import uk.gov.hmrc.euvatrefunds.repositories.ValidationStatusRepository

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

class EuVatFileUploadServiceSpec extends AnyWordSpec with Matchers with MockitoSugar with ScalaFutures with BeforeAndAfterEach {

  implicit val ec: ExecutionContext = ExecutionContext.global

  private val mockRepository = mock[ValidationStatusRepository]

  private val service = new EuVatFileUploadService(mockRepository)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockRepository)
  }

  "EuVatFileUploadService.getUploadSummary" should {

    val applicationId = 1234L
    val timestamp = Instant.parse("2026-11-24T10:00:00Z")

    val storedUpload = ValidationStatus.AwaitingUpload(
      uploadId          = "upload-1",
      applicationId     = applicationId,
      reference         = "f5da5578-8393-4cd1-be0e-d8ef1b78d8e7",
      uploadUrl         = "https://xxxx/upscan-upload-proxy/bucketName",
      initiateTimestamp = timestamp,
      fields            = Map("acl" -> "private", "x-amz-signature" -> "xxxx"),
      createdAt         = timestamp,
      updatedAt         = timestamp
    )

    val expectedSummary = UploadSummary(
      uploadId   = "upload-1",
      reference  = "f5da5578-8393-4cd1-be0e-d8ef1b78d8e7",
      fileStatus = FileStatus.AwaitingUpload,
      uploadUrl  = Some("https://xxxx/upscan-upload-proxy/bucketName"),
      fields     = Some(Map("acl" -> "private", "x-amz-signature" -> "xxxx"))
    )

    "return every upload for the application as a summary" in {
      val secondUpload = storedUpload.copy(uploadId = "upload-2", reference = "ref-2")
      when(mockRepository.findAll(mockitoEq(applicationId)))
        .thenReturn(Future.successful(Seq(storedUpload, secondUpload)))

      service.getUploadSummary(applicationId).futureValue shouldBe Some(
        UploadSummaryResponse(
          applicationId = applicationId,
          uploads       = Seq(expectedSummary, expectedSummary.copy(uploadId = "upload-2", reference = "ref-2"))
        )
      )
    }

    "return None when the application has no uploads" in {
      when(mockRepository.findAll(mockitoEq(applicationId)))
        .thenReturn(Future.successful(Seq.empty))

      service.getUploadSummary(applicationId).futureValue shouldBe None
    }

    "propagate an error from the repository" in {
      when(mockRepository.findAll(mockitoEq(applicationId)))
        .thenReturn(Future.failed(new RuntimeException("Repository error")))

      service.getUploadSummary(applicationId).failed.futureValue.getMessage shouldBe "Repository error"
    }
  }
}
