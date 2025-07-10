package org.dba.mongofx

import javafx.application.Application
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.stage.Stage

class MainApplication : Application() {
    override fun start(stage: Stage) {

        MongoManage.connect()

        val fxmlLoader = FXMLLoader(MainApplication::class.java.getResource("main.fxml"))
        val scene = Scene(fxmlLoader.load(), 650.0, 550.0)
        stage.title = "Hello!"
        stage.scene = scene
        stage.show()
    }
    override fun stop() {
        super.stop()
        MongoManage.close()
    }
}

fun main() {
    Application.launch(MainApplication::class.java)
}