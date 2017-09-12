/*
 * Copyright 2017 HM Revenue & Customs
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

package services

import models.{Address, HMRCAddressValidator}
import play.api.Logger
import address.client.{AddressRecord, Address => LookupAddress}

import scala.util.{Success, Failure, Try}

trait AddressConverter extends HMRCAddressValidator {

  private def blankToNone(line: Option[String]) = line match {
    case Some("") | None => None
    case _ => line
  }

  private def blankToNone(line: String) = line match {
    case "" => None
    case _ => Some(line)
  }

  private def ifBlankUse(lineToCheck: String, lineToUse: String) = blankToNone(lineToCheck) match {
    case Some(line) => line
    case _ => lineToUse
  }


  def convertManualAddressToHMRCFormat(manualAddress: Option[Address]): Option[Address] = {

    def formatLine1(houseNameNumber: String, line1: String): String = {
      Try[String](s"${houseNameNumber.toInt} ${line1}") match {
        case Success(combinedLine1) => combinedLine1
        case Failure(_) => s"$houseNameNumber, $line1"
      }
    }

    Logger.debug(s"[ReviewAddressService] - [convertManualAddressToHMRCFormat] : Address : ${manualAddress}")
    manualAddress flatMap {
      address =>
        blankToNone(address.houseNameNumber) match {
          case None => manualAddress
          case Some(houseNameNumber) =>
            val combinedLine1 = formatLine1(houseNameNumber, address.addressLine1)
            val hmrcAddress = if (combinedLine1.length <= maxLineLength) {
              Logger.debug(s"[ReviewAddressService] - [convertAddressForSaving] : Address HNN & L1 UNDER ${maxLineLength} chars")
              Address(
                houseNameNumber = None,
                addressLine1 = combinedLine1,
                addressLine2 = address.addressLine2,
                addressLine3 = blankToNone(address.addressLine3),
                addressLine4 = blankToNone(address.addressLine4),
                postCode = address.postCode,
                country = address.country,
                txid = address.txid
              )
            } else {
              Logger.debug(s"[ReviewAddressService] - [convertAddressForSaving] : Address HNN & L1 OVER 27 chars")
              Address(
                houseNameNumber = None,
                addressLine1 = houseNameNumber,
                addressLine2 = address.addressLine1,
                addressLine3 = blankToNone(Some(address.addressLine2)),
                addressLine4 = trimAddressLine4(blankToNone(address.addressLine3)),
                postCode = address.postCode,
                country = address.country,
                txid = address.txid
              )
            }
            Some(hmrcAddress)
      }
    }
  }

  def convertLookupAddressToHMRCFormat(addressRecord: AddressRecord): Address = {

    def getLines(address: LookupAddress): (String, String, Option[String], Option[String]) = {
      val n = 4
      val lines = address.lines

      val linesWithTown = address.town.fold(lines.take(n))(town => lines.take(n - 1) ++ Seq(town)).filter(_ != "")

      val padded = linesWithTown ++ Seq.fill(n - linesWithTown.length)("")

      val line1 = padded(0)
      val line2 = padded(1)
      val line3 = blankToNone(padded(2))
      val line4 = trimAddressLine4(blankToNone(padded(3)))
      (line1, line2, line3, line4)
    }


    val address = addressRecord.address
    val (line1, line2, line3, line4) = getLines(address)
    Address(
      None,
      line1,
      line2,
      line3,
      line4,
      Some(address.postcode),
      Some(address.country.name),
      uprn = Some(addressRecord.id)
    )
  }

  private[services] def trimAddressLine4(line4: Option[String]): Option[String] = {
    line4 match {
      case Some(line) if line.length > 18 => Some(line.substring(0, 18))
      case _ => line4
    }
  }
}
