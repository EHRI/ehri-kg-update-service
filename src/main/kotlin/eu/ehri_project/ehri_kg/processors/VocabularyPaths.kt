package eu.ehri_project.ehri_kg.processors

/**
 * The path, below http://lod.ehri-project-test.eu/vocabularies/, of the IRI of a historical agent or concept: its
 * Portal id's vocabulary part, with '-' for '_', then the rest of the id, e.g. ehri_pers-000001 -> ehri-pers/000001.
 * An id without a '-' gives its whole id as the vocabulary part, and nothing after the '/'.
 * Keep in step with vocabularyPath in conf/functions/EHRIFunctions.scala.
 */
fun vocabularyPath(id: String): String = setIdOf(id).replace('_', '-') + "/" + id.substringAfter('-', "")

/** The authority set or vocabulary of a historical agent or concept: its Portal id's part before the '-'. */
fun setIdOf(id: String): String = id.substringBefore('-')

/** [vocabularyPath] as the local part of a SPARQL prefixed name, e.g. `ehri_vocabs:ehri-pers\/000001`. */
fun vocabularyLocalName(id: String): String = vocabularyPath(id).replace("/", "\\/")
