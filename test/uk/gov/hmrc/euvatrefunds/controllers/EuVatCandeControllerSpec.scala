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

package uk.gov.hmrc.euvatrefunds.controllers

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json
import play.api.mvc.{AnyContent, BodyParser, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.euvatrefunds.actions.AuthAction
import uk.gov.hmrc.euvatrefunds.models.requests.*
import uk.gov.hmrc.euvatrefunds.models.responses.*
import uk.gov.hmrc.euvatrefunds.services.EuVatCandeService
import uk.gov.hmrc.http.{HeaderCarrier, SessionId, UpstreamErrorResponse}

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

class EuVatCandeControllerSpec extends AnyWordSpec with Matchers with ScalaFutures with MockitoSugar with BeforeAndAfterEach {

  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val hc: HeaderCarrier = HeaderCarrier()
  private val service = mock[EuVatCandeService]

  private val authAction: AuthAction = new AuthAction {
    override def parser: BodyParser[AnyContent] = stubControllerComponents().parsers.defaultBodyParser
    override protected def executionContext: ExecutionContext = ec

    override def invokeBlock[A](
      request: Request[A],
      block: AuthenticatedRequest[A] => Future[Result]
    ): Future[Result] = {
      val fakeAuthReq = AuthenticatedRequest(
        request,
        credId          = "cred-123",
        sessionId       = SessionId("session-123"),
        identifierName  = "VatRegNo",
        identifierValue = "999108"
      )
      block(fakeAuthReq)
    }
  }

  private val controller =
    new EuVatCandeController(authAction, service, stubControllerComponents())

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(service)
  }

  "EuVatCacheController.createApplication" should {
    val appRequest: ApplicationRequest = ApplicationRequest(
      refundingCountryCode          = Some("FR"),
      periodStartDate               = Some(LocalDateTime.of(2025, 1, 1, 0, 0, 0)),
      periodEndDate                 = Some(LocalDateTime.of(2025, 3, 31, 23, 59, 59)),
      applicantEmailAddress         = Some("test@email.com"),
      applicantTelephoneNumber      = Some("0123456789"),
      applicationLanguage           = Some("EN"),
      businessActivityCode1         = Some("7090"),
      businessActivityCode2         = Some("8903"),
      businessActivityCode3         = None,
      representativeId              = None,
      representativeCountryCode     = None,
      representativeEmailAddress    = None,
      representativeIdType          = None,
      representativeTelephoneNumber = None,
      bankAccountOwnerName          = None,
      bankAccountOwnerType          = None,
      iBanCode                      = None,
      bicCode                       = None,
      bankAccountCurrencyCode       = None
    )

    val response = ApplicationResponse(
      applicationId     = 123,
      applicationNumber = "GB9999999123",
      updateSeqNumber   = 1
    )

    val request = FakeRequest(POST, "/create-application").withJsonBody(Json.toJson(appRequest))

    "return 200 to create refund application" in {
      when(service.createApplication(any(), any())(any()))
        .thenReturn(Future.successful(response))

      val result = controller.createApplication()(request)

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(response)
    }

    "return 400 when request body is invalid" in {
      val result = controller.createApplication()(
        FakeRequest(POST, "/create-application")
      )

      status(result)          shouldBe BAD_REQUEST
      contentAsString(result) shouldBe "Invalid request body"
    }

    "return 500 and log error when DB call fails" in {
      when(service.createApplication(any(), any())(any()))
        .thenReturn(Future.failed(new RuntimeException("DB error")))
      val result: Future[Result] = controller.createApplication()(
        FakeRequest(POST, "/create-application").withMethod("POST").withJsonBody(Json.toJson(appRequest))
      )

      status(result)        shouldBe INTERNAL_SERVER_ERROR
      contentAsString(result) should include("Failed to create refund application")
    }
  }

  "EuVatCandeController.getLatestApplications" should {
    val sampleRequest = LatestApplicationRequest(
      applicantVatRegNumber = "123456789",
      refundingCountry      = Some("LV"),
      startDate             = Some(LocalDateTime.of(2025, 2, 1, 0, 0)),
      endDate               = Some(LocalDateTime.of(2025, 5, 31, 0, 0)),
      representativeId      = Some("rep123"),
      maxNumber             = 10,
      orderBy               = None,
      sortOrder             = None,
      startAt               = None
    )

    val sampleResponse = LatestApplicationResponse(
      applications     = List.empty,
      totalApplication = 0
    )

    "return 200 with JSON when service returns latest applications" in {
      when(service.getLatestApplications(any())(any()))
        .thenReturn(Future.successful(sampleResponse))

      val result = controller.getLatestApplications()(
        FakeRequest(POST, "/get-latest-application")
          .withJsonBody(Json.toJson(sampleRequest))
      )

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(sampleResponse)
    }

    "return 400 when request body is invalid" in {
      val result = controller.getLatestApplications()(
        FakeRequest(POST, "/get-latest-application")
          .withJsonBody(Json.obj("invalid" -> "body"))
      )

      status(result) shouldBe BAD_REQUEST
    }
  }

  "EuVatCandeController.addPurchase" should {

    val purchaseRequest = AddPurchaseRequest(
      applicationId              = 123456,
      goodsDescriptionCategory   = "1",
      goodsDescriptionText       = Some("Fuel"),
      purchaseSubcategory        = None,
      simplifiedInvoiceIndicator = None,
      supplierName               = None,
      supplierAddress1           = None,
      supplierAddress2           = None,
      supplierAddress3           = None,
      supplierVatRegNumber       = None,
      supplierTaxIdentifier      = None,
      invoiceDate                = None,
      invoiceNumber              = None,
      currencyCode               = None,
      taxableAmount              = None,
      vatAmount                  = None,
      deductibleVatAmount        = None,
      updateSequenceNumber       = 1
    )

    val purchaseResponse = AddPurchaseResponse(itemNumber = 4, updateSequenceNumber = 1)

    "return 200 with JSON when service returns add purchase" in {
      when(service.addPurchase(any())(any()))
        .thenReturn(Future.successful(purchaseResponse))

      val result = controller.addPurchase()(
        FakeRequest(POST, "/add-purchase")
          .withJsonBody(Json.toJson(purchaseRequest))
      )

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(purchaseResponse)
    }

    "return 400 when request body is invalid" in {
      val result = controller.addPurchase()(
        FakeRequest(POST, "/add-purchase")
          .withJsonBody(Json.obj("invalid" -> "body"))
      )

      status(result) shouldBe BAD_REQUEST
    }

    "return 500 and log error when DB call fails" in {
      when(service.addPurchase(any())(any()))
        .thenReturn(Future.failed(new RuntimeException("DB error")))

      val result = controller.addPurchase()(
        FakeRequest(POST, "/add-purchase").withJsonBody(Json.toJson(purchaseRequest))
      )

      status(result)        shouldBe INTERNAL_SERVER_ERROR
      contentAsString(result) should include("Failed to add purchase")
    }
  }

  "EuVatCandeController.addImport" should {

    val importRequest = AddImportRequest(
      applicationId            = 123456,
      goodsDescriptionCategory = "1",
      updateSequenceNumber     = 1
    )

    val importResponse = AddImportResponse(itemNumber = 1, updateSequenceNumber = 1)

    "return 200 with JSON when service returns add import" in {
      when(service.addImport(any())(any()))
        .thenReturn(Future.successful(importResponse))

      val result = controller.addImport()(FakeRequest(POST, "/add-import").withJsonBody(Json.toJson(importRequest)))

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(importResponse)
    }

    "return 400 when request body is invalid" in {
      val result = controller.addImport()(FakeRequest(POST, "/add-import").withJsonBody(Json.obj("invalid" -> "body")))

      status(result) shouldBe BAD_REQUEST
    }

    "return 500 and log error when service fails" in {
      when(service.addImport(any())(any()))
        .thenReturn(Future.failed(new RuntimeException("DB error")))

      val result = controller.addImport()(FakeRequest(POST, "/add-import").withJsonBody(Json.toJson(importRequest)))

      status(result)        shouldBe INTERNAL_SERVER_ERROR
      contentAsString(result) should include("Failed to add import")
    }
  }

  "EuVatCandeController.getPurchaseDetails" should {

    val detailsRequest = GetPurchaseDetailsRequest(applicationId = 123456, itemNumber = 4)

    val detailsResponse = GetPurchaseDetailsResponse(
      goodsDescriptionCode       = "1",
      goodsDescriptionSubCode    = Some("1.1"),
      goodsDescriptionText       = Some("Fuel"),
      simplifiedInvoiceIndicator = None,
      supplierName               = Some("Supplier Ltd"),
      supplierAddressLine1       = Some("1 High Street"),
      supplierAddressLine2       = None,
      supplierAddressLine3       = None,
      supplierVatNumber          = Some("LV40003567907"),
      supplierTaxIdentifier      = None,
      invoiceDate                = Some(LocalDateTime.of(2025, 3, 15, 0, 0)),
      invoiceNumber              = Some("INV-001"),
      currencyCode               = Some("EUR"),
      taxableAmount              = Some(BigDecimal("100.50")),
      vatAmount                  = Some(BigDecimal("21.10")),
      deductibleVatAmount        = Some(BigDecimal("21.10")),
      updateSequenceNumber       = 7
    )

    "return 200 with JSON when service returns the purchase details" in {
      when(service.getPurchaseDetails(any())(any()))
        .thenReturn(Future.successful(detailsResponse))

      val result = controller.getPurchaseDetails()(
        FakeRequest(POST, "/get-purchase-details")
          .withJsonBody(Json.toJson(detailsRequest))
      )

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(detailsResponse)
    }

    "return 400 when request body is invalid" in {
      val result = controller.getPurchaseDetails()(
        FakeRequest(POST, "/get-purchase-details")
          .withJsonBody(Json.obj("invalid" -> "body"))
      )

      status(result) shouldBe BAD_REQUEST
    }

    "return 500 when the proxy call fails with an UpstreamErrorResponse" in {
      when(service.getPurchaseDetails(any())(any()))
        .thenReturn(Future.failed(UpstreamErrorResponse("No purchase record", 404)))

      val result = controller.getPurchaseDetails()(
        FakeRequest(POST, "/get-purchase-details").withJsonBody(Json.toJson(detailsRequest))
      )

      status(result)          shouldBe INTERNAL_SERVER_ERROR
      contentAsString(result) shouldBe "Failed to retrieve purchase details"
    }

    "return 500 and log error when DB call fails" in {
      when(service.getPurchaseDetails(any())(any()))
        .thenReturn(Future.failed(new RuntimeException("DB error")))

      val result = controller.getPurchaseDetails()(
        FakeRequest(POST, "/get-purchase-details").withJsonBody(Json.toJson(detailsRequest))
      )

      status(result)        shouldBe INTERNAL_SERVER_ERROR
      contentAsString(result) should include("Failed to retrieve purchase details")
    }
  }

  "EuVatCandeController.getSupplierTaxIdentifierCount" should {
    val sampleRequest = SupplierTaxIdentifierCountRequest(
      applicationId = 1,
      itemNumber    = 2,
      taxIdentifier = "TAX-1",
      invoiceNumber = "INV-1"
    )

    val sampleResponse = SupplierTaxIdentifierCountResponse(duplicateCount = 4)

    "return 200 with JSON when service returns duplicate count" in {
      when(service.getSupplierTaxIdentifierCount(any())(any()))
        .thenReturn(Future.successful(sampleResponse))

      val result = controller.getSupplierTaxIdentifierCount()(
        FakeRequest(POST, "/get-supplier-taxIdentifier-count").withJsonBody(Json.toJson(sampleRequest))
      )

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(sampleResponse)
    }

    "return 400 when request body is invalid" in {
      val result = controller.getSupplierTaxIdentifierCount()(FakeRequest(POST, "/get-supplier-taxIdentifier-count"))

      status(result) shouldBe BAD_REQUEST
    }
  }

  "EuVatCandeController.getSupplierVrnCount" should {
    val vrnCountRequest = SupplierVrnCountRequest(
      applicationId = 133,
      itemNumber    = 4,
      vatNumber     = "500000881",
      invoiceNumber = "a444"
    )
    val vrnCountResponse = SupplierVrnCountResponse(duplicateCount = 1)

    "return 200 with JSON when service returns the count" in {
      when(service.getSupplierVrnCount(any())(any()))
        .thenReturn(Future.successful(vrnCountResponse))

      val result = controller.getSupplierVrnCount()(
        FakeRequest(POST, "/get-supplier-vrn-count").withJsonBody(Json.toJson(vrnCountRequest))
      )

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(vrnCountResponse)
    }

    "return 400 when request body is invalid" in {
      val result = controller.getSupplierVrnCount()(
        FakeRequest(POST, "/get-supplier-vrn-count").withJsonBody(Json.obj("invalid" -> "body"))
      )

      status(result)          shouldBe BAD_REQUEST
      contentAsString(result) shouldBe "Invalid request body"
    }

    "return 500 and log error when the service fails" in {
      when(service.getSupplierVrnCount(any())(any()))
        .thenReturn(Future.failed(new RuntimeException("DB error")))

      val result = controller.getSupplierVrnCount()(
        FakeRequest(POST, "/get-supplier-vrn-count").withJsonBody(Json.toJson(vrnCountRequest))
      )

      status(result)        shouldBe INTERNAL_SERVER_ERROR
      contentAsString(result) should include("Failed to retrieve supplier VRN count")
    }
  }

  "EuVatCandeController.updatePurchaseDetails" should {
    val updateRequest = UpdatePurchaseRequest(
      applicationId               = 123456,
      itemNumber                  = 1,
      goodsDescriptionCategory    = "1",
      goodsDescriptionSubCategory = None,
      goodsDescriptionText        = Some("Fuel"),
      simplifiedInvoiceIndicator  = None,
      supplierName                = None,
      supplierAddress1            = None,
      supplierAddress2            = None,
      supplierAddress3            = None,
      supplierVatRegNumber        = None,
      supplierTaxIdentifier       = None,
      invoiceDate                 = None,
      invoiceNumber               = None,
      currencyCode                = None,
      taxableAmount               = None,
      vatAmount                   = None,
      deductibleVatAmount         = None,
      updateSequenceNumber        = 1
    )

    val updateResponse = UpdatePurchaseResponse(updateSequenceNumber = 1)

    "return 200 with JSON when service returns update response" in {
      when(service.updatePurchaseDetails(any())(any()))
        .thenReturn(Future.successful(updateResponse))

      val result = controller.updatePurchaseDetails()(
        FakeRequest(PUT, "/update-purchase-details").withJsonBody(Json.toJson(updateRequest))
      )

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(updateResponse)
    }

    "return 400 when request body is invalid" in {
      val result = controller.updatePurchaseDetails()(
        FakeRequest(PUT, "/update-purchase-details").withJsonBody(Json.obj("invalid" -> "body"))
      )

      status(result) shouldBe BAD_REQUEST
    }

    "return 500 and log error when DB call fails" in {
      when(service.updatePurchaseDetails(any())(any()))
        .thenReturn(Future.failed(new RuntimeException("DB error")))

      val result = controller.updatePurchaseDetails()(
        FakeRequest(PUT, "/update-purchase-details").withJsonBody(Json.toJson(updateRequest))
      )

      status(result)        shouldBe INTERNAL_SERVER_ERROR
      contentAsString(result) should include("Failed to update purchase")
    }
  }

  "EuVatCandeController.getPurchaseImportList" should {
    val updateRequest = PurchaseImportListRequest(applicationId = 123456)
    val updateResponse = PurchaseImportListResponse(Nil, 0, BigDecimal(0))

    "return 200 with JSON when service returns update response" in {
      when(service.getPurchaseImportList(any())(any()))
        .thenReturn(Future.successful(updateResponse))

      val result = controller.getPurchaseImportList()(
        FakeRequest(POST, "/get-purchases-and-imports").withJsonBody(Json.toJson(updateRequest))
      )

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(updateResponse)
    }

    "return 400 when request body is invalid" in {
      val result = controller.getPurchaseImportList()(
        FakeRequest(POST, "/get-purchases-and-imports").withJsonBody(Json.obj("invalid" -> "body"))
      )

      status(result) shouldBe BAD_REQUEST
    }

    "return 500 and log error when DB call fails" in {
      when(service.getPurchaseImportList(any())(any()))
        .thenReturn(Future.failed(new RuntimeException("DB error")))

      val result = controller.getPurchaseImportList()(
        FakeRequest(POST, "/get-purchases-and-imports").withJsonBody(Json.toJson(updateRequest))
      )

      status(result)        shouldBe INTERNAL_SERVER_ERROR
      contentAsString(result) should include("Failed to retrieve purchase import list")
    }
  }

  "EuVatCandeController.getImportDetails" should {
    val response = GetImportDetailsResponse(
      applicationId               = 123456L,
      itemNumber                  = 4,
      issuingDate                 = Some(LocalDateTime.of(2025, 3, 15, 0, 0)),
      supplierName                = Some("Supplier Ltd"),
      supplierAddressLine1        = Some("1 High Street"),
      supplierAddressLine2        = None,
      supplierAddressLine3        = None,
      supplierCountryCode         = Some("LV"),
      goodsDescriptionCategory    = "1",
      currencyCode                = Some("EUR"),
      taxableAmount               = Some(BigDecimal("100.50")),
      vatAmount                   = Some(BigDecimal("21.10")),
      deductibleVatAmount         = Some(BigDecimal("21.10")),
      hasSadReferenceNumber       = Some("N"),
      sadReferenceNumber          = Some("SAD-123"),
      referenceInformation        = Some("Ref Info"),
      goodsDescriptionText        = Some("Fuel"),
      goodsDescriptionSubcategory = Some("1.1"),
      updateSequenceNumber        = 42,
      periodStartDate             = LocalDateTime.of(2025, 1, 1, 0, 0)
    )

    "return 200 with JSON when service returns the import details" in {
      when(service.getImportDetails(any(), any())(any())).thenReturn(Future.successful(response))

      val result = controller.getImportDetails(123456L, 4)(FakeRequest(GET, s"/get-import-details/123456/4"))

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(response)
    }

    "return 500 when service fails" in {
      when(service.getImportDetails(any(), any())(any())).thenReturn(Future.failed(new RuntimeException("DB error")))

      val result = controller.getImportDetails(123456L, 4)(FakeRequest(GET, s"/get-import-details/123456/4"))

      status(result) shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "EuVatCandeController.deleteApplication" should {
    val deleteRequest = DeleteApplicationRequest(applicationId = 123L, updateSequenceNumber = 1)
    val deleteResponse = uk.gov.hmrc.http.HttpResponse(200, "")

    "return 200 to delete application" in {
      when(service.deleteApplication(any())(any()))
        .thenReturn(Future.successful(deleteResponse))

      val result = controller.deleteApplication()(FakeRequest(DELETE, "/delete-application").withJsonBody(Json.toJson(deleteRequest)))

      status(result)          shouldBe 200
      contentAsString(result) shouldBe ""
    }

    "return 400 when request body is invalid" in {
      val result = controller.deleteApplication()(FakeRequest(DELETE, "/delete-application"))

      status(result)          shouldBe BAD_REQUEST
      contentAsString(result) shouldBe "Invalid request body"
    }

    "return 500 and log error when service fails" in {
      when(service.deleteApplication(any())(any()))
        .thenReturn(Future.failed(new RuntimeException("DB error")))

      val result = controller.deleteApplication()(FakeRequest(DELETE, "/delete-application").withJsonBody(Json.toJson(deleteRequest)))

      status(result)        shouldBe INTERNAL_SERVER_ERROR
      contentAsString(result) should include("Failed to delete application")
    }
  }

  "EuVatCandeController.updateApplicationDetails" should {
    val updateRequest = UpdateApplicationDetailsRequest(
      applicationId              = 133,
      applicationLanguage        = "en",
      refundingCountry           = "LV",
      periodStartDate            = LocalDateTime.of(2011, 6, 1, 0, 0),
      periodEndDate              = LocalDateTime.of(2011, 10, 31, 23, 59, 59),
      applicantEmailAddress      = "test@hotmail.com",
      applicantPhoneNumber       = Some("01952233299"),
      representativeCountry      = None,
      representativeEmailAddress = None,
      representativePhoneNumber  = None,
      bankAccountOwnerName       = None,
      bankAccountOwnerType       = None,
      ibanCode                   = None,
      bicCode                    = None,
      bankAccountCurrencyCode    = None,
      businessActivityCode2      = None,
      businessActivityCode3      = None,
      cipherText                 = None,
      encryptionStatus           = None,
      updateSequenceNumber       = 30
    )

    val updateResponse = UpdateApplicationDetailsResponse(updateSequenceNumber = 32)

    "return 200 with JSON when service returns update response" in {
      when(service.updateApplicationDetails(any())(any()))
        .thenReturn(Future.successful(updateResponse))

      val result = controller.updateApplicationDetails()(
        FakeRequest(PUT, "/update-application-details").withJsonBody(Json.toJson(updateRequest))
      )

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(updateResponse)
    }

    "return 400 when request body is invalid" in {
      val result = controller.updateApplicationDetails()(
        FakeRequest(PUT, "/update-application-details").withJsonBody(Json.obj("invalid" -> "body"))
      )

      status(result)          shouldBe BAD_REQUEST
      contentAsString(result) shouldBe "Invalid request body"
    }

    "return 500 when the proxy call fails with an UpstreamErrorResponse" in {
      when(service.updateApplicationDetails(any())(any()))
        .thenReturn(Future.failed(UpstreamErrorResponse("Lost update", 500)))

      val result = controller.updateApplicationDetails()(
        FakeRequest(PUT, "/update-application-details").withJsonBody(Json.toJson(updateRequest))
      )

      status(result)          shouldBe INTERNAL_SERVER_ERROR
      contentAsString(result) shouldBe "Failed to update application details"
    }

    "return 500 and log error when DB call fails" in {
      when(service.updateApplicationDetails(any())(any()))
        .thenReturn(Future.failed(new RuntimeException("DB error")))

      val result = controller.updateApplicationDetails()(
        FakeRequest(PUT, "/update-application-details").withJsonBody(Json.toJson(updateRequest))
      )

      status(result)        shouldBe INTERNAL_SERVER_ERROR
      contentAsString(result) should include("Failed to update application details")
    }
  }

  "EuVatCandeController.updateImportDetails" should {
    val updateRequest = UpdateImportRequest(
      applicationId               = 404,
      itemNumber                  = 4,
      goodsDescriptionCategory    = "10",
      goodsDescriptionSubCategory = Some("10.4.1"),
      goodsDescriptionText        = Some("office stationery and consumables"),
      hasSadReferenceNumber       = Some("Y"),
      sadReferenceNumber          = Some("SAD123456789"),
      referenceInformation        = Some("Import declaration reference"),
      supplierName                = Some("Finnish International"),
      supplierAddressLine1        = Some("356 High Street"),
      supplierAddressLine2        = Some("Rochdale"),
      supplierAddressLine3        = Some("England"),
      supplierCountryCode         = Some("GB"),
      issuingDate                 = Some(LocalDateTime.of(2026, 5, 14, 0, 0)),
      currencyCode                = Some("EUR"),
      taxableAmount               = Some(BigDecimal(1000)),
      vatAmount                   = Some(BigDecimal(99)),
      deductibleVatAmount         = Some(BigDecimal(40)),
      updateSequenceNumber        = 1
    )

    val updateResponse = UpdateImportResponse(updateSequenceNumber = 2)

    "return 200 with JSON when service returns update response" in {
      when(service.updateImportDetails(any())(any()))
        .thenReturn(Future.successful(updateResponse))

      val result = controller.updateImportDetails()(
        FakeRequest(PUT, "/update-importation-details").withJsonBody(Json.toJson(updateRequest))
      )

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(updateResponse)
    }

    "return 400 when request body is invalid" in {
      val result = controller.updateImportDetails()(
        FakeRequest(PUT, "/update-importation-details").withJsonBody(Json.obj("invalid" -> "body"))
      )

      status(result)          shouldBe BAD_REQUEST
      contentAsString(result) shouldBe "Invalid request body"
    }

    "return 400 when request body is missing" in {
      val result = controller.updateImportDetails()(
        FakeRequest(PUT, "/update-importation-details")
      )

      status(result) shouldBe BAD_REQUEST
    }

    "return 500 when the upstream service returns an error" in {
      when(service.updateImportDetails(any())(any()))
        .thenReturn(Future.failed(UpstreamErrorResponse("bad request", BAD_REQUEST)))

      val result = controller.updateImportDetails()(
        FakeRequest(PUT, "/update-importation-details").withJsonBody(Json.toJson(updateRequest))
      )

      status(result)        shouldBe INTERNAL_SERVER_ERROR
      contentAsString(result) should include("Failed to update import details")
    }

    "return 500 and log error when DB call fails" in {
      when(service.updateImportDetails(any())(any()))
        .thenReturn(Future.failed(new RuntimeException("DB error")))

      val result = controller.updateImportDetails()(
        FakeRequest(PUT, "/update-importation-details").withJsonBody(Json.toJson(updateRequest))
      )

      status(result)        shouldBe INTERNAL_SERVER_ERROR
      contentAsString(result) should include("Failed to update import details")
    }
  }

  "EuVatCandeController.deletePurchase" should {

    val deleteRequest = DeletePurchaseRequest(applicationId = 123456, itemNumber = 4, updateSequenceNumber = 7)

    val deleteResponse = DeletePurchaseResponse(updateSequenceNumber = 8)

    "return 200 with JSON when service returns the new update sequence number" in {
      when(service.deletePurchase(any())(any()))
        .thenReturn(Future.successful(deleteResponse))

      val result = controller.deletePurchase()(
        FakeRequest(DELETE, "/delete-purchase")
          .withJsonBody(Json.toJson(deleteRequest))
      )

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(deleteResponse)
    }

    "return 400 when request body is invalid" in {
      val result = controller.deletePurchase()(
        FakeRequest(DELETE, "/delete-purchase")
          .withJsonBody(Json.obj("invalid" -> "body"))
      )

      status(result) shouldBe BAD_REQUEST
    }

    "return 500 when the proxy call fails with an UpstreamErrorResponse" in {
      when(service.deletePurchase(any())(any()))
        .thenReturn(Future.failed(UpstreamErrorResponse("bang", 502)))

      val result = controller.deletePurchase()(
        FakeRequest(DELETE, "/delete-purchase").withJsonBody(Json.toJson(deleteRequest))
      )

      status(result)          shouldBe INTERNAL_SERVER_ERROR
      contentAsString(result) shouldBe "Failed to delete purchase"
    }

    "return 500 and log error when DB call fails" in {
      when(service.deletePurchase(any())(any()))
        .thenReturn(Future.failed(new RuntimeException("DB error")))

      val result = controller.deletePurchase()(
        FakeRequest(DELETE, "/delete-purchase").withJsonBody(Json.toJson(deleteRequest))
      )

      status(result)        shouldBe INTERNAL_SERVER_ERROR
      contentAsString(result) should include("Failed to delete purchase")
    }
  }

  "EuVatCandeController.getSadReferenceCount" should {
    val sadReferenceCountRequest = SadReferenceCountRequest(
      applicationId      = 133,
      itemNumber         = 4,
      sadReferenceNumber = "500000881"
    )
    val sadReferenceCountResponse = SadReferenceCountResponse(duplicateCount = 1)

    "return 200 with JSON when service returns the count" in {
      when(service.getSadReferenceCount(any())(any()))
        .thenReturn(Future.successful(sadReferenceCountResponse))

      val result = controller.getSadReferenceCount()(
        FakeRequest(POST, "/get-sad-reference-count").withJsonBody(Json.toJson(sadReferenceCountRequest))
      )

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(sadReferenceCountResponse)
    }

    "return 400 when request body is invalid" in {
      val result = controller.getSadReferenceCount()(
        FakeRequest(POST, "/get-sad-reference-count").withJsonBody(Json.obj("invalid" -> "body"))
      )

      status(result)          shouldBe BAD_REQUEST
      contentAsString(result) shouldBe "Invalid request body"
    }

    "return 500 and log error when the service fails" in {
      when(service.getSadReferenceCount(any())(any()))
        .thenReturn(Future.failed(new RuntimeException("DB error")))

      val result = controller.getSadReferenceCount()(
        FakeRequest(POST, "/get-sad-reference-count").withJsonBody(Json.toJson(sadReferenceCountRequest))
      )

      status(result)        shouldBe INTERNAL_SERVER_ERROR
      contentAsString(result) should include("Failed to retrieve SAD reference count")
    }
  }
}
