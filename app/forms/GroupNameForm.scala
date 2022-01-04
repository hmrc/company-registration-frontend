/*
 * Copyright 2022 HM Revenue & Customs
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

package forms

import models.{EmptyStringValidator, GroupCompanyName}
import play.api.data.{Form, Mapping}
import play.api.data.Forms._
import uk.gov.voa.play.form.ConditionalMappings._
import utils.SCRSValidators._

object GroupNameForm extends EmptyStringValidator {


  def gcnApply(groupName: String, other: Option[String]): GroupCompanyName = {
    if(groupName ==  "otherName") {
      GroupCompanyName(other.get,"Other")
    } else {
      GroupCompanyName(groupName,"CohoEntered")
    }
  }

  def gcnUnapply(gcn: GroupCompanyName):Option[(String, Option[String])] = {
    if(gcn.nameType == "Other") {
      Some(("otherName", Some(gcn.name)))
    }
    else {
      Some((gcn.name, None))
    }
  }
  private def ifOther(mapping: Mapping[String]): Mapping[Option[String]] = mandatoryIfEqual("groupName", "otherName", mapping)


  def form: Form[GroupCompanyName] = Form(
    mapping(
      "groupName" -> customErrorTextValidation,
      "otherName" -> ifOther(text.verifying(shareholderNameValidation))
    )(gcnApply)(gcnUnapply)
  )
}
