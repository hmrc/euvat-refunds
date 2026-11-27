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

import org.mockito.Mockito.when
import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.{Document, SingleObservableFuture}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.euvatrefunds.config.AppConfig
import uk.gov.hmrc.euvatrefunds.models.upload.*
import uk.gov.hmrc.mongo.test.MongoSupport

import java.time.Instant
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

class UploadRepositorySpec extends AnyWordSpec with Matchers with ScalaFutures with MockitoSugar with MongoSupport with BeforeAndAfterEach:

  implicit private val ec: ExecutionContext =
    ExecutionContext.global

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(
      timeout  = 5.seconds,
      interval = 50.millis
    )

  private val appConfig =
    mock[AppConfig]

  when(appConfig.validationStatusTtl)
    .thenReturn(1.day)

  when(appConfig.replaceIndexes)
    .thenReturn(false)

  private val repository =
    new UploadRepository(
      mongoComponent = mongoComponent,
      appConfig      = appConfig
    )

  private val applicationId =
    12345L

  private val anotherApplicationId =
    67890L

  private val createdAt =
    Instant.parse("2026-11-26T10:00:00Z")

  private val updatedAt =
    Instant.parse("2026-11-26T10:05:00Z")

  private val uploadTimestamp =
    Instant.parse("2026-11-26T10:02:00Z")

  private val uploadUrl =
    "https://example.com/upload"

  private val downloadUrl =
    "https://example.com/download"

  private val fields =
    Map(
      "key"    -> "reference-123",
      "policy" -> "test-policy"
    )

  private val uploadDetails =
    UploadDetails(
      fileName        = "test.pdf",
      fileMimeType    = "application/pdf",
      uploadTimestamp = uploadTimestamp,
      checksum        = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
      size            = 987L
    )

  private val failureDetails =
    FailureDetails(
      failureReason = FailureReason.Rejected,
      message       = "MIME type is not allowed"
    )

  private val awaitingUpload =
    UploadRecord(
      uploadId          = "upload-123",
      applicationId     = applicationId,
      reference         = "reference-123",
      fileStatus        = FileStatus.AwaitingUpload,
      uploadUrl         = uploadUrl,
      initiateTimestamp = createdAt,
      fields            = fields,
      downloadUrl       = None,
      uploadDetails     = None,
      failureDetails    = None,
      createdAt         = createdAt,
      updatedAt         = createdAt
    )

  override protected def beforeEach(): Unit =
    super.beforeEach()

    repository.collection
      .deleteMany(Document())
      .toFuture()
      .futureValue

  private def insert(
    upload: UploadRecord
  ): Unit =
    repository.collection
      .insertOne(upload)
      .toFuture()
      .futureValue

  "findAll" should {

    "return every upload belonging to the application" in {
      val readyUpload =
        awaitingUpload.copy(
          uploadId      = "upload-ready",
          reference     = "reference-ready",
          fileStatus    = FileStatus.Ready,
          downloadUrl   = Some(downloadUrl),
          uploadDetails = Some(uploadDetails),
          updatedAt     = updatedAt
        )

      val failedUpload =
        awaitingUpload.copy(
          uploadId       = "upload-failed",
          reference      = "reference-failed",
          fileStatus     = FileStatus.Failed,
          failureDetails = Some(failureDetails),
          updatedAt      = updatedAt
        )

      val anotherApplicationUpload =
        awaitingUpload.copy(
          uploadId      = "upload-other-application",
          applicationId = anotherApplicationId,
          reference     = "reference-other-application"
        )

      insert(readyUpload)
      insert(failedUpload)
      insert(anotherApplicationUpload)

      repository
        .findAll(applicationId)
        .futureValue
        .toSet shouldBe Set(
        readyUpload,
        failedUpload
      )
    }

    "return an empty sequence when the application has no uploads" in {
      repository
        .findAll(applicationId)
        .futureValue shouldBe Seq.empty
    }
  }

  "find" should {

    "return the upload matching the upload ID" in {
      insert(awaitingUpload)

      repository
        .find(awaitingUpload.uploadId)
        .futureValue shouldBe Some(awaitingUpload)
    }

    "return None when the upload ID does not exist" in {
      repository
        .find("unknown-upload")
        .futureValue shouldBe None
    }
  }

  "findByReference" should {

    "return the upload matching both application ID and reference" in {
      insert(awaitingUpload)

      repository
        .findByReference(
          applicationId = applicationId,
          reference     = awaitingUpload.reference
        )
        .futureValue shouldBe Some(awaitingUpload)
    }

    "return None when the reference belongs to another application" in {
      insert(awaitingUpload)

      repository
        .findByReference(
          applicationId = anotherApplicationId,
          reference     = awaitingUpload.reference
        )
        .futureValue shouldBe None
    }

    "return None when the reference does not exist" in {
      repository
        .findByReference(
          applicationId = applicationId,
          reference     = "unknown-reference"
        )
        .futureValue shouldBe None
    }
  }

  "findAwaitingUpload" should {

    "return the awaiting upload for the application" in {
      insert(awaitingUpload)

      repository
        .findAwaitingUpload(applicationId)
        .futureValue shouldBe Some(awaitingUpload)
    }

    "ignore Ready and Failed uploads" in {
      val readyUpload =
        awaitingUpload.copy(
          uploadId      = "upload-ready",
          reference     = "reference-ready",
          fileStatus    = FileStatus.Ready,
          downloadUrl   = Some(downloadUrl),
          uploadDetails = Some(uploadDetails),
          updatedAt     = updatedAt
        )

      val failedUpload =
        awaitingUpload.copy(
          uploadId       = "upload-failed",
          reference      = "reference-failed",
          fileStatus     = FileStatus.Failed,
          failureDetails = Some(failureDetails),
          updatedAt      = updatedAt
        )

      insert(readyUpload)
      insert(failedUpload)

      repository
        .findAwaitingUpload(applicationId)
        .futureValue shouldBe None
    }

    "return None when the application has no awaiting upload" in {
      repository
        .findAwaitingUpload(applicationId)
        .futureValue shouldBe None
    }
  }

  "replaceAwaitingUpload" should {

    "insert a new awaiting upload when no existing upload is being replaced" in {
      val result =
        repository
          .replaceAwaitingUpload(
            newUpload = awaitingUpload,
            replacing = None
          )
          .futureValue

      result shouldBe true

      repository
        .find(awaitingUpload.uploadId)
        .futureValue shouldBe Some(awaitingUpload)
    }

    "delete the upload identified by the replacing reference and insert the new upload" in {
      val existingUpload =
        awaitingUpload.copy(
          uploadId  = "upload-old",
          reference = "reference-old",
          fields = Map(
            "key" -> "reference-old"
          )
        )

      val newUpload =
        awaitingUpload.copy(
          uploadId  = "upload-new",
          reference = "reference-new",
          fields = Map(
            "key" -> "reference-new"
          )
        )

      insert(existingUpload)

      val result =
        repository
          .replaceAwaitingUpload(
            newUpload = newUpload,
            replacing = Some(existingUpload.reference)
          )
          .futureValue

      result shouldBe true

      repository
        .find(existingUpload.uploadId)
        .futureValue shouldBe None

      repository
        .find(newUpload.uploadId)
        .futureValue shouldBe Some(newUpload)
    }

    "only delete the replacing upload belonging to the same application" in {
      val uploadForAnotherApplication =
        awaitingUpload.copy(
          uploadId      = "upload-other",
          applicationId = anotherApplicationId,
          reference     = "shared-replacement-reference"
        )

      val newUpload =
        awaitingUpload.copy(
          uploadId  = "upload-new",
          reference = "reference-new"
        )

      insert(uploadForAnotherApplication)

      repository
        .replaceAwaitingUpload(
          newUpload = newUpload,
          replacing = Some("shared-replacement-reference")
        )
        .futureValue shouldBe true

      repository
        .find(uploadForAnotherApplication.uploadId)
        .futureValue shouldBe Some(uploadForAnotherApplication)

      repository
        .find(newUpload.uploadId)
        .futureValue shouldBe Some(newUpload)
    }

    "reject a Ready upload" in {
      val readyUpload =
        awaitingUpload.copy(
          fileStatus    = FileStatus.Ready,
          downloadUrl   = Some(downloadUrl),
          uploadDetails = Some(uploadDetails),
          updatedAt     = updatedAt
        )

      val thrown =
        repository
          .replaceAwaitingUpload(
            newUpload = readyUpload,
            replacing = None
          )
          .failed
          .futureValue

      thrown shouldBe an[IllegalArgumentException]

      thrown.getMessage should include(
        FileStatus.AwaitingUpload.jsonValue
      )

      thrown.getMessage should include(
        FileStatus.Ready.jsonValue
      )

      repository
        .find(readyUpload.uploadId)
        .futureValue shouldBe None
    }

    "reject a Failed upload" in {
      val failedUpload =
        awaitingUpload.copy(
          fileStatus     = FileStatus.Failed,
          failureDetails = Some(failureDetails),
          updatedAt      = updatedAt
        )

      val thrown =
        repository
          .replaceAwaitingUpload(
            newUpload = failedUpload,
            replacing = None
          )
          .failed
          .futureValue

      thrown shouldBe an[IllegalArgumentException]

      thrown.getMessage should include(
        FileStatus.AwaitingUpload.jsonValue
      )

      thrown.getMessage should include(
        FileStatus.Failed.jsonValue
      )
    }

    "return false when the upload ID already exists" in {
      insert(awaitingUpload)

      val result =
        repository
          .replaceAwaitingUpload(
            newUpload = awaitingUpload,
            replacing = None
          )
          .futureValue

      result shouldBe false
    }
  }

  "updateAwaitingUpload" should {

    "atomically change an awaiting upload to Ready" in {
      insert(awaitingUpload)

      val readyUpload =
        awaitingUpload.copy(
          fileStatus     = FileStatus.Ready,
          downloadUrl    = Some(downloadUrl),
          uploadDetails  = Some(uploadDetails),
          failureDetails = None,
          updatedAt      = updatedAt
        )

      val result =
        repository
          .updateAwaitingUpload(readyUpload)
          .futureValue

      result shouldBe true

      repository
        .find(awaitingUpload.uploadId)
        .futureValue shouldBe Some(readyUpload)
    }

    "atomically change an awaiting upload to Failed" in {
      insert(awaitingUpload)

      val failedUpload =
        awaitingUpload.copy(
          fileStatus     = FileStatus.Failed,
          downloadUrl    = None,
          uploadDetails  = None,
          failureDetails = Some(failureDetails),
          updatedAt      = updatedAt
        )

      val result =
        repository
          .updateAwaitingUpload(failedUpload)
          .futureValue

      result shouldBe true

      repository
        .find(awaitingUpload.uploadId)
        .futureValue shouldBe Some(failedUpload)
    }

    "return false when the upload has already been handled" in {
      val storedReadyUpload =
        awaitingUpload.copy(
          fileStatus    = FileStatus.Ready,
          downloadUrl   = Some(downloadUrl),
          uploadDetails = Some(uploadDetails),
          updatedAt     = updatedAt
        )

      insert(storedReadyUpload)

      val failedUpdate =
        storedReadyUpload.copy(
          fileStatus     = FileStatus.Failed,
          downloadUrl    = None,
          uploadDetails  = None,
          failureDetails = Some(failureDetails)
        )

      val result =
        repository
          .updateAwaitingUpload(failedUpdate)
          .futureValue

      result shouldBe false

      repository
        .find(storedReadyUpload.uploadId)
        .futureValue shouldBe Some(storedReadyUpload)
    }

    "return false when the upload ID does not exist" in {
      val readyUpload =
        awaitingUpload.copy(
          fileStatus    = FileStatus.Ready,
          downloadUrl   = Some(downloadUrl),
          uploadDetails = Some(uploadDetails),
          updatedAt     = updatedAt
        )

      repository
        .updateAwaitingUpload(readyUpload)
        .futureValue shouldBe false
    }

    "reject AwaitingUpload as a callback target status" in {
      val thrown =
        repository
          .updateAwaitingUpload(awaitingUpload)
          .failed
          .futureValue

      thrown shouldBe an[IllegalArgumentException]

      thrown.getMessage should include(
        FileStatus.Ready.jsonValue
      )

      thrown.getMessage should include(
        FileStatus.Failed.jsonValue
      )

      thrown.getMessage should include(
        FileStatus.AwaitingUpload.jsonValue
      )
    }

    "not modify the stored upload when the callback target status is invalid" in {
      insert(awaitingUpload)

      repository
        .updateAwaitingUpload(awaitingUpload)
        .failed
        .futureValue shouldBe an[IllegalArgumentException]

      repository
        .find(awaitingUpload.uploadId)
        .futureValue shouldBe Some(awaitingUpload)
    }
  }

  "indexes" should {

    "define the TTL index on updatedAt" in {
      val indexes =
        UploadRepository.indexes(
          ttlInSeconds = 86400L
        )

      val ttlIndex =
        indexes.find(
          _.getOptions.getName == "updatedAt-ttl-index"
        )

      ttlIndex should not be empty

      ttlIndex.value.getOptions.getExpireAfter(
        TimeUnit.SECONDS
      ) shouldBe 86400L
    }

    "define a non-unique applicationId index" in {
      val indexes =
        UploadRepository.indexes(
          ttlInSeconds = 86400L
        )

      val applicationIndex =
        indexes.find(
          _.getOptions.getName == "applicationId-index"
        )

      applicationIndex                             should not be empty
      applicationIndex.value.getOptions.isUnique shouldBe false
    }

    "define a unique reference index" in {
      val indexes =
        UploadRepository.indexes(
          ttlInSeconds = 86400L
        )

      val referenceIndex =
        indexes.find(
          _.getOptions.getName == "reference-index"
        )

      referenceIndex                             should not be empty
      referenceIndex.value.getOptions.isUnique shouldBe true
    }

    "define a partial unique index for awaiting uploads" in {
      val indexes =
        UploadRepository.indexes(
          ttlInSeconds = 86400L
        )

      val awaitingUploadIndex =
        indexes.find(
          _.getOptions.getName ==
            "applicationId-awaiting-upload-index"
        )

      awaitingUploadIndex should not be empty

      val index =
        awaitingUploadIndex.value

      index.getOptions.isUnique shouldBe true

      val partialFilter =
        index.getOptions.getPartialFilterExpression
          .toBsonDocument(
            classOf[org.bson.BsonDocument],
            DEFAULT_CODEC_REGISTRY
          )

      partialFilter.getString("fileStatus").getValue shouldBe
        FileStatus.AwaitingUpload.jsonValue
    }

    "use the existing collection name" in {
      UploadRepository.collectionName shouldBe
        "euvat-validation-status"
    }
  }
