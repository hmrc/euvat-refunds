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
import uk.gov.hmrc.euvatrefunds.models.UploadSummary
import uk.gov.hmrc.euvatrefunds.models.responses.{DeleteUploadTrackingResponse, UploadSummaryResponse}
import uk.gov.hmrc.euvatrefunds.models.upload.ValidationStatus
import uk.gov.hmrc.euvatrefunds.models.upload.ValidationStatus.AwaitingUpload
import uk.gov.hmrc.euvatrefunds.repositories.ValidationStatusRepository

import java.time.{Clock, Instant}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class FileUploadService @Inject() (repository: ValidationStatusRepository, appConfig: AppConfig)(implicit ec: ExecutionContext, clock: Clock)
    extends Logging {

  def getUploadSummary(applicationId: Long): Future[UploadSummaryResponse] =
    repository.findAll(applicationId).flatMap { validationStatuses =>
      val (expiredRecords, nonExpiredRecords) = validationStatuses.partition(isExpiredAwaitingUpload)
      if (expiredRecords.isEmpty) {
        Future.successful(UploadSummaryResponse(applicationId, validationStatuses.map(toSummary)))
      } else {
        val deleteFutures = expiredRecords.map { expiredRecord =>
          deleteUploadTrackingByReference(applicationId, expiredRecord.reference)
        }
        Future.sequence(deleteFutures).map(_ => UploadSummaryResponse(applicationId, nonExpiredRecords.map(toSummary)))
      }
    }

  def deleteUploadTracking(applicationId: Long): Future[DeleteUploadTrackingResponse] =
    repository
      .deleteApplicationUploads(applicationId)
      .map(DeleteUploadTrackingResponse(_))

  private def deleteUploadTrackingByReference(applicationId: Long, reference: String): Future[DeleteUploadTrackingResponse] =
    repository
      .deleteUploadByReference(applicationId, reference)
      .map(DeleteUploadTrackingResponse(_))

  private def toSummary(status: ValidationStatus): UploadSummary =
    status match {
      case upload: ValidationStatus.AwaitingUpload =>
        UploadSummary(upload.uploadId, upload.reference, upload.fileStatus, Some(upload.uploadUrl), Some(upload.fields))
    }

  private def isExpiredAwaitingUpload(validationStatus: ValidationStatus): Boolean = {
    val awaitingUploadExpiryPeriod = appConfig.awaitingUploadExpiryPeriod
    val expiryTime = Instant.now(clock).minus(awaitingUploadExpiryPeriod.length, awaitingUploadExpiryPeriod.unit.toChronoUnit)
    validationStatus match {
      case status: AwaitingUpload =>
        status.initiateTimestamp.isBefore(expiryTime)
    }
  }
}
