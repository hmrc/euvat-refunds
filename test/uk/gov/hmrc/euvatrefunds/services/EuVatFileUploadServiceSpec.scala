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
import uk.gov.hmrc.euvatrefunds.models.responses.UploadSummaryResponse
import uk.gov.hmrc.euvatrefunds.models.{FileStatus, UploadSummary}
import uk.gov.hmrc.euvatrefunds.repositories.EuVatFileUploadRepository
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class EuVatFileUploadServiceSpec extends AnyWordSpec with Matchers with MockitoSugar with ScalaFutures {

  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val mockRepository = mock[EuVatFileUploadRepository]

  private val service = new EuVatFileUploadService(mockRepository)

  "EuVatFileUploadServiceS.getUploadSummary" should {

    val applicationId = 1234L
    val emptyUploadsResponse = UploadSummaryResponse(applicationId = applicationId, uploads = Nil)
    val uploadsResponse = emptyUploadsResponse.copy(
      uploads = Seq(
        UploadSummary(
          reference  = "f5da5578-8393-4cd1-be0e-d8ef1b78d8e7",
          fileStatus = FileStatus.AWAITING_UPLOAD,
          uploadUrl  = Option("https://xxxx/upscan-upload-proxy/bucketName"),
          fields     = Option(Map("acl" -> "private", "x-amz-signature" -> "xxxx", "x-amz-date" -> "yyyyMMddThhmmssZ"))
        )
      )
    )

    "return uploads response when repository returns one" in {
      when(mockRepository.getUploadSummary(mockitoEq(applicationId))(any[ExecutionContext]()))
        .thenReturn(Future.successful(Option(uploadsResponse)))

      val result = service.getUploadSummary(applicationId).futureValue

      result shouldBe Option(uploadsResponse)
    }

    "return empty uploads response when repository returns empty uploads" in {
      when(mockRepository.getUploadSummary(mockitoEq(applicationId))(any[ExecutionContext]()))
        .thenReturn(Future.successful(Option(emptyUploadsResponse)))

      val result = service.getUploadSummary(applicationId).futureValue

      result shouldBe Option(emptyUploadsResponse)
    }

    "return None response when repository returns None" in {
      when(mockRepository.getUploadSummary(mockitoEq(applicationId))(any[ExecutionContext]()))
        .thenReturn(Future.successful(None))

      val result = service.getUploadSummary(applicationId).futureValue

      result shouldBe None
    }

    "propagate error from repository" in {
      val errorMessage = "Repository error"
      when(mockRepository.getUploadSummary(mockitoEq(applicationId))(any[ExecutionContext]()))
        .thenReturn(Future.failed(Exception(errorMessage)))

      val result = service.getUploadSummary(applicationId).failed.futureValue

      result.getMessage shouldBe errorMessage
    }
  }

}
