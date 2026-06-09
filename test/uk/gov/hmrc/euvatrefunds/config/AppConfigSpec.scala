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

package uk.gov.hmrc.euvatrefunds.config

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration

class AppConfigSpec extends AnyWordSpec with Matchers {

  "AppConfig" should {

    "load appName from configuration" in {
      val config = Configuration(
        "appName" -> "test-service"
      )

      val appConfig = new AppConfig(config)
      appConfig.appName shouldBe "test-service"
    }

    "build baseUrl correctly from microservice config for rds-cande" in {
      val config = Configuration(
        "appName"                                        -> "test-service",
        "microservice.services.rds-cande-proxy.protocol" -> "http",
        "microservice.services.rds-cande-proxy.host"     -> "localhost",
        "microservice.services.rds-cande-proxy.port"     -> "9000"
      )

      val appConfig = new AppConfig(config)
      appConfig.baseUrl("rds-cande-proxy") shouldBe "http://localhost:9000"
      appConfig.appName                    shouldBe "test-service"
    }

    "build baseUrl correctly from microservice config for rds-datacache" in {
      val config = Configuration(
        "appName"                                            -> "test-service",
        "microservice.services.rds-datacache-proxy.protocol" -> "http",
        "microservice.services.rds-datacache-proxy.host"     -> "localhost",
        "microservice.services.rds-datacache-proxy.port"     -> "6992"
      )

      val appConfig = new AppConfig(config)
      appConfig.baseUrl("rds-datacache-proxy") shouldBe "http://localhost:6992"
      appConfig.appName                        shouldBe "test-service"
    }

  }
}
