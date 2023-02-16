/*
 * Copyright 2023 HM Revenue & Customs
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

package forms.takeovers.formatters

import models.takeovers.{AddressChoice, OtherAddress, PreselectedAddress}
import play.api.data.FormError
import play.api.data.format.Formatter
import scala.util.Try

object AddressChoiceFormatter {

  val OtherKey: String = "Other"

  def addressChoiceFormatter(optName: Option[String],
                             addressCount: Int,
                             pageSpecificKey: String
                            ): Formatter[AddressChoice] = new Formatter[AddressChoice] {

    def errorMessage(key: String): Left[Seq[FormError], Nothing] =
      optName match {
        case Some(name) => Left(Seq(FormError(key, s"error.$pageSpecificKey.required", Seq(name))))
        case _ => Left(Seq(FormError(key, s"error.$pageSpecificKey.required")))
      }

    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], AddressChoice] =
      data.get(key) match {
        case Some(choice) if choice == OtherKey => Right(OtherAddress)
        case Some(choice) =>  Try(choice.toInt).toOption match {
          case Some(index) if index < addressCount && index >= 0 => Right(PreselectedAddress(index))
          case _ => errorMessage(key)
        }
        case None => errorMessage(key)
      }

    override def unbind(key: String, value: AddressChoice): Map[String, String] =
      value match {
        case PreselectedAddress(index) => Map(key -> index.toString)
        case OtherAddress => Map(key -> OtherKey)
      }

  }

}
