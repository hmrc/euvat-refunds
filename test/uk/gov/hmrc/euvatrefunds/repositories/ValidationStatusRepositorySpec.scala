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

  private val awaitingUpload = ValidationStatus.AwaitingUpload(
    applicationId     = applicationId,
    reference         = "ref-1",
    uploadUrl         = "https://upscan.example/upload",
    initiateTimestamp = timestamp,
    fields            = Map("key" -> "ref-1", "x-amz-algorithm" -> "AWS4-HMAC-SHA256"),
    createdAt         = timestamp,
    updatedAt         = timestamp
  )

  private val newerUpload = awaitingUpload.copy(reference = "ref-2", fields = Map("key" -> "ref-2"))

  "the collection" should {
    "be named euvat-validation-status" in {
      repository.collectionName shouldBe "euvat-validation-status"
    }

    "expire documents 15 days after they were last updated" in {
      val ttlIndex = repository.indexes.find(_.getOptions.getName == "updatedAt-ttl-index").value

      ttlIndex.getOptions.getExpireAfter(TimeUnit.SECONDS) shouldBe fifteenDaysInSeconds
    }
  }

  "ValidationStatusRepository.get" should {
    "return nothing when the application has no upload" in {
      repository.get(applicationId).futureValue shouldBe None
    }

    "return the upload held for the application unchanged" in {
      insert(awaitingUpload).futureValue

      repository.get(applicationId).futureValue shouldBe Some(awaitingUpload)
    }

    "return nothing for a different application" in {
      insert(awaitingUpload).futureValue

      repository.get(999999L).futureValue shouldBe None
    }
  }

  "ValidationStatusRepository.replaceUpload" should {
    "insert when the application has no upload" in {
      repository.replaceUpload(awaitingUpload, replacing = None).futureValue shouldBe true

      repository.get(applicationId).futureValue shouldBe Some(awaitingUpload)
    }

    "refuse to insert over an upload stored by another request" in {
      insert(awaitingUpload).futureValue

      repository.replaceUpload(newerUpload, replacing = None).futureValue shouldBe false

      repository.get(applicationId).futureValue shouldBe Some(awaitingUpload)
    }

    "replace the upload it was given" in {
      insert(awaitingUpload).futureValue

      repository.replaceUpload(newerUpload, replacing = Some(awaitingUpload.reference)).futureValue shouldBe true

      repository.get(applicationId).futureValue shouldBe Some(newerUpload)
      count().futureValue                       shouldBe 1
    }

    "refuse to replace an upload that has changed" in {
      insert(awaitingUpload).futureValue

      repository.replaceUpload(newerUpload, replacing = Some("a-stale-reference")).futureValue shouldBe false

      repository.get(applicationId).futureValue shouldBe Some(awaitingUpload)
    }
  }
}
