package eu.ehri_project.ehri_kg.validation

import eu.ehri_project.ehri_kg.helpers.Config
import eu.ehri_project.ehri_kg.model.EHRITypes
import org.apache.jena.graph.Graph

/** The validators of each entity type's mapped data, for the types whose `<type>Shapes` property is set. */
class TypeValidators(private val config: Config) {

    private val validators = mutableMapOf<EHRITypes, KGValidator?>()

    /** The violations of a type's mapped data, or none if the type has no shapes configured. */
    fun validate(type: EHRITypes, graph: Graph): List<Violation> =
        validators.getOrPut(type) { config.getOptional(shapesProperty(type))?.let(::KGValidator) }
            ?.validate(graph).orEmpty()

    companion object {
        fun shapesProperty(type: EHRITypes): String = when(type) {
            EHRITypes.COUNTRY -> "countriesShapes"
            EHRITypes.INSTITUTION -> "institutionsShapes"
            EHRITypes.ARCHIVAL_DESCRIPTION -> "archivalDescriptionsShapes"
            EHRITypes.VOCABULARY -> "vocabulariesShapes"
            EHRITypes.CORPORATE_BODY -> "corporateBodiesShapes"
            EHRITypes.PERSON -> "personsShapes"
            EHRITypes.LINK -> "linksShapes"
        }
    }
}
