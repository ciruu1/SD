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
    private val devicesList = listOf(
        Device(Position(194.0, 166.0), "home/room1/lamp", 0, "Lamp 1", false, Color.YELLOW),
        Device(Position(823.0, 166.0), "home/room2/lamp", 0, "Lamp 2", false, Color.YELLOW),
        Device(Position(194.0, 688.0), "home/kitchen/lamp", 0, "Kitchen Lamp", false, Color.YELLOW),
        Device(Position(782.0, 606.0), "home/livingroom/lamp", 0, "Living Room Lamp", false, Color.YELLOW),
        Device(Position(516.0, 677.0), "home/foyer/lamp", 0, "Foyer Lamp", false, Color.YELLOW),
        Device(Position(478.0, 178.0), "home/bathroom/lamp", 0, "Bathroom Lamp", false, Color.YELLOW),
        Device(Position(557.0, 914.0), "home/foyer/entrance", 1, "Foyer Entrance", false, Color.LIGHTGREEN),
        Device(Position(379.0, 228.0), "home/bathroom/humidity", 0, "Bathroom Humidity Sensor", false, Color.SKYBLUE),
        Device(Position(780.0, 743.0), "home/livingroom/temperature", 0, "Living Room Temperature Sensor", false, Color.RED),
        Device(Position(70.0, 715.0), "home/kitchen/smoke", 1, "Kitchen Smoke Detector", false, Color.ORANGE)
    )

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
                    val receivedMessage = String(message.payload)
                    println("Mensaje recibido: $topic -> $receivedMessage")
                    handleReceivedMessage(topic, receivedMessage)
                }

                override fun deliveryComplete(token: IMqttDeliveryToken) {
                    println("Entrega completa")
                }
            })

            mqttClient.connect()

            // Suscribirse a los tópicos de cada dispositivo
            for (device in devicesList) {
                mqttClient.subscribe(device.topic, device.qos)
                println("Suscrito al tópico: ${device.topic} con QoS ${device.qos}")
            }

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
/*
        root.addEventHandler(MouseEvent.MOUSE_CLICKED) { event ->
            val x = event.x
            val y = event.y
            println("Coordenadas del clic: X=$x, Y=$y")
        }
*/

        // Dibujar las lámparas como círculos sobre la imagen
        for (device in devicesList) {
            val circle = Circle(device.pos.x, device.pos.y, 15.0, device.color)
            circle.isVisible = true
            root.children.add(circle)

            // Asociar el círculo con el dispositivo
            device.circle = circle

            // Añadir un evento de clic en cada lámpara
            circle.addEventHandler(MouseEvent.MOUSE_CLICKED) {
                toggleLamp(device.circle!!, device)
            }
        }

        val scene = Scene(root, WIDTH, HEIGHT)
        primaryStage.scene = scene
        primaryStage.title = "IoT Lamp Control"
        primaryStage.show()
    }

    // Manejar mensajes recibidos para cambiar el estado de las lámparas
    private fun handleReceivedMessage(topic: String, message: String) {
        // Encontrar el dispositivo correspondiente al tópico
        val device = devicesList.find { it.topic == topic }

        if (device != null) {
            val newState = message.contains("ON")
            device.state = newState

            // Cambiar el color del círculo según el estado
            device.circle?.fill = if (newState) Color.GREEN else device.color
            println("${device.name} cambiado a ${if (newState) "ON" else "OFF"}")
        } else {
            println("No se encontró dispositivo para el tópico: $topic")
        }
    }

    // Cambiar el estado de la lámpara y enviar el mensaje a MQTT
    private fun toggleLamp(circle: Circle, device: Device) {
        // Cambiar el estado de la lámpara
        val currentState = device.state
        val newState = !currentState
        device.state = newState

        // Cambiar el color del círculo según el estado
        circle.fill = if (newState) Color.GREEN else device.color

        // Enviar el estado de la lámpara por MQTT con el QoS correspondiente
        sendMqttMessage(device.topic, newState, device.qos, device.name)
    }

    // Enviar mensaje a MQTT con un QoS específico
    private fun sendMqttMessage(topic: String, state: Boolean, qos: Int, name: String) {
        val message = if (state) "$name: ON" else "$name: OFF"
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

class Device(val pos: Position, val topic: String, var qos: Int, val name: String, var state: Boolean, var color: Color) {
    var circle: Circle? = null // Referencia al círculo en la interfaz gráfica
}

class Position(val x: Double, val y: Double);

fun main() {
    Application.launch(IoTApp::class.java)
}