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
import uk.gov.hmrc.euvatrefunds.models.requests.ApplicationRequest
import uk.gov.hmrc.euvatrefunds.models.responses.ApplicationResponse
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.Implicits.global

class RdsCandeProxyConnectorSpec
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
           |microservice.services.rds-cande-proxy.host = "$wireMockHost"
           |microservice.services.rds-cande-proxy.protocol = "http"
           |microservice.services.rds-cande-proxy.port = $wireMockPort
           |""".stripMargin
      )
    )

  private lazy val appConfig: AppConfig = new AppConfig(configuration)

  private lazy val connector: RdsCandeProxyConnector = new RdsCandeProxyConnector(appConfig, httpClientV2)

  "RdsCandeProxyConnector.createApplication" should {
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
    "return successful after the refund application is created when rds-cande-proxy returns 200" in {
      stubFor(
        post(urlEqualTo("/rds-cande-proxy/euvat/create-application"))
          .willReturn(aResponse().withStatus(200).withBody(Json.toJson(expectedResponse).toString))
      )

      connector.createApplication(appRequest).futureValue shouldBe expectedResponse
    }

    "return error when rds-cande-proxy returns 404" in {
      stubFor(
        post(urlEqualTo("/invalid-application"))
          .willReturn(aResponse().withStatus(404))
      )

      connector.createApplication(appRequest).failed.futureValue shouldBe a[UpstreamErrorResponse]
    }
  }
}
