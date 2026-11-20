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

import play.api.Logging
import uk.gov.hmrc.euvatrefunds.config.AppConfig
import uk.gov.hmrc.euvatrefunds.models
import uk.gov.hmrc.euvatrefunds.models.FileStatus.AWAITING_UPLOAD
import uk.gov.hmrc.euvatrefunds.models.responses.{DeleteUploadTrackingResponse, UploadSummaryResponse}
import uk.gov.hmrc.euvatrefunds.models.{FileStatus, UploadSummary, UploadSummaryItem}
import uk.gov.hmrc.euvatrefunds.repositories.EuVatFileUploadRepository

import java.time.{Clock, Instant}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class EuVatFileUploadService @Inject() (repository: EuVatFileUploadRepository, appConfig: AppConfig)(implicit ec: ExecutionContext, clock: Clock)
    extends Logging {

  def getUploadSummary(applicationId: Long): Future[UploadSummaryResponse] =
    repository.getUploadSummary(applicationId).flatMap { uploadItems =>
      deleteExpiredUploadSummaries(applicationId, uploadItems).map { remainingUploads =>
        val uploads = remainingUploads.map { uploadItem =>
          UploadSummary(
            reference  = uploadItem.reference,
            fileStatus = uploadItem.fileStatus,
            uploadUrl  = uploadItem.uploadUrl,
            fields     = uploadItem.fields
          )
        }
        UploadSummaryResponse(applicationId, uploads)
      }
    }

  def deleteUploadTracking(applicationId: Long): Future[DeleteUploadTrackingResponse] =
    repository.deleteUploadTracking(applicationId)

  def deleteUploadTrackingByReference(applicationId: Long, reference: String): Future[DeleteUploadTrackingResponse] =
    repository.deleteUploadTrackingByReference(applicationId, reference)

  private def deleteExpiredUploadSummaries(applicationId: Long, uploads: Seq[UploadSummaryItem]): Future[Seq[UploadSummaryItem]] = {
    val (expired, nonExpired) = uploads.partition(isExpired)

    if (expired.isEmpty) {
      Future.successful(uploads)
    } else {
      val deleteFutures = expired.map { status =>
        logger.info(s"Purging expired upload: applicationId: $applicationId, reference: ${status.reference}")
        repository.deleteUploadTrackingByReference(applicationId, status.reference)
      }
      Future.sequence(deleteFutures).map(_ => nonExpired)
    }
  }

  private def isExpired(uploadSummaryItem: UploadSummaryItem): Boolean = {
    val expiryTime = Instant.now(clock).minus(appConfig.uploadTrackingExpiryPeriod)
    val isAwaitingUploadStatus = uploadSummaryItem.fileStatus == AWAITING_UPLOAD
    val wasInitiatedBeforeExpiryPeriod = uploadSummaryItem.initiateTimestamp.exists(_.isBefore(expiryTime))
    isAwaitingUploadStatus && wasInitiatedBeforeExpiryPeriod
  }
}
