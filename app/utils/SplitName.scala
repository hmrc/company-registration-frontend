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

object SplitName extends SplitName

trait SplitName {

  def splitName(fullName: String): Name = {
    val split = fullName.trim.split("\\s+")

    val middleName = {
      val middleSplit = split
        .drop(1)
        .dropRight(1)
        .toList

      if(middleSplit.nonEmpty) Some(middleSplit.reduceLeft(_ + " " + _)) else None
    }

    val lastName = if(split.length < 2) None else Some(split.last)

    Name(split.head, middleName, lastName)
  }
}
