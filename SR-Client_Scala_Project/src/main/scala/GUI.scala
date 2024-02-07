import scalafx.Includes._
import scalafx.application.JFXApp
import scalafx.scene.Scene
import scalafx.scene.input.KeyEvent

class GUI(keyBoardHandler: KeyBoardHandler, display: Display) extends JFXApp {
  stage = new JFXApp.PrimaryStage {
    title.value = "client"
    scene = new Scene {
      content = display.panel
      onKeyPressed = (event: KeyEvent) => keyBoardHandler.handle(event)
    }
  }
}
