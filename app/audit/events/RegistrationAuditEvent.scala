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

package audit.events

import play.api.libs.json.JsObject
import play.api.mvc.{Request, AnyContent}
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

import RegistrationAuditEvent.buildTags
import uk.gov.hmrc.http.HeaderCarrier

case class TagSet(clientIP : Boolean,
                  clientPort : Boolean,
                  requestId : Boolean,
                  sessionId : Boolean,
                  deviceId : Boolean,
                  authorisation : Boolean,
                  path: Boolean)

object TagSet {
  val ALL_TAGS = TagSet(true, true, true, true, true, true, true)
  val NO_TAGS = TagSet(false, false, false, false, false, false, false)
}

import TagSet.ALL_TAGS

abstract class RegistrationAuditEvent(auditType: String, detail: JsObject, tagSet: TagSet = ALL_TAGS)(implicit hc: HeaderCarrier, req: Request[AnyContent])
  extends ExtendedDataEvent(
    auditSource = "company-registration-frontend",
    auditType = auditType,
    detail = detail,
    tags = buildTags(auditType, tagSet)
  )

object RegistrationAuditEvent {

  val EXT_USER_ID = "externalUserId"
  val AUTH_PROVIDER_ID = "authProviderId"
  val JOURNEY_ID = "journeyId"
  val COMPANY_NAME = "companyName"
  val RO_ADDRESS = "registeredOfficeAddress"
  val PATH = "path"

  def buildTags(auditType: String, tagSet: TagSet)(implicit hc: HeaderCarrier, req: Request[AnyContent]) = {
    Map("transactionName" -> auditType) ++
      buildClientIP(tagSet) ++
      buildClientPort(tagSet) ++
      buildRequestId(tagSet) ++
      buildSessionId(tagSet) ++
      buildAuthorization(tagSet) ++
      buildDeviceId(tagSet) ++
      buildPath(tagSet)
  }

  private def buildClientIP(tagSet: TagSet)(implicit hc: HeaderCarrier) =
    if(tagSet.clientIP) Map("clientIP" -> hc.trueClientIp.getOrElse("-")) else Map()

  private def buildClientPort(tagSet: TagSet)(implicit hc: HeaderCarrier) =
    if(tagSet.clientPort) Map("clientPort" -> hc.trueClientPort.getOrElse("-")) else Map()

  private def buildRequestId(tagSet: TagSet)(implicit hc: HeaderCarrier) =
    if(tagSet.requestId) Map(hc.names.xRequestId -> hc.requestId.map(_.value).getOrElse("-")) else Map()

  private def buildSessionId(tagSet: TagSet)(implicit hc: HeaderCarrier) =
    if(tagSet.sessionId) Map(hc.names.xSessionId -> hc.sessionId.map(_.value).getOrElse("-")) else Map()

  private def buildAuthorization(tagSet: TagSet)(implicit hc: HeaderCarrier) =
    if(tagSet.authorisation) Map(hc.names.authorisation -> hc.authorization.map(_.value).getOrElse("-")) else Map()

  private def buildDeviceId(tagSet: TagSet)(implicit hc: HeaderCarrier) =
    if(tagSet.deviceId) Map(hc.names.deviceID -> hc.deviceID.getOrElse("-")) else Map()

  private def buildPath(tagSet: TagSet)(implicit req: Request[AnyContent]) =
    if(tagSet.path) Map(PATH -> req.path) else Map()
}
