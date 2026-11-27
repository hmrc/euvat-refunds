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

import uk.gov.hmrc.euvatrefunds.models.responses.UploadStatusResponse
import uk.gov.hmrc.euvatrefunds.models.upload.{FileStatus, UploadRecord}
import uk.gov.hmrc.euvatrefunds.repositories.UploadRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

sealed trait GetUploadStatusResult

object GetUploadStatusResult:

  final case class Found(
    response: UploadStatusResponse
  ) extends GetUploadStatusResult

  case object NotFound extends GetUploadStatusResult

@Singleton
class UploadStatusService @Inject() (
  repository: UploadRepository
)(implicit ec: ExecutionContext):

  def getUploadStatus(
    uploadId: String
  ): Future[GetUploadStatusResult] =
    repository.find(uploadId).map {
      case None =>
        GetUploadStatusResult.NotFound

      case Some(upload) =>
        GetUploadStatusResult.Found(
          toResponse(upload)
        )
    }

  private def toResponse(
    upload: UploadRecord
  ): UploadStatusResponse =
    upload.fileStatus match {
      case FileStatus.AwaitingUpload =>
        UploadStatusResponse(
          fileStatus = FileStatus.AwaitingUpload
        )

      case FileStatus.Ready =>
        UploadStatusResponse(
          fileStatus   = FileStatus.Ready,
          fileName     = upload.uploadDetails.map(_.fileName),
          fileMimeType = upload.uploadDetails.map(_.fileMimeType),
          fileSize     = upload.uploadDetails.map(_.size)
        )

      case FileStatus.Failed =>
        UploadStatusResponse(
          fileStatus     = FileStatus.Failed,
          failureDetails = upload.failureDetails
        )
    }
