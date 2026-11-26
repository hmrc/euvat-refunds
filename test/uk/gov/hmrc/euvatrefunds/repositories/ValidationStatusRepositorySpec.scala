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

import com.typesafe.config.ConfigFactory
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import uk.gov.hmrc.euvatrefunds.config.AppConfig
import uk.gov.hmrc.euvatrefunds.models.upload.ValidationStatus
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.Instant
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext.Implicits.global

class ValidationStatusRepositorySpec extends AnyWordSpec with Matchers with OptionValues with DefaultPlayMongoRepositorySupport[ValidationStatus] {

  private val fifteenDaysInSeconds = 1296000L

  private val appConfig = new AppConfig(
    Configuration(
      ConfigFactory.parseString(
        """
          |appName = euvat-refunds
          |mongodb {
          |  replaceIndexes = true
          |  validationStatus.ttl = 15 days
          |}
          |""".stripMargin
      )
    )
  )

  override protected val repository: ValidationStatusRepository =
    new ValidationStatusRepository(mongoComponent, appConfig)

  private val applicationId = 123456L

  // mongo stores milliseconds, so use a fixed instant to make round trips compare equal
  private val timestamp = Instant.parse("2026-11-12T10:00:00Z")

  private val inProgressUpload = ValidationStatus.AwaitingUpload(
    uploadId          = "upload-1",
    applicationId     = applicationId,
    reference         = "ref-1",
    uploadUrl         = "https://upscan.example/upload",
    initiateTimestamp = timestamp,
    fields            = Map("key" -> "ref-1", "x-amz-algorithm" -> "AWS4-HMAC-SHA256"),
    createdAt         = timestamp,
    updatedAt         = timestamp
  )

  private val newerUpload = inProgressUpload.copy(uploadId = "upload-2", reference = "ref-2", fields = Map("key" -> "ref-2"))

  private val otherApplicationUpload =
    inProgressUpload.copy(uploadId = "upload-3", applicationId = 999999L, reference = "ref-3", fields = Map("key" -> "ref-3"))

  "the collection" should {
    "be named euvat-validation-status" in {
      repository.collectionName shouldBe "euvat-validation-status"
    }

    "expire documents 15 days after they were last updated" in {
      val ttlIndex = repository.indexes.find(_.getOptions.getName == "updatedAt-ttl-index").value

      ttlIndex.getOptions.getExpireAfter(TimeUnit.SECONDS) shouldBe fifteenDaysInSeconds
    }
  }

  "ValidationStatusRepository.findAll" should {
    "return nothing when the application has no uploads" in {
      repository.findAll(applicationId).futureValue shouldBe empty
    }

    // TODO: F6.4 - add a finished upload here once there are statuses other than AWAITING_UPLOAD
    "return the uploads for the application only" in {
      insert(inProgressUpload).futureValue
      insert(otherApplicationUpload).futureValue

      repository.findAll(applicationId).futureValue shouldBe Seq(inProgressUpload)
    }
  }

  "ValidationStatusRepository.findAwaitingUpload" should {
    "return nothing when the application has no upload in progress" in {
      repository.findAwaitingUpload(applicationId).futureValue shouldBe None
    }

    "return the upload in progress for the application" in {
      insert(inProgressUpload).futureValue

      repository.findAwaitingUpload(applicationId).futureValue shouldBe Some(inProgressUpload)
    }

    "not return an upload from another application" in {
      insert(otherApplicationUpload).futureValue

      repository.findAwaitingUpload(applicationId).futureValue shouldBe None
    }
  }

  "ValidationStatusRepository.replaceAwaitingUpload" should {
    "store the upload when the application has none in progress" in {
      repository.replaceAwaitingUpload(inProgressUpload, replacing = None).futureValue shouldBe true

      repository.findAll(applicationId).futureValue shouldBe Seq(inProgressUpload)
    }

    "refuse a second upload in progress when another request stored one first" in {
      insert(inProgressUpload).futureValue

      repository.replaceAwaitingUpload(newerUpload, replacing = None).futureValue shouldBe false

      repository.findAll(applicationId).futureValue shouldBe Seq(inProgressUpload)
    }

    "replace the upload in progress it was given" in {
      insert(inProgressUpload).futureValue

      repository.replaceAwaitingUpload(newerUpload, replacing = Some(inProgressUpload.reference)).futureValue shouldBe true

      repository.findAll(applicationId).futureValue shouldBe Seq(newerUpload)
    }

    "refuse to replace when the upload in progress has changed" in {
      insert(inProgressUpload).futureValue

      repository.replaceAwaitingUpload(newerUpload, replacing = Some("a-stale-reference")).futureValue shouldBe false

      repository.findAll(applicationId).futureValue shouldBe Seq(inProgressUpload)
    }

    "leave other applications' uploads alone" in {
      insert(otherApplicationUpload).futureValue

      repository.replaceAwaitingUpload(inProgressUpload, replacing = None).futureValue shouldBe true

      repository.findAll(999999L).futureValue shouldBe Seq(otherApplicationUpload)
    }
  }

  "ValidationStatusRepository.deleteApplicationUploads" should {

    "delete upload having the given applicationId" in {
      val applicationId1 = 1L
      val applicationId2 = 2L
      val upload1 = createUpload("uploadId-1", applicationId1, "ref-1", timestamp)
      val upload3 = createUpload("uploadId-3", applicationId2, "ref-3", timestamp)

      insert(upload1).futureValue
      insert(upload3).futureValue

      val result = repository.deleteApplicationUploads(applicationId1).futureValue

      result              shouldBe true
      findAll().futureValue should contain theSameElementsAs Seq(upload3)
    }
  }

  "ValidationStatusRepository.deleteUploadByReference" should {

    "delete upload having the given applicationId and reference" in {
      val applicationId1 = 1L
      val applicationId2 = 2L
      val upload2 = createUpload("uploadId-2", applicationId1, "ref-2", timestamp)
      val upload3 = createUpload("uploadId-3", applicationId2, "ref-3", timestamp)

      insert(upload2).futureValue
      insert(upload3).futureValue

      val result = repository.deleteUploadByReference(applicationId1, "ref-2").futureValue

      result              shouldBe true
      findAll().futureValue should contain theSameElementsAs Seq(upload3)
    }
  }

  private def createUpload(uploadId: String, applicationId: Long, reference: String, timestamp: Instant) = {
    ValidationStatus.AwaitingUpload(
      uploadId          = uploadId,
      applicationId     = applicationId,
      reference         = reference,
      uploadUrl         = "https://upscan.example/upload",
      initiateTimestamp = timestamp,
      fields            = Map("key" -> "ref-1", "x-amz-algorithm" -> "AWS4-HMAC-SHA256"),
      createdAt         = timestamp,
      updatedAt         = timestamp
    )
  }
}
