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

package models

import java.util.UUID

import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

case class Address(houseNameNumber: Option[String],
                   addressLine1: String,
                   addressLine2: String,
                   addressLine3: Option[String],
                   addressLine4: Option[String],
                   postCode: Option[String],
                   country: Option[String] = None,
                   uprn: Option[String] = None,
                   txid: String = Address.generateTxId,
                   auditRef: Option[String] = None
                  ){

  override def toString: String = {
    s"$addressLine1, $addressLine2${addressLine3.fold("")(l3 => s", $l3")}${addressLine4.fold("")(l4 => s", $l4")}${postCode.fold("")(pc => s", $pc")}${country.fold("")(c => s", $c")}"
  }
}

object Address {
  implicit val formats: OFormat[Address] = Json.format[Address]

  def generateTxId = UUID.randomUUID().toString

  def concatForRO(str1: String, str2: String) = {
    if((str1 + str2).length > 25){
      s"$str1, $str2"
    } else {
      s"$str1 $str2"
    }
  }


  val prePopWrites = new Writes[Address] {
    import utils.RichJson._

    override def writes(a: Address): JsValue = {

      Json.obj(
        "addressLine1" -> a.addressLine1,
        "addressLine2" -> a.addressLine2,
        "addressLine3" -> a.addressLine3,
        "addressLine4" -> a.addressLine4,
        "postcode" -> a.postCode,
        "country" -> a.country,
        "txid" -> a.txid,
        "auditRef" -> a.auditRef
      ).purgeOpts
    }
  }

  val ppobReads = (
    Reads.pure(None) and
      (__ \ "companyDetails" \ "pPOBAddress" \ "address" \ "addressLine1").read[String] and
      (__ \ "companyDetails" \ "pPOBAddress" \ "address" \ "addressLine2").read[String] and
      (__ \ "companyDetails" \ "pPOBAddress" \ "address" \ "addressLine3").readNullable[String] and
      (__ \ "companyDetails" \ "pPOBAddress" \ "address" \ "addressLine4").readNullable[String] and
      (__ \ "companyDetails" \ "pPOBAddress" \ "address" \ "postCode").readNullable[String] and
      (__ \ "companyDetails" \ "pPOBAddress" \ "address" \ "country").readNullable[String] and
      (__ \ "companyDetails" \ "pPOBAddress" \ "address" \ "uprn").readNullable[String] and
      (__ \ "companyDetails" \ "pPOBAddress" \ "address" \ "txid").read[String] and
      Reads.pure(None)
    )(Address.apply _)

  val addressReads = new Reads[Address] {
    override def reads(json: JsValue): JsResult[Address] = {
      val line1 = (json \ "addressLine1").as[String]
      val line2 = (json \ "addressLine2").as[String]
      val line3 = (json \ "addressLine3").asOpt[String]
      val line4 = (json \ "addressLine4").asOpt[String]
      val postcode = (json \ "postcode").asOpt[String]
      val country = (json \ "country").asOpt[String]

      JsSuccess(Address(country, line1, line2, line3, line4, postcode))
    }
  }

  val prePopReads = new Reads[Seq[Address]] {
    override def reads(json: JsValue): JsResult[Seq[Address]] = {
      JsSuccess((json \ "addresses").as[Seq[Address]](Reads.seq[Address](addressReads)))
    }
  }
}

case class PPOB(addressType: String,
                address: Option[Address])

object PPOB {
  implicit val formats = Json.format[PPOB]

  def empty = PPOB("", None)

  lazy val RO = "RO"
  lazy val LOOKUP = "LOOKUP"
  lazy val MANUAL = "MANUAL"
}

case class CHROAddress(premises : String,
                       address_line_1 : String,
                       address_line_2 : Option[String],
                       locality : String,
                       country : String = "UK",
                       po_box : Option[String],
                       postal_code : Option[String],
                       region : Option[String]) {

  override def toString: String = Seq(Some(s"${premises} ${address_line_1}"), address_line_2, Some(locality), po_box, region, postal_code, Some(country)).flatten.mkString(", ")


}

object CHROAddress extends CHAddressValidator {

  implicit val formats: Format[CHROAddress] = (
    (__ \ "premises").format[String](premisesValidator) and
      (__ \ "address_line_1").format[String](lineValidator) and
      (__ \ "address_line_2").formatNullable[String](lineValidator) and
      (__ \ "locality").format[String](lineValidator) and
      (__ \ "country").formatNullable[String](countryValidator).inmap[String](_.getOrElse("UK"), Some(_)) and
      (__ \ "po_box").formatNullable[String](lineValidator) and
      (__ \ "postal_code").formatNullable[String](postcodeValidator) and
      (__ \ "region").formatNullable[String](regionValidator)
    ) (CHROAddress.apply, unlift(CHROAddress.unapply))

  val auditWrites = new Writes[CHROAddress] {
    def writes(address: CHROAddress) = {
      Json.obj(
        "premises" -> address.premises,
        "addressLine1" -> address.address_line_1,
        "locality" -> address.locality
      ).++(
        address.address_line_2.fold(Json.obj())(line2 => Json.obj("addressLine2" -> line2))
      ).++(
        address.postal_code.fold(Json.obj())(pc => Json.obj("postCode" -> pc))
      ).++(
        address.region.fold(Json.obj())(r => Json.obj("region" -> r))
      )
    }
  }
}

case class CompanyDetails(companyName: String,
                          cHROAddress: CHROAddress,
                          pPOBAddress: PPOB,
                          jurisdiction: String)

object CompanyDetails {
  implicit val formatCH: OFormat[CHROAddress] = Json.format[CHROAddress]
  implicit val formats: OFormat[CompanyDetails] = Json.format[CompanyDetails]

  def createFromHandoff(companyName: String, roAddress: CHROAddress, ppob: PPOB, jurisdiction: String): CompanyDetails = {
    CompanyDetails(companyName, roAddress, ppob, jurisdiction = jurisdiction)
  }

  def updateFromHandoff(curr: CompanyDetails, companyName: String, roAddress: CHROAddress, jurisdiction: String): CompanyDetails = {
    curr.copy(companyName = companyName, cHROAddress = roAddress, jurisdiction = jurisdiction)
  }
}
