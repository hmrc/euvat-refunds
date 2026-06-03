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
import uk.gov.hmrc.euvatrefunds.models.TraderKnownFactsResponse
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

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

  private val sampleFacts = TraderKnownFactsResponse(
    vatRegNumber           = 123456789,
    traderName             = Some("ABC GmbH"),
    postcode               = Some("AB12 3CD"),
    tradeClass             = Some("8765"),
    missingTraderIndicator = Some("N")
  )

  "RdsCandeProxyConnector.getTraderKnownFacts" should {

    "return the trader known facts when rds-cande-proxy returns 200" in {
      stubFor(
        get(urlEqualTo("/rds-cande-proxy/euvat/traders/getKnownFacts"))
          .willReturn(aResponse().withStatus(200).withBody(Json.toJson(sampleFacts).toString))
      )

      connector.getTraderKnownFacts().futureValue shouldBe sampleFacts
    }

    "return error when rds-cande-proxy returns 404" in {
      stubFor(
        get(urlEqualTo("/traders/999999999"))
          .willReturn(aResponse().withStatus(404))
      )

      connector.getTraderKnownFacts().failed.futureValue shouldBe a[UpstreamErrorResponse]
    }
  }
}
