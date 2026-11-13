package eu.ehri_project.ehri_kg.batch

import eu.ehri_project.ehri_kg.helpers.Config
import eu.ehri_project.ehri_kg.processors.KgSets
import java.io.File
import java.security.MessageDigest

/**
 * What the data mapped from a chunk depends on besides the chunk itself: the authority sets and vocabularies in the KG,
 * which decide the access points units link to, and the mapping rules with the functions they load. Data mapped with
 * one configuration mustn't be mixed with data mapped with another.
 */
object MappingConfiguration {

    private val functions = Regex("""FUNCTIONS\s+\w+\s+<scala:\s*([^>]+)>""")

    /** A description of the configuration, which differs whenever the data mapped from a chunk could. */
    fun describe(config: Config): String {
        val sets = KgSets(config)
        val rules = config.propertyNames().filter { it.endsWith("ShexmlMappingRules") }.map { config.get(it) }
        val files = (rules + rules.flatMap { file ->
            functions.findAll(File(file).readText()).map { it.groupValues[1].trim() }
        }).distinct().sorted()
        return (listOf(
            "personSets=${sets.personSets.joinToString(",")}",
            "corporateBodySets=${sets.corporateBodySets.joinToString(",")}",
            "vocabularySets=${sets.vocabularySets.joinToString(",")}",
        ) + files.map { path -> File(path).takeIf { it.isFile }?.let { "$path ${sha256(it)}" } ?: path }
        ).joinToString("") { "$it\n" }
    }

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
}
