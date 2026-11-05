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

package uk.gov.hmrc.euvatrefunds.models.responses

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

import java.time.LocalDateTime

class ApplicationDetailsResponseSpec extends AnyWordSpec with Matchers {

  private val application = ApplicationDetails(
    applicationId              = 133,
    applicationNumber          = Some("GB0000000000000133"),
    refundingCountryCode       = Some("LV"),
    applicationVersion         = Some(LocalDateTime.of(2009, 2, 1, 0, 0)),
    periodStartDate            = Some(LocalDateTime.of(2011, 6, 1, 0, 0)),
    periodEndDate              = Some(LocalDateTime.of(2011, 10, 31, 23, 59, 59, 999000000)),
    applicantVatRegNumber      = Some("123456789"),
    applicantEmailAddress      = Some("applicant@example.com"),
    applicantTelephoneNumber   = Some("01952233248"),
    representativeId           = None,
    representativeIdType       = None,
    representativeCountry      = None,
    representativeEmailAddress = None,
    representativeTelephoneNum = None,
    applicationLanguage        = Some("EN"),
    bankAccountOwnerName       = None,
    bankAccountOwnerType       = None,
    ibanCode                   = Some("GB00TEST00000000000000"),
    bicCode                    = Some("TESTGB00"),
    bankAccountCurrencyCode    = Some("GBP"),
    businessActivityCode1      = Some("2345"),
    businessActivityCode2      = Some("6789"),
    businessActivityCode3      = Some("1234"),
    messageId                  = None,
    correlationId              = None,
    cipherText                 = None,
    encryptionStatus           = None,
    updateSeqNumber            = 42,
    applicationStatus          = Some("D"),
    submissionStatus           = Some("S")
  )

  "ApplicationDetailsResponse JSON format" should {
    "serialize the applicationDetails list with the service model field names" in {
      val response = ApplicationDetailsResponse(List(application))
      val json = Json.toJson(response)
      val detail = (json \ "applicationDetails")(0)

      (detail \ "applicationId").as[Long]           shouldBe 133L
      (detail \ "applicationVersion").as[String]      should startWith("2009-02-01T00:00:00")
      (detail \ "periodStartDate").as[String]         should startWith("2011-06-01T00:00:00")
      (detail \ "applicantEmailAddress").as[String] shouldBe "applicant@example.com"
      (detail \ "ibanCode").as[String]              shouldBe "GB00TEST00000000000000"
      (detail \ "bicCode").as[String]               shouldBe "TESTGB00"
      (detail \ "updateSeqNumber").as[Int]          shouldBe 42
      (detail \ "applicationStatus").as[String]     shouldBe "D"
      (detail \ "submissionStatus").as[String]      shouldBe "S"
    }

    "deserialize the response and round-trip the service model" in {
      Json
        .toJson(ApplicationDetailsResponse(List(application)))
        .as[ApplicationDetailsResponse] shouldBe ApplicationDetailsResponse(List(application))
    }
  }
}
