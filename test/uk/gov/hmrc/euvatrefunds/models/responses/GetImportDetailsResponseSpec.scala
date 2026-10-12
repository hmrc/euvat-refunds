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

class GetImportDetailsResponseSpec extends AnyWordSpec with Matchers {

  private val fullResponse = GetImportDetailsResponse(
    applicationId               = 123456L,
    itemNumber                  = 4,
    issuingDate                 = Some(LocalDateTime.of(2025, 3, 15, 0, 0)),
    supplierName                = Some("Supplier Ltd"),
    supplierAddressLine1        = Some("1 High Street"),
    supplierAddressLine2        = Some("Riga"),
    supplierAddressLine3        = None,
    supplierCountryCode         = Some("LV"),
    goodsDescriptionCategory    = Some("1"),
    currencyCode                = Some("EUR"),
    taxableAmount               = Some(BigDecimal("100.50")),
    vatAmount                   = Some(BigDecimal("21.10")),
    deductibleVatAmount         = Some(BigDecimal("21.10")),
    updateDateTime              = Some(LocalDateTime.of(2025, 3, 15, 12, 0)),
    hasSadReferenceNumber       = Some("N"),
    sadReferenceNumber          = Some("SAD-123"),
    referenceInformation        = Some("Ref Info"),
    goodsDescriptionText        = Some("Fuel"),
    goodsDescriptionSubcategory = Some("1.1")
  )

  "GetImportDetailsResponse JSON format" should {

    "round-trip a fully populated response" in {
      Json.toJson(fullResponse).as[GetImportDetailsResponse] shouldBe fullResponse
    }

    "omit absent optional fields from the JSON" in {
      val minimal = GetImportDetailsResponse(
        applicationId               = 1,
        itemNumber                  = 1,
        issuingDate                 = None,
        supplierName                = None,
        supplierAddressLine1        = None,
        supplierAddressLine2        = None,
        supplierAddressLine3        = None,
        supplierCountryCode         = None,
        goodsDescriptionCategory    = None,
        currencyCode                = None,
        taxableAmount               = None,
        vatAmount                   = None,
        deductibleVatAmount         = None,
        updateDateTime              = None,
        hasSadReferenceNumber       = None,
        sadReferenceNumber          = None,
        referenceInformation        = None,
        goodsDescriptionText        = None,
        goodsDescriptionSubcategory = None
      )

      Json.toJson(minimal).as[GetImportDetailsResponse] shouldBe minimal
    }
  }
}
