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
import uk.gov.hmrc.euvatrefunds.models.responses.TraderKnownFactsResponse
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

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

  private val sampleFacts = TraderKnownFactsResponse(
    vatRegNumber           = 123456789,
    traderName             = Some("ABC GmbH"),
    postcode               = Some("AB12 3CD"),
    tradeClass             = Some("8765"),
    missingTraderIndicator = Some("N")
  )

  "EuVatStubsConnector.getTraderKnownFacts" should {

    "return the trader known facts when euvat-stubs returns 200" in {
      stubFor(
        get(urlEqualTo("/euvat-stubs/traders/getKnownFacts/123456789"))
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
}
