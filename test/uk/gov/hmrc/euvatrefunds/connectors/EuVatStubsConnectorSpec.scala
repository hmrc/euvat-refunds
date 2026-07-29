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

package uk.gov.hmrc.euvatrefunds.connectors

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import play.api.libs.json.Json
import uk.gov.hmrc.euvatrefunds.config.AppConfig
import uk.gov.hmrc.euvatrefunds.models.requests.{AddPurchaseRequest, ApplicationRequest, LatestApplicationRequest}
import uk.gov.hmrc.euvatrefunds.models.responses.{AddPurchaseResponse, ApplicationResponse, LatestApplicationResponse, TraderKnownFactsResponse}
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.Implicits.global

class EuVatStubsConnectorSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with WireMockSupport
    with HttpClientV2Support {

  private given HeaderCarrier = HeaderCarrier()

  private lazy val configuration: Configuration =
    Configuration(
      ConfigFactory.parseString(
        s"""
           |appName = euvat-refunds
           |microservice.services.euvat-stubs.host = "$wireMockHost"
           |microservice.services.euvat-stubs.protocol = "http"
           |microservice.services.euvat-stubs.port = $wireMockPort
           |""".stripMargin
      )
    )

  private lazy val appConfig: AppConfig = new AppConfig(configuration)

  private lazy val connector: EuVatStubsConnector = new EuVatStubsConnector(appConfig, httpClientV2)

  private val sampleLatestApplicationResponse = LatestApplicationResponse(
    applications     = List.empty,
    totalApplication = 0
  )

  private val sampleLatestApplicationRequest = LatestApplicationRequest(
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

  "EuVatStubsConnector.getTraderKnownFacts" should {
    val sampleFacts = TraderKnownFactsResponse(
      vatRegNumber           = 123456789,
      traderName             = Some("ABC GmbH"),
      postCode               = Some("AB12 3CD"),
      tradeClass             = Some("8765"),
      missingTraderIndicator = Some("N")
    )

    "return the trader known facts when euvat-stubs returns 200" in {
      stubFor(
        get(urlEqualTo("/euvat-stubs/traders/get-known-facts/123456789"))
          .willReturn(aResponse().withStatus(200).withBody(Json.toJson(sampleFacts).toString))
      )

      connector.getTraderKnownFacts("123456789").futureValue shouldBe sampleFacts
    }

    "return error when euvat-stubs returns 404" in {
      stubFor(
        get(urlEqualTo("/traders/999999999"))
          .willReturn(aResponse().withStatus(404))
      )

      connector.getTraderKnownFacts("123456789").failed.futureValue shouldBe a[UpstreamErrorResponse]
    }
  }

  "EuVatStubsConnector.getLatestApplications" should {

    "return latest applications when euvat-stubs returns 200" in {
      stubFor(
        post(urlEqualTo("/euvat-stubs/get-latest-application"))
          .willReturn(aResponse().withStatus(200).withBody(Json.toJson(sampleLatestApplicationResponse).toString))
      )

      connector.getLatestApplications(sampleLatestApplicationRequest).futureValue shouldBe sampleLatestApplicationResponse
    }

    "return error when euvat-stubs returns 500" in {
      stubFor(
        post(urlEqualTo("/euvat-stubs/get-latest-application"))
          .willReturn(aResponse().withStatus(500))
      )

      connector.getLatestApplications(sampleLatestApplicationRequest).failed.futureValue shouldBe a[UpstreamErrorResponse]
    }
  }

  "EuVatStubsConnector.createApplication" should {
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
      applicationId     = 123456789,
      applicationNumber = "GB123456789",
      updateSeqNumber   = 123
    )

    "return 200 as successful saved application when euvat-stubs returns 200" in {
      stubFor(
        post(urlEqualTo("/euvat-stubs/create-application/3333333"))
          .willReturn(aResponse().withStatus(200).withBody(Json.toJson(response).toString))
      )

      connector.createApplication(appRequest, "3333333").futureValue shouldBe response
    }

    "return error when euvat-stubs returns 404" in {
      stubFor(
        post(urlEqualTo("/application"))
          .willReturn(aResponse().withStatus(404))
      )

      connector.createApplication(appRequest, "7777777").failed.futureValue shouldBe a[UpstreamErrorResponse]
    }
  }

  "EuVatStubsConnector.addPurchase" should {
    val purchaseResponse = AddPurchaseResponse(itemNumber = 4, updateSequenceNumber = 1)

    val purchaseRequest = AddPurchaseRequest(
      applicationId              = 123456,
      goodsDescriptionCategory   = Some("1234"),
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
      updateSequenceNumber       = None
    )

    "return purchase Response when rds-cande-proxy returns 200" in {
      stubFor(
        post(urlEqualTo("/euvat-stubs/add-purchase"))
          .willReturn(aResponse().withStatus(200).withBody(Json.toJson(purchaseResponse).toString))
      )

      connector.addPurchase(purchaseRequest).futureValue shouldBe purchaseResponse
    }

    "return error when rds-cande-proxy returns 500" in {
      stubFor(
        post(urlEqualTo("/euvat-stubs/add-purchase"))
          .willReturn(aResponse().withStatus(500))
      )

      connector.addPurchase(purchaseRequest).failed.futureValue shouldBe a[UpstreamErrorResponse]
    }
  }
}
