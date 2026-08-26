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

package uk.gov.hmrc.euvatrefunds.services

import com.typesafe.config.ConfigFactory
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import uk.gov.hmrc.euvatrefunds.connectors.{EuVatStubsConnector, RdsCandeProxyConnector}
import uk.gov.hmrc.euvatrefunds.models.requests.*
import uk.gov.hmrc.euvatrefunds.models.responses.*
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

class EuVatCandeServiceSpec extends AnyWordSpec with Matchers with MockitoSugar with ScalaFutures {

  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val hc: HeaderCarrier = HeaderCarrier()

  "EuVatCandeService.createApplication" should {
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

    val expectedResponse: ApplicationResponse = ApplicationResponse(
      applicationId     = 123456789,
      applicationNumber = "GB123456789",
      updateSeqNumber   = 123
    )

    "return the response from the rds cande connector" in {
      lazy val configuration: Configuration =
        Configuration(
          ConfigFactory.parseString(
            s"""
               |feature-switch.rds-cande-stubbed = false
               |""".stripMargin
          )
        )

      val mockCandeConnector: RdsCandeProxyConnector = mock[RdsCandeProxyConnector]
      val mockStubsConnector: EuVatStubsConnector = mock[EuVatStubsConnector]
      val service = new EuVatCandeService(mockCandeConnector, mockStubsConnector, configuration)

      when(mockCandeConnector.createApplication(any())(any()))
        .thenReturn(Future.successful(expectedResponse))

      val result = service.createApplication(appRequest, "123456").futureValue

      result shouldBe expectedResponse
      verify(mockCandeConnector, times(1)).createApplication(any())(any())
    }

    "return the response from the stubs connector" in {
      lazy val configuration: Configuration =
        Configuration(
          ConfigFactory.parseString(
            s"""
               |feature-switch.rds-cande-stubbed = true
               |""".stripMargin
          )
        )

      val mockCandeConnector: RdsCandeProxyConnector = mock[RdsCandeProxyConnector]
      val mockStubsConnector: EuVatStubsConnector = mock[EuVatStubsConnector]
      val service = new EuVatCandeService(mockCandeConnector, mockStubsConnector, configuration)

      when(mockStubsConnector.createApplication(any(), any())(any()))
        .thenReturn(Future.successful(expectedResponse))

      val result = service.createApplication(appRequest, "9999999").futureValue

      result shouldBe expectedResponse
    }

    "propagate an exception from the connector" in {
      val failure = new RuntimeException("Connector failed")
      lazy val configuration: Configuration =
        Configuration(
          ConfigFactory.parseString(
            s"""
               |feature-switch.rds-cande-stubbed = false
               |""".stripMargin
          )
        )
      val mockCandeConnector: RdsCandeProxyConnector = mock[RdsCandeProxyConnector]
      val mockStubsConnector: EuVatStubsConnector = mock[EuVatStubsConnector]
      val service = new EuVatCandeService(mockCandeConnector, mockStubsConnector, configuration)

      when(mockCandeConnector.createApplication(any())(any()))
        .thenReturn(Future.failed(failure))

      val result = service.createApplication(appRequest, "6666666")

      whenReady(result.failed) { ex =>
        ex shouldBe failure
      }
    }
  }

  "EuVatCandeService.getLatestApplications" should {
    val request = LatestApplicationRequest(
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

    "return the response from the rds cande connector" in {
      lazy val configuration: Configuration =
        Configuration(
          ConfigFactory.parseString(
            s"""
               |feature-switch.rds-cande-stubbed = false
               |""".stripMargin
          )
        )

      val mockCandeConnector: RdsCandeProxyConnector = mock[RdsCandeProxyConnector]
      val mockStubsConnector: EuVatStubsConnector = mock[EuVatStubsConnector]
      val service = new EuVatCandeService(mockCandeConnector, mockStubsConnector, configuration)

      val expectedResponse = LatestApplicationResponse(
        applications     = List.empty,
        totalApplication = 0
      )

      when(mockCandeConnector.getLatestApplications(any())(any()))
        .thenReturn(Future.successful(expectedResponse))

      val result = service.getLatestApplications(request).futureValue

      result shouldBe expectedResponse
      verify(mockCandeConnector, times(1)).getLatestApplications(any())(any())
    }

    "return the response from the euvat stubs connector" in {
      lazy val configuration: Configuration =
        Configuration(
          ConfigFactory.parseString(
            s"""
               |feature-switch.rds-cande-stubbed = true
               |""".stripMargin
          )
        )

      val mockCandeConnector: RdsCandeProxyConnector = mock[RdsCandeProxyConnector]
      val mockStubsConnector: EuVatStubsConnector = mock[EuVatStubsConnector]
      val service = new EuVatCandeService(mockCandeConnector, mockStubsConnector, configuration)

      val expectedResponse = LatestApplicationResponse(
        applications     = List.empty,
        totalApplication = 0
      )

      when(mockStubsConnector.getLatestApplications(any())(any()))
        .thenReturn(Future.successful(expectedResponse))

      val result = service.getLatestApplications(request).futureValue

      result shouldBe expectedResponse
      verify(mockStubsConnector, times(1)).getLatestApplications(any())(any())
    }

    "propagate an exception from the connector" in {
      val failure = new RuntimeException("Connector failed")
      lazy val configuration: Configuration =
        Configuration(
          ConfigFactory.parseString(
            s"""
               |feature-switch.rds-cande-stubbed = false
               |""".stripMargin
          )
        )
      val mockCandeConnector: RdsCandeProxyConnector = mock[RdsCandeProxyConnector]
      val mockStubsConnector: EuVatStubsConnector = mock[EuVatStubsConnector]
      val service = new EuVatCandeService(mockCandeConnector, mockStubsConnector, configuration)

      when(mockCandeConnector.getLatestApplications(any())(any()))
        .thenReturn(Future.failed(failure))

      val result = service.getLatestApplications(request)

      whenReady(result.failed) { ex =>
        ex shouldBe failure
      }
    }
  }

  "EuVatCandeService.addPurchase" should {
    val request = AddPurchaseRequest(
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
    val expectedResponse = AddPurchaseResponse(itemNumber = 4, updateSequenceNumber = 1)

    "return the response from the rds cande connector" in {
      lazy val configuration: Configuration =
        Configuration(ConfigFactory.parseString("feature-switch.rds-cande-stubbed = false"))

      val mockCandeConnector: RdsCandeProxyConnector = mock[RdsCandeProxyConnector]
      val mockStubsConnector: EuVatStubsConnector = mock[EuVatStubsConnector]
      val service = new EuVatCandeService(mockCandeConnector, mockStubsConnector, configuration)

      when(mockCandeConnector.addPurchase(any())(any()))
        .thenReturn(Future.successful(expectedResponse))

      service.addPurchase(request).futureValue shouldBe expectedResponse
      verify(mockCandeConnector, times(1)).addPurchase(any())(any())
    }

    "return the response from the euvat stubs connector" in {
      lazy val configuration: Configuration =
        Configuration(ConfigFactory.parseString("feature-switch.rds-cande-stubbed = true"))

      val mockCandeConnector: RdsCandeProxyConnector = mock[RdsCandeProxyConnector]
      val mockStubsConnector: EuVatStubsConnector = mock[EuVatStubsConnector]
      val service = new EuVatCandeService(mockCandeConnector, mockStubsConnector, configuration)

      when(mockStubsConnector.addPurchase(any())(any()))
        .thenReturn(Future.successful(expectedResponse))

      service.addPurchase(request).futureValue shouldBe expectedResponse
      verify(mockStubsConnector, times(1)).addPurchase(any())(any())
    }

    "propagate an exception from the connector" in {
      val failure = new RuntimeException("Connector failed")
      lazy val configuration: Configuration =
        Configuration(ConfigFactory.parseString("feature-switch.rds-cande-stubbed = false"))

      val mockCandeConnector: RdsCandeProxyConnector = mock[RdsCandeProxyConnector]
      val mockStubsConnector: EuVatStubsConnector = mock[EuVatStubsConnector]
      val service = new EuVatCandeService(mockCandeConnector, mockStubsConnector, configuration)

      when(mockCandeConnector.addPurchase(any())(any()))
        .thenReturn(Future.failed(failure))

      whenReady(service.addPurchase(request).failed) { ex =>
        ex shouldBe failure
      }
    }
  }

  "EuVatCandeService.getSupplierTaxIdentifierCount" should {
    val sampleRequest = SupplierTaxIdentifierCountRequest(
      applicationId = 10,
      itemNumber    = 2,
      taxIdentifier = "TID-99",
      invoiceNumber = "INV-99"
    )
    val sampleResponse = SupplierTaxIdentifierCountResponse(duplicateCount = 5)

    "return the response from the rds cande connector" in {
      lazy val configuration: Configuration =
        Configuration(
          ConfigFactory.parseString(
            s"""
               |feature-switch.rds-cande-stubbed = false
               |""".stripMargin
          )
        )

      val mockCandeConnector: RdsCandeProxyConnector = mock[RdsCandeProxyConnector]
      val mockStubsConnector: EuVatStubsConnector = mock[EuVatStubsConnector]
      val service = new EuVatCandeService(mockCandeConnector, mockStubsConnector, configuration)

      when(mockCandeConnector.getSupplierTaxIdentifierCount(any())(any()))
        .thenReturn(Future.successful(sampleResponse))

      val result = service.getSupplierTaxIdentifierCount(sampleRequest).futureValue

      result shouldBe sampleResponse
      verify(mockCandeConnector, times(1)).getSupplierTaxIdentifierCount(any())(any())
    }

    "return the response from the stubs connector" in {
      lazy val configuration: Configuration =
        Configuration(
          ConfigFactory.parseString(
            s"""
               |feature-switch.rds-cande-stubbed = true
               |""".stripMargin
          )
        )

      val mockCandeConnector: RdsCandeProxyConnector = mock[RdsCandeProxyConnector]
      val mockStubsConnector: EuVatStubsConnector = mock[EuVatStubsConnector]
      val service = new EuVatCandeService(mockCandeConnector, mockStubsConnector, configuration)

      when(mockStubsConnector.getSupplierTaxIdentifierCount(any())(any()))
        .thenReturn(Future.successful(sampleResponse))

      val result = service.getSupplierTaxIdentifierCount(sampleRequest).futureValue

      result shouldBe sampleResponse
      verify(mockStubsConnector, times(1)).getSupplierTaxIdentifierCount(any())(any())
    }

    "propagate an exception from the connector" in {
      val failure = new RuntimeException("Connector failed")
      lazy val configuration: Configuration =
        Configuration(
          ConfigFactory.parseString(
            s"""
               |feature-switch.rds-cande-stubbed = false
               |""".stripMargin
          )
        )
      val mockCandeConnector: RdsCandeProxyConnector = mock[RdsCandeProxyConnector]
      val mockStubsConnector: EuVatStubsConnector = mock[EuVatStubsConnector]
      val service = new EuVatCandeService(mockCandeConnector, mockStubsConnector, configuration)

      when(mockCandeConnector.getSupplierTaxIdentifierCount(any())(any()))
        .thenReturn(Future.failed(failure))

      val result = service.getSupplierTaxIdentifierCount(sampleRequest)

      whenReady(result.failed) { ex =>
        ex shouldBe failure
      }
    }
  }

  "EuVatCandeService.getSupplierVrnCount" should {
    val request = SupplierVrnCountRequest(
      applicationId = 133,
      itemNumber    = 4,
      vatNumber     = "500000881",
      invoiceNumber = "a444"
    )

    val expectedResponse = SupplierVrnCountResponse(duplicateCount = 1)

    "return the response from the rds cande connector" in {
      lazy val configuration: Configuration =
        Configuration(
          ConfigFactory.parseString(
            s"""
               |feature-switch.rds-cande-stubbed = false
               |""".stripMargin
          )
        )

      val mockCandeConnector: RdsCandeProxyConnector = mock[RdsCandeProxyConnector]
      val mockStubsConnector: EuVatStubsConnector = mock[EuVatStubsConnector]
      val service = new EuVatCandeService(mockCandeConnector, mockStubsConnector, configuration)

      when(mockCandeConnector.getSupplierVrnCount(any())(any()))
        .thenReturn(Future.successful(expectedResponse))

      val result = service.getSupplierVrnCount(request).futureValue

      result shouldBe expectedResponse
      verify(mockCandeConnector, times(1)).getSupplierVrnCount(any())(any())
    }

    "return the response from the euvat stubs connector" in {
      lazy val configuration: Configuration =
        Configuration(
          ConfigFactory.parseString(
            s"""
               |feature-switch.rds-cande-stubbed = true
               |""".stripMargin
          )
        )

      val mockCandeConnector: RdsCandeProxyConnector = mock[RdsCandeProxyConnector]
      val mockStubsConnector: EuVatStubsConnector = mock[EuVatStubsConnector]
      val service = new EuVatCandeService(mockCandeConnector, mockStubsConnector, configuration)

      when(mockStubsConnector.getSupplierVrnCount(any())(any()))
        .thenReturn(Future.successful(expectedResponse))

      val result = service.getSupplierVrnCount(request).futureValue

      result shouldBe expectedResponse
      verify(mockStubsConnector, times(1)).getSupplierVrnCount(any())(any())
    }

    "propagate an exception from the connector" in {
      val failure = new RuntimeException("Connector failed")
      lazy val configuration: Configuration =
        Configuration(
          ConfigFactory.parseString(
            s"""
               |feature-switch.rds-cande-stubbed = false
               |""".stripMargin
          )
        )
      val mockCandeConnector: RdsCandeProxyConnector = mock[RdsCandeProxyConnector]
      val mockStubsConnector: EuVatStubsConnector = mock[EuVatStubsConnector]
      val service = new EuVatCandeService(mockCandeConnector, mockStubsConnector, configuration)

      when(mockCandeConnector.getSupplierVrnCount(any())(any()))
        .thenReturn(Future.failed(failure))

      val result = service.getSupplierVrnCount(request)

      whenReady(result.failed) { ex =>
        ex shouldBe failure
      }
    }
  }

}
