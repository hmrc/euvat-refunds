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
import uk.gov.hmrc.euvatrefunds.models.upload.ValidationStatus
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

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

  def get(applicationId: Long): Future[Option[ValidationStatus]] =
    collection.find(byApplicationId(applicationId)).headOption()

  // only writes while the application still holds the upload it was based on,
  // so two requests starting an upload at the same time end up with one record
  def replaceUpload(status: ValidationStatus, replacing: Option[String]): Future[Boolean] =
    replacing match
      case Some(reference) =>
        collection
          .replaceOne(and(byApplicationId(status.applicationId), equal("reference", reference)), status)
          .toFuture()
          .map(_.getMatchedCount > 0)
      case None =>
        collection
          .insertOne(status)
          .toFuture()
          .map(_ => true)
          .recover { case e: MongoWriteException if e.getError.getCategory == ErrorCategory.DUPLICATE_KEY => false }

  private def byApplicationId(applicationId: Long) = equal("_id", applicationId)

object ValidationStatusRepository:

  val collectionName: String = "euvat-validation-status"

  def indexes(ttlInSeconds: Long): Seq[IndexModel] = Seq(
    IndexModel(
      keys = ascending("updatedAt"),
      indexOptions = IndexOptions()
        .name("updatedAt-ttl-index")
        .expireAfter(ttlInSeconds, TimeUnit.SECONDS)
    )
  )
