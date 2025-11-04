package eu.ehri_project.ehri_kg.helpers

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import java.io.File
import java.util.Properties

object SourceHelper {

    fun readFile(file: String) = File(file).readText()

    fun readFileAsInputStream(file: String) = File(file).inputStream()

    fun writeToFile(file: String, content: String) = File(file).appendText(content)

}

class Config(propertiesFilePath: String) {
    private val props = Properties()

    init {
        props.load(SourceHelper.readFileAsInputStream(propertiesFilePath))
    }

    fun get(key: String): String {
        return props.getProperty(key)
    }
}

class KafkaEmitter(val url: String, val topic: String) {
    val kafkaProducer: Producer<String, String> = createProducer()

    private fun createProducer(): Producer<String, String> {
        return KafkaProducer(mapOf(
            "bootstrap.servers" to url,
            "acks" to "all",
            "retries" to 0,
            "linger.ms" to 1,
            "key.serializer" to "org.apache.kafka.common.serialization.StringSerializer",
            "value.serializer" to "org.apache.kafka.common.serialization.StringSerializer"
        ))
    }

    fun sendMessage(message: String) {
        kafkaProducer.send(ProducerRecord(topic, message))
    }

}