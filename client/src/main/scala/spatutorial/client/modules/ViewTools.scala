package spatutorial.client.modules

import japgolly.scalajs.react.vdom.prefix_<^._
import org.scalajs.dom.html


object ViewTools {
  def spinner: ReactTagOf[html.Image] = {
    <.img(^.src := "http://cdnjs.cloudflare.com/ajax/libs/semantic-ui/0.16.1/images/loader-large.gif")
  }
}
