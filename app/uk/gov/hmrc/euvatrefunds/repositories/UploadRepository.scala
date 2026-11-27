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

package uk.gov.hmrc.euvatrefunds.repositories

import com.mongodb.ErrorCategory
import org.mongodb.scala.MongoWriteException
import org.mongodb.scala.model.Filters.{and, equal}
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.{IndexModel, IndexOptions}
import uk.gov.hmrc.euvatrefunds.config.AppConfig
import uk.gov.hmrc.euvatrefunds.models.upload.{FileStatus, UploadRecord}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

/*
 * One Mongo record per upload:
 *
 *   _id = uploadId
 *
 * An application can have multiple uploads, but only one upload can have
 * AWAITING_UPLOAD status for a given application at any point in time.
 */
@Singleton
class UploadRepository @Inject() (
  mongoComponent: MongoComponent,
  appConfig: AppConfig
)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[UploadRecord](
      mongoComponent = mongoComponent,
      collectionName = UploadRepository.collectionName,
      domainFormat   = UploadRecord.format,
      indexes = UploadRepository.indexes(
        appConfig.validationStatusTtl.toSeconds
      ),
      replaceIndexes = appConfig.replaceIndexes
    ):

  def findAll(
    applicationId: Long
  ): Future[Seq[UploadRecord]] =
    collection
      .find(byApplicationId(applicationId))
      .toFuture()

  def find(
    uploadId: String
  ): Future[Option[UploadRecord]] =
    collection
      .find(byUploadId(uploadId))
      .headOption()

  def findByReference(
    applicationId: Long,
    reference: String
  ): Future[Option[UploadRecord]] =
    collection
      .find(
        and(
          byApplicationId(applicationId),
          byReference(reference)
        )
      )
      .headOption()

  def findAwaitingUpload(
    applicationId: Long
  ): Future[Option[UploadRecord]] =
    collection
      .find(
        and(
          byApplicationId(applicationId),
          awaitingUpload
        )
      )
      .headOption()

  /*
   * Used when initiating an upload.
   *
   * If `replacing` contains an existing Upscan reference, that old upload
   * record is removed before the new AWAITING_UPLOAD record is inserted.
   *
   * The partial unique index also protects against concurrent requests
   * inserting two AWAITING_UPLOAD records for the same application.
   */
  def replaceAwaitingUpload(
    newUpload: UploadRecord,
    replacing: Option[String]
  ): Future[Boolean] =
    if newUpload.fileStatus != FileStatus.AwaitingUpload then
      Future.failed(
        new IllegalArgumentException(
          s"Expected ${FileStatus.AwaitingUpload.jsonValue} status " +
            s"when creating upload ${newUpload.uploadId}, " +
            s"but found ${newUpload.fileStatus.jsonValue}"
        )
      )
    else
      for
        _ <-
          replacing.fold(Future.unit) { reference =>
            deleteUpload(
              applicationId = newUpload.applicationId,
              reference     = reference
            )
          }

        inserted <- insert(newUpload)
      yield inserted

  /*
   * Used when processing the Upscan callback.
   *
   * This performs an atomic transition:
   *
   *   AWAITING_UPLOAD -> READY
   *
   * or:
   *
   *   AWAITING_UPLOAD -> FAILED
   *
   * The update succeeds only when the current persisted record is still
   * AWAITING_UPLOAD. This prevents a duplicate callback from overwriting
   * an upload that has already been handled.
   */
  def updateAwaitingUpload(
    updatedUpload: UploadRecord
  ): Future[Boolean] =
    validateCallbackStatus(updatedUpload) match
      case Some(error) =>
        Future.failed(error)

      case None =>
        collection
          .replaceOne(
            and(
              byUploadId(updatedUpload.uploadId),
              awaitingUpload
            ),
            updatedUpload
          )
          .toFuture()
          .map(_.getModifiedCount == 1)

  private def validateCallbackStatus(
    upload: UploadRecord
  ): Option[IllegalArgumentException] =
    upload.fileStatus match
      case FileStatus.Ready | FileStatus.Failed =>
        None

      case other =>
        Some(
          new IllegalArgumentException(
            s"Expected ${FileStatus.Ready.jsonValue} or " +
              s"${FileStatus.Failed.jsonValue} status when updating " +
              s"upload ${upload.uploadId}, but found ${other.jsonValue}"
          )
        )

  private def deleteUpload(
    applicationId: Long,
    reference: String
  ): Future[Unit] =
    collection
      .deleteOne(
        and(
          byApplicationId(applicationId),
          byReference(reference)
        )
      )
      .toFuture()
      .map(_ => ())

  private def insert(
    upload: UploadRecord
  ): Future[Boolean] =
    collection
      .insertOne(upload)
      .toFuture()
      .map(_ => true)
      .recover {
        case exception: MongoWriteException if exception.getError.getCategory == ErrorCategory.DUPLICATE_KEY =>
          false
      }

  private def byUploadId(
    uploadId: String
  ) =
    equal("_id", uploadId)

  private def byApplicationId(
    applicationId: Long
  ) =
    equal("applicationId", applicationId)

  private def byReference(
    reference: String
  ) =
    equal("reference", reference)

  private val awaitingUpload =
    equal(
      "fileStatus",
      FileStatus.AwaitingUpload.jsonValue
    )

object UploadRepository:

  /*
   * Keep the existing collection name so this refactor does not create a
   * new Mongo collection or require migration of existing records.
   */
  val collectionName: String =
    "euvat-validation-status"

  def indexes(
    ttlInSeconds: Long
  ): Seq[IndexModel] =
    Seq(
      IndexModel(
        keys = ascending("updatedAt"),
        indexOptions = IndexOptions()
          .name("updatedAt-ttl-index")
          .expireAfter(
            ttlInSeconds,
            TimeUnit.SECONDS
          )
      ),
      IndexModel(
        keys = ascending("applicationId"),
        indexOptions = IndexOptions()
          .name("applicationId-index")
      ),
      IndexModel(
        keys = ascending("reference"),
        indexOptions = IndexOptions()
          .name("reference-index")
          .unique(true)
      ),
      /*
       * Prevents two concurrent requests from storing separate
       * AWAITING_UPLOAD records for the same application.
       *
       * READY and FAILED records are not covered by this unique constraint,
       * so an application can retain multiple completed upload records.
       */
      IndexModel(
        keys = ascending("applicationId"),
        indexOptions = IndexOptions()
          .name("applicationId-awaiting-upload-index")
          .unique(true)
          .partialFilterExpression(
            equal(
              "fileStatus",
              FileStatus.AwaitingUpload.jsonValue
            )
          )
      )
    )
