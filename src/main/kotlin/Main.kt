package com.etsisi

import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.shape.Circle
import javafx.stage.Stage
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

const val WIDTH = 1280.0/1.3
const val HEIGHT = 1239.0/1.3

class IoTApp : Application() {
    // Coordenadas, tópicos y QoS predefinidos para las lámparas
    private val lampDetails = listOf(
        Triple(Pair(194.0, 166.0), "home/room1/lamp", 0), // QoS 0 para esta lámpara
        Triple(Pair(823.0, 166.0), "home/room2/lamp", 0),
        Triple(Pair(194.0, 688.0), "home/kitchen/lamp", 0),
        Triple(Pair(782.0, 606.0), "home/livingroom/lamp", 0),
        Triple(Pair(516.0, 677.0), "home/foyer/lamp", 0),
        Triple(Pair(478.0, 178.0), "home/bathroom/lamp", 0),
        Triple(Pair(557.0, 914.0), "home/foyer/entrance", 1),
        Triple(Pair(379.0, 228.0), "home/bathroom/humidity", 0),
        Triple(Pair(780.0, 743.0), "home/livingroom/temperature", 0),
        Triple(Pair(70.0, 715.0), "home/kitchen/smoke", 1),

    )

    // Estado inicial de las lámparas (apagadas = false)
    private val lampStates = mutableMapOf<Pair<Double, Double>, Boolean>().apply {
        lampDetails.forEach { this[it.first] = false }
    }

    // MQTT client settings
    private val brokerUrl = "tcp://localhost:1883" // Reemplaza con la IP de tu broker MQTT
    private val clientId = "IoTAppClient"

    private lateinit var mqttClient: MqttClient

    override fun start(primaryStage: Stage) {
        // Configurar cliente MQTT
        mqttClient = MqttClient(brokerUrl, clientId, MemoryPersistence())

        try {
            // Configurar el callback para manejar la recepción de mensajes y la conexión perdida
            mqttClient.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable) {
                    println("Conexión perdida con el broker MQTT: ${cause.message}")
                }

                override fun messageArrived(topic: String, message: MqttMessage) {
                    println("Mensaje recibido: $topic -> ${String(message.payload)}")
                }

                override fun deliveryComplete(token: IMqttDeliveryToken) {
                    println("Entrega completa")
                }
            })

            mqttClient.connect()

        } catch (e: MqttException) {
            println("Error al conectar al broker MQTT: ${e.message}")
            e.printStackTrace()
        }

        // Crear un panel donde cargar la imagen de la planta del piso
        val root = Pane()

        // Aquí se carga la imagen de la planta del piso
        val img = Image("file:./test.png")
        val imageView = ImageView()
        imageView.image = img
        imageView.fitWidth = WIDTH
        imageView.fitHeight = HEIGHT
        imageView.x = 0.0
        imageView.y = 0.0
        imageView.isPreserveRatio = true
        root.children.add(imageView)

        // Añadir un evento de clic al panel para capturar las coordenadas
        root.addEventHandler(MouseEvent.MOUSE_CLICKED) { event ->
            val x = event.x
            val y = event.y
            println("Coordenadas del clic: X=$x, Y=$y")
        }

        // Dibujar las lámparas como círculos sobre la imagen
        for ((position, topic, qos) in lampDetails) {
            val circle = Circle(position.first, position.second, 15.0, getColor(topic))
            circle.isVisible = true
            root.children.add(circle)

            // Añadir un evento de clic en cada lámpara
            circle.addEventHandler(MouseEvent.MOUSE_CLICKED) {

                toggleLamp(circle, position, topic, qos)
            }
        }

        val scene = Scene(root, WIDTH, HEIGHT)
        primaryStage.scene = scene
        primaryStage.title = "IoT Lamp Control"
        primaryStage.show()
    }

    private fun getColor(topic: String): Color {
        if (topic.endsWith("lamp"))
            return Color.YELLOW
        else if (topic.endsWith("entrance"))
            return Color.LIGHTGREEN
        else if (topic.endsWith("temperature"))
            return Color.RED
        else if (topic.endsWith("humidity"))
            return Color.SKYBLUE
        else if (topic.endsWith("smoke"))
            return Color.ORANGE
        return Color.BLACK
    }

    // Cambiar el estado de la lámpara y enviar el mensaje a MQTT
    private fun toggleLamp(circle: Circle, position: Pair<Double, Double>, topic: String, qos: Int) {
        // Cambiar el estado de la lámpara
        val currentState = lampStates[position] ?: false
        val newState = !currentState
        lampStates[position] = newState

        // Cambiar el color del círculo según el estado
        circle.fill = if (newState) Color.GREEN else getColor(topic)

        // Enviar el estado de la lámpara por MQTT con el QoS correspondiente
        sendMqttMessage(topic, newState, qos)
    }

    // Enviar mensaje a MQTT con un QoS específico
    private fun sendMqttMessage(topic: String, state: Boolean, qos: Int) {
        val message = if (state) "$topic: ON" else "$topic: OFF"
        try {
            val mqttMessage = MqttMessage(message.toByteArray())
            mqttMessage.qos = qos // Usar el QoS específico de esta lámpara
            mqttClient.publish(topic, mqttMessage)
            println("Mensaje MQTT enviado a $topic con QoS $qos: $message")
        } catch (e: MqttException) {
            println("Error al enviar mensaje MQTT: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun stop() {
        try {
            mqttClient.disconnect() // Cerrar la conexión MQTT al cerrar la app
            mqttClient.close()
        } catch (e: MqttException) {
            println("Error al desconectar del broker MQTT: ${e.message}")
        }
    }
}

fun main() {
    Application.launch(IoTApp::class.java)
}