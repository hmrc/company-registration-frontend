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

package views.messages

import views.Viewtils

object VatDashboardMessages extends Viewtils {

  sealed trait Messages {
    val dashboardHeading: String
    val vatStatusHeading: String
    val registerForVATLink: String
    val disclosureHeading: String
    val disclosureP1: String
    def disclosureBullet1(threshold: Int): String
    def disclosureBullet2(threshold: Int): String
    def disclosureP2(threshold: Int): String
    val disclosureP3 : String
  }

  object English extends Messages {
    override val dashboardHeading = "VAT"
    override val vatStatusHeading = "Status:"
    override val registerForVATLink = "Register using another HMRC service (opens in new tab)"
    override val disclosureHeading = "Do you need to register for VAT?"
    override val disclosureP1 = "If a company doesn’t meet these criteria, you can register a company voluntarily if it sells or intends to sell VAT taxable goods or services."
    override def disclosureBullet1(threshold: Int) = s"your taxable sales were over ${monetary(threshold)} at the end of any month in the last year"
    override def disclosureBullet2(threshold: Int) = s"you expect your taxable sales to go over ${monetary(threshold)} in the next 30-day period"
    override def disclosureP2(threshold: Int) = s"If you were registered for VAT as a sole trader but have switched to a limited company, you’ll need to register your limited company for VAT if any of the above applies. You can register voluntarily if the company’s annual taxable sales are less than ${monetary(threshold)}."
    override val disclosureP3 = "You must wait until you get your Corporation Tax UTR before you register for VAT."
  }

  object Welsh extends Messages {
    override val dashboardHeading = "TAW"
    override val vatStatusHeading = "Statws:"
    override val registerForVATLink = "Cofrestru gan ddefnyddio gwasanaeth CThEM arall. (cysylltiad yn agor tab newydd)"
    override val disclosureHeading = "Oes angen i chi gofrestru ar gyfer TAW?"
    override val disclosureP1 = "Os nad yw cwmni’n bodloni’r meini prawf hyn, gallwch gofrestru cwmni’n wirfoddol os yw’n gwerthu neu’n bwriadu gwerthu nwyddau neu wasanaethau trethadwy TAW."
    override def disclosureBullet1(threshold: Int) = s"oedd eich gwerthiant trethadwy dros ${monetary(threshold)} ar ddiwedd unrhyw fis yn ystod y flwyddyn ddiwethaf"
    override def disclosureBullet2(threshold: Int) = s"rydych yn disgwyl i’ch trosiant trethadwy TAW fod yn fwy nag ${monetary(threshold)} yn ystod y cyfnod 30 diwrnod nesaf"
    override def disclosureP2(threshold: Int) = s"Os oeddech wedi cofrestru ar gyfer TAW fel unig fasnachwr ond wedi newid i gwmni cyfyngedig, bydd angen i chi gofrestru’ch cwmni cyfyngedig ar gyfer TAW os bydd unrhyw un o’r uchod yn berthnasol. Gallwch gofrestru’n wirfoddol os yw gwerthiannau trethadwy blynyddol y cwmni yn llai na ${monetary(threshold)}."
    override val disclosureP3 = "Mae’n rhaid i chi aros nes eich bod yn cael eich Cyfeirnod Unigryw y Trethdalwr (UTR) ar gyfer Treth Gorfforaeth cyn i chi gofrestru ar gyfer TAW."
  }
}
