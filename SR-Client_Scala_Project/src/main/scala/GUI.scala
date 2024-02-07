import scalafx.Includes._
import scalafx.application.JFXApp
import scalafx.scene.Scene
import scalafx.scene.input.KeyEvent

class GUI(var keyBoardHandler: KeyBoardHandler, var display: Display) extends JFXApp {
  stage = new JFXApp.PrimaryStage {
    title.value = "client"
    scene = new Scene {
      content = display.panel
      onKeyPressed = (event: KeyEvent) => keyBoardHandler.handle(event)
    }
  }

  def updateGameState(newKeyBoardHandler: KeyBoardHandler, newDisplay: Display): Unit = {
    stage.scene.value.content = newDisplay.panel
    stage.scene.value.onKeyPressed = (event: KeyEvent) => newKeyBoardHandler.handle(event)
  }
}