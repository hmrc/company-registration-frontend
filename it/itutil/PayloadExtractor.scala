
package itutil

object PayloadExtractor {

  def extractPayload(url: String): String = {
    url.substring(url.indexOf("?request="), url.length).replace("?request=", "")
  }

}
