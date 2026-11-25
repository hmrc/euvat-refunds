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
import uk.gov.hmrc.euvatrefunds.models.requests.UpscanInitiateRequest
import uk.gov.hmrc.euvatrefunds.models.responses.{UploadRequest, UpscanInitiateResponse}
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import scala.concurrent.ExecutionContext.Implicits.global

class UpscanInitiateConnectorSpec
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
           |microservice.services.upscan-initiate.host = "$wireMockHost"
           |microservice.services.upscan-initiate.protocol = "http"
           |microservice.services.upscan-initiate.port = $wireMockPort
           |""".stripMargin
      )
    )

  private lazy val appConfig: AppConfig = new AppConfig(configuration)
  private lazy val connector: UpscanInitiateConnector = new UpscanInitiateConnector(appConfig, httpClientV2)

  private val initiateUrl = "/upscan/v2/initiate"

  private val request = UpscanInitiateRequest(
    callbackUrl     = "http://localhost:18502/euvat-refunds/133/upscan-callback",
    successRedirect = "http://localhost:18501/supporting-documents/your-file",
    errorRedirect   = "http://localhost:18501/supporting-documents/upload-file",
    minimumFileSize = 1,
    maximumFileSize = 5242880
  )

  // upscan nests href and fields under uploadRequest
  private val upscanResponseJson =
    """
      |{
      |  "reference": "ref-123",
      |  "uploadRequest": {
      |    "href": "https://upscan.example/upload",
      |    "fields": { "key": "ref-123", "x-amz-algorithm": "AWS4-HMAC-SHA256" }
      |  }
      |}
      |""".stripMargin

  private val expectedResponse = UpscanInitiateResponse(
    reference = "ref-123",
    uploadRequest = UploadRequest(
      href   = "https://upscan.example/upload",
      fields = Map("key" -> "ref-123", "x-amz-algorithm" -> "AWS4-HMAC-SHA256")
    )
  )

  "UpscanInitiateConnector.initiate" should {

    "return the upscan response when upscan returns 200" in {
      stubFor(
        post(urlEqualTo(initiateUrl))
          .willReturn(aResponse().withStatus(200).withBody(upscanResponseJson))
      )

      connector.initiate(request).futureValue shouldBe expectedResponse
    }

    "send the request as JSON in the body" in {
      stubFor(
        post(urlEqualTo(initiateUrl))
          .willReturn(aResponse().withStatus(200).withBody(upscanResponseJson))
      )

      connector.initiate(request).futureValue

      verify(
        postRequestedFor(urlEqualTo(initiateUrl))
          .withRequestBody(equalToJson(Json.toJson(request).toString))
      )
    }

    "return error when upscan returns 400" in {
      stubFor(
        post(urlEqualTo(initiateUrl))
          .willReturn(aResponse().withStatus(400))
      )

      connector.initiate(request).failed.futureValue shouldBe a[UpstreamErrorResponse]
    }

    "return error when upscan returns 500" in {
      stubFor(
        post(urlEqualTo(initiateUrl))
          .willReturn(aResponse().withStatus(500))
      )

      connector.initiate(request).failed.futureValue shouldBe a[UpstreamErrorResponse]
    }
  }
}
