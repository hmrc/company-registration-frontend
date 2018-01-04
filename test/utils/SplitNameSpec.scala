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

package utils

import models.Name
import uk.gov.hmrc.play.test.UnitSpec

class SplitNameSpec extends UnitSpec {

  "splitName" should {

    "return a name with no middle name" in {
      val name = "Foofoo Barbar"
      SplitName.splitName(name) shouldBe Name("Foofoo", None, Some("Barbar"))
    }

    "return a name with a middle name" in  {
      val name = "Foofoo Wibble Barbar"
      SplitName.splitName(name) shouldBe Name("Foofoo", Some("Wibble"), Some("Barbar"))
    }

    "return a name with 2 middle names separated by a space" in  {
      val name = "Foofoo Wibble Fooo Barbar"
      SplitName.splitName(name) shouldBe Name("Foofoo", Some("Wibble Fooo"), Some("Barbar"))
    }

    "return a name with 2 middle names separated by more than 1 space" in {
      val name = "Foofoo Wibble    Fooo Barbar"
      SplitName.splitName(name) shouldBe Name("Foofoo", Some("Wibble Fooo"), Some("Barbar"))
    }

    "return a name with only 1 string" in {
      val name = "Foofoo"
      SplitName.splitName(name) shouldBe Name("Foofoo", None, None)
    }

    "return a trimmed name that begins with a space" in {
      val name = " Foofoo"
      SplitName.splitName(name) shouldBe Name("Foofoo", None, None)
    }

    "return a trimmed name that ends with a space" in {
      val name = "Foofoo "
      SplitName.splitName(name) shouldBe Name("Foofoo", None, None)
    }

    "return a trimmed name that contains 2 spaces between each string" in {
      val name = "Foofoo  Wibble  Barbar"
      SplitName.splitName(name) shouldBe Name("Foofoo", Some("Wibble"), Some("Barbar"))
    }

    "return a name that contains an apostrophe" in {
      val name = "Foofoo O'Connor"
      SplitName.splitName(name) shouldBe Name("Foofoo", None, Some("O'Connor"))
    }

    "return a name that contains a number" in {
      val name = "Foofoo the 3rd"
      SplitName.splitName(name) shouldBe Name("Foofoo", Some("the"), Some("3rd"))
    }

    "return a name that contains a double-barrelled middle name" in {
      val name = "Foofoo Wibble-Fooo Barbar"
      SplitName.splitName(name) shouldBe Name("Foofoo", Some("Wibble-Fooo"), Some("Barbar"))
    }

    "return a name that contains a double-barrelled last name" in {
      val name = "Foofoo Fooo-Barbar"
      SplitName.splitName(name) shouldBe Name("Foofoo", None, Some("Fooo-Barbar"))
    }

    "return a name that contains a triple barrelled middle name" in {
      val name = "Foofoo Wibble-Fooo-Derek Barbar"
      SplitName.splitName(name) shouldBe Name("Foofoo", Some("Wibble-Fooo-Derek"), Some("Barbar"))
    }

    "return a name that contains a triple barrelled last name" in {
      val name = "Foofoo Wibble-Fooo-Barbar"
      SplitName.splitName(name) shouldBe Name("Foofoo", None, Some("Wibble-Fooo-Barbar"))
    }

    "return a name despite the characters the string contains" in {
      val name = "0123456789!£$%^&*-+=[]{}#~'@;:|!<>,./"
      SplitName.splitName(name) shouldBe Name("0123456789!£$%^&*-+=[]{}#~'@;:|!<>,./", None, None)
    }

    "return a name that contains the unicode for space" in {
      val name = "Foofoo\u0020Barbar"
      SplitName.splitName(name) shouldBe Name("Foofoo", None, Some("Barbar"))
    }
  }
}
