/*
 * Copyright 2021 HM Revenue & Customs
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

package utils

case class SCRSException(message: String) extends Exception

object SCRSExceptions extends SCRSExceptions

trait SCRSExceptions {
  lazy val CompanyDetailsNotFoundException = SCRSException("Could not find a company details record - suspected direct routing before a record could be created")
  lazy val RegistrationIDNotFoundException = SCRSException("Could not find a company details record - suspected direct routing before a registration ID could be created")
}
