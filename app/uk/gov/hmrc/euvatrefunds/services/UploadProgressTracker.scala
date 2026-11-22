/*
 * Copyright 2023 HM Revenue & Customs
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

import org.bson.types.ObjectId
import uk.gov.hmrc.euvatrefunds.models.upscan.{Reference, UploadDetails, UploadStatus}
import uk.gov.hmrc.euvatrefunds.repositories.UserSessionRepository
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClient
import uk.gov.hmrc.objectstore.client.{Path, RetentionPeriod, Sha256Checksum}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UploadProgressTracker @Inject() (
  repository: UserSessionRepository,
  osClient: PlayObjectStoreClient
)(using ExecutionContext):

  def requestUpload(applicationId: Long, fileReference: Reference): Future[Unit] =
    repository.insert(UploadDetails(ObjectId.get(), applicationId, fileReference, UploadStatus.InProgress))

  def registerUploadResult(fileReference: Reference, uploadStatus: UploadStatus)(using hc: HeaderCarrier): Future[Unit] =
    for
      _ <- repository.updateStatus(fileReference, uploadStatus)
      _ <- transferToObjectStore(fileReference, uploadStatus)
    yield ()

  def getUploadResult(id: Long): Future[Option[UploadStatus]] =
    repository
      .findByApplicationId(id)
      .map(_.map(_.status))

  private def transferToObjectStore(fileReference: Reference, uploadStatus: UploadStatus)(using HeaderCarrier): Future[Unit] =
    uploadStatus match
      case details: UploadStatus.UploadedSuccessfully =>
        val fileLocation = Path.File(s"${fileReference.value}/${details.name}")
        val contentSha256 = Sha256Checksum.fromHex(details.checksum)
        osClient
          .uploadFromUrl(
            from            = url"${details.downloadUrl}",
            to              = fileLocation,
            retentionPeriod = RetentionPeriod.OneDay,
            contentType     = Some(details.mimeType),
            contentSha256   = Some(contentSha256)
          )
          .map(_ => ())
      case _ => Future.unit
