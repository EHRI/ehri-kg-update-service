package eu.ehri_project.ehri_kg.helpers

import java.io.File
import java.util.Properties

object SourceHelper {

    fun readFile(file: String) = File(file).readText()

    fun readFileAsInputStream(file: String) = File(file).inputStream()

}

object Config {
    private val props = Properties()

    init {
        props.load(SourceHelper.readFileAsInputStream("src/main/resources/config.properties"))
    }

    fun get(key: String): String {
        return props.getProperty(key)
    }

}