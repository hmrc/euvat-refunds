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

import play.api.Configuration

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.FiniteDuration

@Singleton
class AppConfig @Inject() (config: Configuration):

  val appName: String = config.get[String]("appName")

  def baseUrl(service: String): String = {
    val protocol: String = config.get[String](s"microservice.services.$service.protocol")
    val host: String = config.get[String](s"microservice.services.$service.host")
    val port: String = config.get[String](s"microservice.services.$service.port")

    s"$protocol://$host:$port"
  }

  val upscanCallbackBaseUrl: String = config.get[String]("upscan.callbackBaseUrl")
  val upscanSuccessRedirect: String = config.get[String]("upscan.successRedirect")
  val upscanErrorRedirect: String = config.get[String]("upscan.errorRedirect")
  val upscanMinimumFileSize: Long = config.get[Long]("upscan.minimumFileSize")
  val upscanMaximumFileSize: Long = config.get[Long]("upscan.maximumFileSize")
  val validationStatusTtl: FiniteDuration = config.get[FiniteDuration]("mongodb.validationStatus.ttl")
  val replaceIndexes: Boolean = config.get[Boolean]("mongodb.replaceIndexes")
