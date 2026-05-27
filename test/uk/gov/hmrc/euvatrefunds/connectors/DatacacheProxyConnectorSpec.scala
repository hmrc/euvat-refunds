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
import uk.gov.hmrc.euvatrefunds.models.TraderKnownFacts
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global

class DatacacheProxyConnectorSpec
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
           |microservice.services.rds-datacache-proxy.host = "$wireMockHost"
           |microservice.services.rds-datacache-proxy.port = $wireMockPort
           |""".stripMargin
      )
    )

  private lazy val appConfig: AppConfig = new AppConfig(configuration, new ServicesConfig(configuration))

  private lazy val connector: DatacacheProxyConnector = new DatacacheProxyConnector(appConfig, httpClientV2)

  private val sampleFacts = TraderKnownFacts(
    vrn                    = "123456789",
    traderName             = Some("ABC GmbH"),
    postcode               = Some("AB12 3CD"),
    tradeClass             = Some("8765"),
    missingTraderIndicator = Some(false)
  )

  "DatacacheProxyConnector.getTraderKnownFacts" should {

    "return the trader known facts when rds-datacache-proxy returns 200" in {
      stubFor(
        get(urlEqualTo("/traders/123456789"))
          .willReturn(aResponse().withStatus(200).withBody(Json.toJson(sampleFacts).toString))
      )

      connector.getTraderKnownFacts("123456789").futureValue shouldBe Some(sampleFacts)
    }

    "return None when rds-datacache-proxy returns 404" in {
      stubFor(
        get(urlEqualTo("/traders/999999999"))
          .willReturn(aResponse().withStatus(404))
      )

      connector.getTraderKnownFacts("999999999").futureValue shouldBe None
    }
  }
}
