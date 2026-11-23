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
import uk.gov.hmrc.euvatrefunds.models.upload.{FileStatus, ValidationStatus}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

// one record per upload (_id = uploadId); an application can have several uploads,
// but at most one AWAITING_UPLOAD at a time
@Singleton
class ValidationStatusRepository @Inject() (
  mongoComponent: MongoComponent,
  appConfig: AppConfig
)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[ValidationStatus](
      mongoComponent = mongoComponent,
      collectionName = ValidationStatusRepository.collectionName,
      domainFormat   = ValidationStatus.format,
      indexes        = ValidationStatusRepository.indexes(appConfig.validationStatusTtl.toSeconds),
      replaceIndexes = appConfig.replaceIndexes
    ):

  def findAll(applicationId: Long): Future[Seq[ValidationStatus]] =
    collection.find(byApplicationId(applicationId)).toFuture()

  def findAwaitingUpload(applicationId: Long): Future[Option[ValidationStatus.AwaitingUpload]] =
    collection
      .find(and(byApplicationId(applicationId), awaitingUpload))
      .headOption()
      .map(_.collect { case upload: ValidationStatus.AwaitingUpload => upload })

  def replaceAwaitingUpload(newUpload: ValidationStatus.AwaitingUpload, replacing: Option[String]): Future[Boolean] =
    for
      _        <- replacing.fold(Future.unit)(reference => deleteUpload(newUpload.applicationId, reference))
      inserted <- insert(newUpload)
    yield inserted

  def deleteUploadByReference(applicationId: Long, reference: String): Future[Boolean] =
    deleteUpload(applicationId, reference)

  def deleteApplicationUploads(applicationId: Long): Future[Boolean] =
    collection
      .deleteOne(byApplicationId(applicationId))
      .toFuture()
      .map(_.getDeletedCount > 0)

  private def deleteUpload(applicationId: Long, reference: String) = {
    collection
      .deleteOne(and(byApplicationId(applicationId), equal("reference", reference)))
      .toFuture()
      .map(_.getDeletedCount > 0)
  }

  private def insert(upload: ValidationStatus): Future[Boolean] =
    collection
      .insertOne(upload)
      .toFuture()
      .map(_ => true)
      .recover { case e: MongoWriteException if e.getError.getCategory == ErrorCategory.DUPLICATE_KEY => false }

  private def byApplicationId(applicationId: Long) = equal("applicationId", applicationId)

  private val awaitingUpload = equal("fileStatus", FileStatus.AwaitingUpload.jsonValue)

object ValidationStatusRepository:

  val collectionName: String = "euvat-validation-status"

  def indexes(ttlInSeconds: Long): Seq[IndexModel] = Seq(
    IndexModel(
      keys = ascending("updatedAt"),
      indexOptions = IndexOptions()
        .name("updatedAt-ttl-index")
        .expireAfter(ttlInSeconds, TimeUnit.SECONDS)
    ),
    IndexModel(
      keys         = ascending("applicationId"),
      indexOptions = IndexOptions().name("applicationId-index")
    ),
    IndexModel(
      keys         = ascending("reference"),
      indexOptions = IndexOptions().name("reference-index").unique(true)
    ),
    // stops two requests both storing an in-progress upload for the same application
    IndexModel(
      keys = ascending("applicationId"),
      indexOptions = IndexOptions()
        .name("applicationId-awaiting-upload-index")
        .unique(true)
        .partialFilterExpression(equal("fileStatus", FileStatus.AwaitingUpload.jsonValue))
    )
  )
