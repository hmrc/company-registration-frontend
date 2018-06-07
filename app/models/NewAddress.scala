/*
 * Copyright 2018 HM Revenue & Customs
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

package models

import play.api.data.validation.ValidationError
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import utils.SCRSValidators

case class NewAddress(addressLine1: String,
                      addressLine2: String,
                      addressLine3: Option[String],
                      addressLine4: Option[String],
                      postcode: Option[String],
                      country: Option[String],
                      auditRef: Option[String] = None){

  def mkString: String = {
    s"$addressLine1, $addressLine2${addressLine3.fold("")(l3 => s", $l3")}${addressLine4.fold("")(l4 => s", $l4")}${postcode.fold("")(pc => s", $pc")}${country.fold("")(c => s", $c")}"
  }

  def isEqualTo(other: NewAddress): Boolean = {
    (addressLine1.toLowerCase == other.addressLine1.toLowerCase) && (
      (postcode, other.postcode, country, other.country) match {
      case (Some(p), Some(oP), _      , _       ) => p.toLowerCase == oP.toLowerCase
      case (None   , None    , Some(c), Some(oC)) => c.toLowerCase == oC.toLowerCase
      case _ => false
    })
  }
}

object NewAddress {

  val readAddressType: Reads[String] = (__ \\ "addressType").read[String]



  val roReads: Reads[NewAddress] = new Reads[NewAddress] {
    def reads(json: JsValue): JsResult[NewAddress] = {

      val oPremises      = (json \ "companyDetails" \ "cHROAddress" \ "premises").asOpt[String]
      val oAddressLine1  = (json \ "companyDetails" \ "cHROAddress" \ "address_line_1").asOpt[String]
      val oAddressLine2  = (json \ "companyDetails" \ "cHROAddress" \ "address_line_2").asOpt[String]
      val oLocality      = (json \ "companyDetails" \ "cHROAddress" \ "locality").asOpt[String]
      val oPostalCode    = (json \ "companyDetails" \ "cHROAddress" \ "postal_code").asOpt[String]
      val oCountry       = (json \ "companyDetails" \ "cHROAddress" \ "country").asOpt[String]

      val (oLine1, oLine2) = oPremises match {
        case Some(premises) => oAddressLine1 match {
          case Some(addressLine1) => if((premises + " " + addressLine1).length > 26) {
            (Some(premises), Some(addressLine1))
          } else {
            (Some(premises + " " + addressLine1), None)
          }
          case None => (Some(premises), None)
        }
        case None => (oAddressLine1, None)
      }

      val lines: Seq[String] = Seq(oLine1, oLine2, oAddressLine2, oLocality).flatten

      def incorrectAddressErrorMessage(msg: String): String = {
        s"$msg\n" +
          s"Lines defined:\n" +
          s"premises: ${oPremises.isDefined}\n" +
          s"address line 1: ${oAddressLine1.isDefined}\n" +
          s"address line 2: ${oAddressLine2.isDefined}\n" +
          s"locality: ${oLocality.isDefined}\n" +
          s"postcode: ${oPostalCode.isDefined}\n" +
          s"country: ${oCountry.isDefined}"
      }

      if(lines.length < 2) {
        JsError(incorrectAddressErrorMessage(s"Only ${lines.length} address lines returned for RO Address"))
      } else if (oPostalCode.isEmpty && oCountry.isEmpty) {
        JsError(incorrectAddressErrorMessage(s"Neither postcode nor country returned for RO Address"))
      } else {
        JsSuccess(NewAddress(
          addressLine1  = trimLine(lines.head, 27),
          addressLine2  = trimLine(lines(1), 27),
          addressLine3  = trimLine(lines.lift(2), 27),
          addressLine4  = trimLine(lines.lift(3), 18),
          postcode      = oPostalCode,
          country       = if(oPostalCode.isEmpty) oCountry else None
        ))
      }
    }
  }

  val ppobFormats: Format[NewAddress] = {
    val ppobPath = __ \ "companyDetails" \ "pPOBAddress" \ "address"
    (
      (ppobPath ++ (__ \ "addressLine1")).format[String] and
      (ppobPath ++ (__ \ "addressLine2")).format[String] and
      (ppobPath ++ (__ \ "addressLine3")).formatNullable[String] and
      (ppobPath ++ (__ \ "addressLine4")).formatNullable[String] and
      (ppobPath ++ (__ \ "postCode")).formatNullable[String] and
      (ppobPath ++ (__ \ "country")).formatNullable[String] and
      (ppobPath ++ (__ \ "auditRef")).formatNullable[String]
    )(NewAddress.apply, unlift(NewAddress.unapply))
  }

  val verifyRoToPPOB: Format[NewAddress] = {
    (
      (__ \ "addressLine1").format[String] and
        (__ \ "addressLine2").format[String] and
        (__ \ "addressLine3").formatNullable[String] and
        (__ \ "addressLine4").formatNullable[String] and
        (__ \ "postCode").formatNullable[String] and
        (__ \ "country").formatNullable[String] and
        (__ \ "auditRef").formatNullable[String]
    )(NewAddress.apply, unlift(NewAddress.unapply))
  }

  def trimLine(toTrim: String, trimTo: Int): String = {
    val trimmed = toTrim.trim
    if(trimmed.length > trimTo) trimmed.substring(0, trimTo) else trimmed
  }

  def trimLine(toTrim: Option[String], trimTo: Int): Option[String] = toTrim map (trimLine(_, trimTo))

  def validatePostcode(pc: Option[String]): Option[String] = pc.filter(_.matches(SCRSValidators.postCodeRegex.regex))

  def makeAddress(postcode: Option[String], country: Option[String], lines: List[String], auditRef: Option[String]): NewAddress = {
    NewAddress(
      addressLine1 = trimLine(lines.head, 27),
      addressLine2 = trimLine(lines(1), 27),
      addressLine3 = trimLine(lines.lift(2), 27),
      addressLine4 = trimLine(lines.lift(3), 18),
      postcode     = postcode,
      country      = country,
      auditRef     = auditRef
    )
  }

  val addressLookupReads: Reads[NewAddress] = new Reads[NewAddress] {
    def reads(json: JsValue): JsResult[NewAddress] = {

      val unvalidatedPostCode = (json \ "address" \ "postcode").asOpt[String]
      val validatedPostcode   = validatePostcode(unvalidatedPostCode)

      val addressLines  = (json \ "address" \ "lines").as[List[String]]
      val countryName   = (json \ "address" \ "country" \ "name").asOpt[String]
      val auditRef      = (json \ "auditRef").asOpt[String]

      (validatedPostcode, countryName, addressLines) match {
        case (None         , None       , _    )                      => JsError(ValidationError(s"no country or valid postcode"))
        case (_            , _          , lines) if lines.length < 2  => JsError(ValidationError(s"only ${lines.length} lines provided from address-lookup-frontend"))
        case (None         , c @ Some(_), lines)                      => JsSuccess(makeAddress(None, c, lines, auditRef))
        case (p @ Some(pc) , _          , lines)                      => JsSuccess(makeAddress(p, None, lines, auditRef))
      }
    }
  }
}
