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
import uk.gov.hmrc.euvatrefunds.models.responses.TraderKnownFactsResponse
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class EuVatCandeServiceSpec extends AnyWordSpec with Matchers with MockitoSugar with ScalaFutures {

  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val hc: HeaderCarrier = HeaderCarrier()

  "EuVatCandeService.retrieveDirectDebits" should {

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
      val expectedResponse = TraderKnownFactsResponse(
        traderName   = Some("Test Trader"),
        vatRegNumber = 123456
      )

      when(mockCandeConnector.getTraderKnownFacts()(any()))
        .thenReturn(Future.successful(expectedResponse))

      val result = service.retrieveKnownFacts("123456").futureValue

      result shouldBe expectedResponse
      verify(mockCandeConnector, times(1)).getTraderKnownFacts()(any())
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
      val expectedResponse = TraderKnownFactsResponse(
        traderName   = Some("Test Trader"),
        vatRegNumber = 123456
      )

      when(mockStubsConnector.getTraderKnownFacts(any())(any()))
        .thenReturn(Future.successful(expectedResponse))

      val result = service.retrieveKnownFacts("123456").futureValue

      result shouldBe expectedResponse
      verify(mockStubsConnector, times(1)).getTraderKnownFacts(any())(any())
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

      when(mockCandeConnector.getTraderKnownFacts()(any()))
        .thenReturn(Future.failed(failure))

      val result = service.retrieveKnownFacts("123")

      whenReady(result.failed) { ex =>
        ex shouldBe failure
      }
    }
  }
}
