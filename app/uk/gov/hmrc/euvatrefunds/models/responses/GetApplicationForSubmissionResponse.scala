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

import play.api.libs.json.{Json, OFormat}

import java.time.LocalDateTime

case class SubmissionApplicationDetails(
  applicationId: Long,
  applicationNumber: Option[String],
  refundingCountryCode: Option[String],
  applicationVersion: Option[LocalDateTime],
  periodStartDate: Option[LocalDateTime],
  periodEndDate: Option[LocalDateTime],
  applicantVatRegNumber: Option[String],
  applicantEmailAddress: Option[String],
  applicantTelephoneNumber: Option[String],
  representativeId: Option[String],
  representativeIdType: Option[String],
  representativeCountry: Option[String],
  representativeEmailAddress: Option[String],
  representativeTelephoneNum: Option[String],
  applicationLanguage: Option[String],
  bankAccountOwnerName: Option[String],
  bankAccountOwnerType: Option[String],
  ibanCode: Option[String],
  bicCode: Option[String],
  bankAccountCurrencyCode: Option[String],
  businessActivityCode1: Option[String],
  businessActivityCode2: Option[String],
  businessActivityCode3: Option[String],
  messageId: Option[String],
  correlationId: Option[String],
  cipherText: Option[String],
  encryptionStatus: Option[String],
  updateSeqNumber: Int,
  applicationStatus: Option[String],
  submissionStatus: Option[String]
)

object SubmissionApplicationDetails {
  implicit val format: OFormat[SubmissionApplicationDetails] = Json.format[SubmissionApplicationDetails]
}

case class SubmissionImportationDetails(
  itemNumber: Option[Int],
  goodsDescriptionCode: Option[String],
  goodsDescriptionText: Option[String],
  goodsDescriptionSubCode: Option[String],
  sadReferenceNumber: Option[String],
  hasSadReferenceNumber: Option[String],
  referenceInformation: Option[String],
  issuingDate: Option[LocalDateTime],
  supplierName: Option[String],
  supplierAddressLine1: Option[String],
  supplierAddressLine2: Option[String],
  supplierAddressLine3: Option[String],
  supplierCountryCode: Option[String],
  currencyCode: Option[String],
  taxableAmount: Option[BigDecimal],
  vatAmount: Option[BigDecimal],
  deductibleVatAmount: Option[BigDecimal]
)

object SubmissionImportationDetails {
  implicit val format: OFormat[SubmissionImportationDetails] = Json.format[SubmissionImportationDetails]
}

case class SubmissionPurchaseDetails(
  itemNumber: Option[Int],
  goodsDescriptionCode: Option[String],
  goodsDescriptionText: Option[String],
  goodsDescriptionSubCode: Option[String],
  simplifiedInvoiceIndicator: Option[String],
  supplierName: Option[String],
  supplierAddressLine1: Option[String],
  supplierAddressLine2: Option[String],
  supplierAddressLine3: Option[String],
  supplierVatNumber: Option[String],
  supplierTaxIdentifier: Option[String],
  invoiceDate: Option[LocalDateTime],
  invoiceNumber: Option[String],
  currencyCode: Option[String],
  taxableAmount: Option[BigDecimal],
  vatAmount: Option[BigDecimal],
  deductibleVatAmount: Option[BigDecimal]
)

object SubmissionPurchaseDetails {
  implicit val format: OFormat[SubmissionPurchaseDetails] = Json.format[SubmissionPurchaseDetails]
}

case class SubmissionAttachmentDetails(
  attachmentNumber: Option[Int],
  fileName: Option[String],
  fileType: Option[String],
  fileDescription: Option[String],
  fileSize: Option[Int],
  fileContent: Option[String]
)

object SubmissionAttachmentDetails {
  implicit val format: OFormat[SubmissionAttachmentDetails] = Json.format[SubmissionAttachmentDetails]
}

case class GetApplicationForSubmissionResponse(
  applicationDetails: List[SubmissionApplicationDetails],
  importationDetails: List[SubmissionImportationDetails],
  purchaseDetails: List[SubmissionPurchaseDetails],
  attachmentDetails: List[SubmissionAttachmentDetails]
)

object GetApplicationForSubmissionResponse {
  implicit val format: OFormat[GetApplicationForSubmissionResponse] = Json.format[GetApplicationForSubmissionResponse]
}
