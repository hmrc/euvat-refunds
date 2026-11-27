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

import com.google.inject.ImplementedBy
import uk.gov.hmrc.euvatrefunds.models.upload.{FileStatus, UploadRecord, UpscanCallbackRequest}
import uk.gov.hmrc.euvatrefunds.repositories.UploadRepository

import java.time.Clock
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

sealed trait UpscanCallbackResult

object UpscanCallbackResult:
  case object Accepted           extends UpscanCallbackResult
  case object AlreadyHandled     extends UpscanCallbackResult
  case object StaleReference     extends UpscanCallbackResult
  case object MissingDownloadUrl extends UpscanCallbackResult

@ImplementedBy(classOf[UpscanCallbackServiceImpl])
trait UpscanCallbackService:

  def handleCallback(
    applicationId: Long,
    callback: UpscanCallbackRequest
  ): Future[UpscanCallbackResult]

@Singleton
class UpscanCallbackServiceImpl @Inject() (
  repository: UploadRepository,
  clock: Clock
)(implicit ec: ExecutionContext)
    extends UpscanCallbackService:

  override def handleCallback(
    applicationId: Long,
    callback: UpscanCallbackRequest
  ): Future[UpscanCallbackResult] =
    repository
      .findByReference(
        applicationId,
        callback.reference
      )
      .flatMap {
        case None =>
          Future.successful(
            UpscanCallbackResult.StaleReference
          )

        case Some(stored) if stored.fileStatus != FileStatus.AwaitingUpload =>
          Future.successful(
            UpscanCallbackResult.AlreadyHandled
          )

        case Some(stored) =>
          record(stored, callback)
      }

  private def record(
    stored: UploadRecord,
    callback: UpscanCallbackRequest
  ): Future[UpscanCallbackResult] =
    callback match {
      case UpscanCallbackRequest.Ready(
            _,
            downloadUrl,
            _
          ) if downloadUrl.isBlank =>
        Future.successful(
          UpscanCallbackResult.MissingDownloadUrl
        )

      case UpscanCallbackRequest.Ready(
            _,
            downloadUrl,
            uploadDetails
          ) =>
        store(
          stored.copy(
            fileStatus     = FileStatus.Ready,
            downloadUrl    = Some(downloadUrl),
            uploadDetails  = Some(uploadDetails),
            failureDetails = None,
            updatedAt      = clock.instant()
          )
        )

      case UpscanCallbackRequest.Failed(
            _,
            failureDetails
          ) =>
        store(
          stored.copy(
            fileStatus     = FileStatus.Failed,
            downloadUrl    = None,
            uploadDetails  = None,
            failureDetails = Some(failureDetails),
            updatedAt      = clock.instant()
          )
        )
    }

  private def store(
    upload: UploadRecord
  ): Future[UpscanCallbackResult] =
    repository
      .updateAwaitingUpload(upload)
      .map {
        case true =>
          UpscanCallbackResult.Accepted

        case false =>
          UpscanCallbackResult.AlreadyHandled
      }
