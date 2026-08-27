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

}
