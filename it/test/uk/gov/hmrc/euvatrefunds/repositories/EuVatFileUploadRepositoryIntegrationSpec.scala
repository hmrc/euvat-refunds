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
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import uk.gov.hmrc.euvatrefunds.models.responses.UploadSummaryResponse
import uk.gov.hmrc.euvatrefunds.models.{FileStatus, UploadSummary, UploadSummaryItem}
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
  private val applicationId = 12345L
  private val uploadSummaryItem = UploadSummaryItem(
    applicationId = applicationId,
    reference     = "f5da5578-8393-4cd1-be0e-d8ef1b78d8e7",
    fileStatus    = FileStatus.AWAITING_UPLOAD,
    uploadUrl     = Option("https://xxxx/upscan-upload-proxy/bucketName"),
    fields        = Option(Map("acl" -> "private", "x-amz-signature" -> "xxxx", "x-amz-date" -> "yyyyMMddThhmmssZ"))
  )

  private val uploadSummaryResponse = UploadSummaryResponse(
    applicationId = applicationId,
    uploads = Seq(
      UploadSummary(
        reference  = uploadSummaryItem.reference,
        fileStatus = uploadSummaryItem.fileStatus,
        uploadUrl  = uploadSummaryItem.uploadUrl,
        fields     = uploadSummaryItem.fields
      )
    )
  )

  override def beforeEach(): Unit = {
    prepareDatabase()
  }

  "EuVatFileUploadRepository" should {
    "retrieve uploads from database" in {
      repository.collection.insertOne(uploadSummaryItem).map(_.wasAcknowledged()).head().futureValue

      val result = fileUploadRepository.getUploadSummary(uploadSummaryItem.applicationId)(ec).futureValue

      result.value shouldBe uploadSummaryResponse
    }
  }
}
