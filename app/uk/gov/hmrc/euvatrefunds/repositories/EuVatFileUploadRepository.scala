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

import com.google.inject.{ImplementedBy, Inject, Singleton}
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.{IndexModel, Indexes}
import play.api.Logging
import uk.gov.hmrc.euvatrefunds.models.responses.UploadSummaryResponse
import uk.gov.hmrc.euvatrefunds.models.{UploadSummary, UploadSummaryItem}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[EuVatFileUploadMongoRepository])
trait EuVatFileUploadRepository {
  def getUploadSummary(applicationId: Long)(implicit ec: ExecutionContext): Future[Option[UploadSummaryResponse]]
}

@Singleton
class EuVatFileUploadMongoRepository @Inject() (mongoComponent: MongoComponent)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[UploadSummaryItem](
      mongoComponent = mongoComponent,
      collectionName = "file-upload-status",
      domainFormat   = UploadSummaryItem.format,
      indexes        = EuVatFileUploadMongoRepository.indexes()
    )
    with EuVatFileUploadRepository
    with Logging {

  override def getUploadSummary(applicationId: Long)(implicit ec: ExecutionContext): Future[Option[UploadSummaryResponse]] =
    collection
      .find(equal("applicationId", applicationId))
      .toFuture()
      .map(_.toSeq)
      .recoverWith { case ex =>
        logger.error(s"Failed to find file upload summary with applicationId: $applicationId", ex)
        Future.failed(ex)
      }
      .map { uploadItems =>
        if (uploadItems.nonEmpty) {
          val uploads = uploadItems.map { uploadItem =>
            UploadSummary(
              reference  = uploadItem.reference,
              fileStatus = uploadItem.fileStatus,
              uploadUrl  = uploadItem.uploadUrl,
              fields     = uploadItem.fields
            )
          }
          Option(UploadSummaryResponse(applicationId, uploads))
        } else None
      }
}

object EuVatFileUploadMongoRepository {

  import org.mongodb.scala.model.IndexOptions
  import org.mongodb.scala.model.Indexes.ascending

  private def indexes(): Seq[IndexModel] = {

    val applicationIdIndex = IndexModel(
      keys         = ascending("applicationId"),
      indexOptions = IndexOptions().name("applicationId-index")
    )

    Seq(applicationIdIndex)
  }
}
