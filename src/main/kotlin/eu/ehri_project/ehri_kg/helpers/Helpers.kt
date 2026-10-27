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

class Config(private val props: Properties) {

    constructor(propertiesFilePath: String) : this(Properties().apply {
        SourceHelper.readFileAsInputStream(propertiesFilePath).use { load(it) }
    })

    fun get(key: String): String {
        return props.getProperty(key)
    }

    fun getOptional(key: String): String? = props.getProperty(key)?.ifEmpty { null }

    fun propertyNames(): Set<String> = props.stringPropertyNames()

    /** A comma-separated list, which may be empty, but must be set. */
    fun getList(key: String): List<String> =
        requireNotNull(props.getProperty(key)) { "The property $key is missing from the configuration" }
            .split(',').map { it.trim() }.filter { it.isNotEmpty() }
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