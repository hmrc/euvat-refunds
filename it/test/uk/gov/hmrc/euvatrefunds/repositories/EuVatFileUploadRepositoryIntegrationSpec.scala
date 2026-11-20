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

import org.apache.pekko.actor.ActorSystem
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import uk.gov.hmrc.euvatrefunds.models.{FileStatus, UploadSummaryItem}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import scala.concurrent.ExecutionContext

class EuVatFileUploadRepositoryIntegrationSpec
    extends AnyWordSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[UploadSummaryItem]
    with OptionValues
    with IntegrationPatience
    with GuiceOneServerPerSuite
    with BeforeAndAfterEach {

  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val system: ActorSystem = ActorSystem("file-upload-repository-spec")

  override protected def checkTtlIndex = false

  override protected val repository = new EuVatFileUploadMongoRepository(mongoComponent)

  private val fileUploadRepository = new EuVatFileUploadMongoRepository(mongoComponent)

  override def beforeEach(): Unit = {
    prepareDatabase()
  }

  "EuVatFileUploadRepository.getUploadSummary" should {
    
    "retrieve all uploads for given applicationId from database" in {
      val applicationId1 = 1L
      val uploadSummaryItem1 = createUploadSummaryItem(applicationId1, "ref-1")
      val uploadSummaryItem2 = createUploadSummaryItem(applicationId1, "ref-2")
      val uploadSummaryItem3 = createUploadSummaryItem(2L, "ref-3")

      insertItemsIntoDb(uploadSummaryItem1)
      insertItemsIntoDb(uploadSummaryItem2)
      insertItemsIntoDb(uploadSummaryItem3)

      val result = fileUploadRepository.getUploadSummary(applicationId1)(ec).futureValue

      result shouldBe Seq(uploadSummaryItem1, uploadSummaryItem2)
    }
  }

  "EuVatFileUploadRepository.deleteUploadTracking" should {
    
    "delete all uploads having the given applicationId from database" in {
      val applicationId1 = 1L
      val uploadSummaryItem1 = createUploadSummaryItem(applicationId1, "ref-1")
      val uploadSummaryItem2 = createUploadSummaryItem(applicationId1, "ref-2")
      val uploadSummaryItem3 = createUploadSummaryItem(2L, "ref-3")

      insertItemsIntoDb(uploadSummaryItem1)
      insertItemsIntoDb(uploadSummaryItem2)
      insertItemsIntoDb(uploadSummaryItem3)

      val result = fileUploadRepository.deleteUploadTracking(applicationId1)(ec).futureValue

      result.success shouldBe true
      val itemsPostDeletion = getItemsFromDb(Filters.equal("applicationId", applicationId1))
      itemsPostDeletion shouldBe empty
    }
  }

  "EuVatFileUploadRepository.deleteUploadTrackingByReference" should {
    
    "delete single upload having the given applicationId and reference from database" in {
      val applicationId = 1L
      val reference1 = "ref-1"
      val uploadSummaryItem1 = createUploadSummaryItem(applicationId, reference1)
      val uploadSummaryItem2 = createUploadSummaryItem(applicationId, "ref-2")

      insertItemsIntoDb(uploadSummaryItem1)
      insertItemsIntoDb(uploadSummaryItem2)

      val result = fileUploadRepository.deleteUploadTrackingByReference(applicationId, reference1)(ec).futureValue

      result.success shouldBe true
      val queryFilter = Filters.and(Filters.equal("applicationId", applicationId), Filters.equal("reference", reference1))
      val itemsPostDeletion = getItemsFromDb(queryFilter)
      itemsPostDeletion shouldBe empty
    }
  }

  private def insertItemsIntoDb(item: UploadSummaryItem) =
    repository.collection.insertOne(item).map(_.wasAcknowledged()).head().futureValue

  private def getItemsFromDb(queryFilter: Bson) = repository.collection.find(queryFilter).headOption().futureValue

  private def createUploadSummaryItem(applicationId: Long, reference: String) = {
    UploadSummaryItem(
      applicationId = applicationId,
      reference     = reference,
      fileStatus    = FileStatus.AWAITING_UPLOAD,
      uploadUrl     = Option("https://xxxx/upscan-upload-proxy/bucketName"),
      fields        = Option(Map("acl" -> "private", "x-amz-signature" -> "xxxx", "x-amz-date" -> "yyyyMMddThhmmssZ"))
    )
  }
}
