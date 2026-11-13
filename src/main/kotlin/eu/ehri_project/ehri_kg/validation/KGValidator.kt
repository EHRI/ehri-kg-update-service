package eu.ehri_project.ehri_kg.validation

import org.apache.jena.graph.Graph
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.shacl.ShaclValidator
import org.apache.jena.shacl.Shapes
import org.apache.jena.shex.Shex
import org.apache.jena.shex.ShapeMap
import org.apache.jena.shex.ShexSchema
import org.apache.jena.shex.ShexStatus
import org.apache.jena.shex.ShexValidator
import java.io.File

/** A node of the mapped data that does not conform to one of its shapes. */
data class Violation(val schema: String, val focusNode: String, val shape: String, val message: String) {
    override fun toString() = "$schema: $focusNode does not conform to $shape: $message"
}

/**
 * Validates mapped data against the ShEx and SHACL shapes of one entity type, as validateAll.sh of the EHRI-KG-mapping
 * repository does: the ShEx schema `<shapes>.shex` with the shape map `<shapes>.smap`, and the SHACL shapes
 * `<shapes>.shacl.ttl`, using whichever of them exist.
 *
 * The shapes describe the data mapped for an entity of that type, not the whole KG: other types' data can match
 * a shape's targets without conforming to it.
 */
class KGValidator(shapes: String) {

    private val shexFile = File("$shapes.shex")
    private val shaclFile = File("$shapes.shacl.ttl")

    private val shex: Pair<ShexSchema, ShapeMap>? = shexFile.takeIf { it.exists() }
        ?.let { Shex.readSchema(it.path) to Shex.readShapeMap("$shapes.smap") }

    private val shacl: Shapes? = shaclFile.takeIf { it.exists() }?.let { Shapes.parse(RDFDataMgr.loadGraph(it.path)) }

    init {
        require(shex != null || shacl != null) { "No ShEx or SHACL shapes found for $shapes" }
    }

    fun validate(graph: Graph): List<Violation> = validateShex(graph) + validateShacl(graph)

    private fun validateShex(graph: Graph): List<Violation> = shex?.let { (schema, shapeMap) ->
        buildList {
            ShexValidator.get().validate(graph, schema, shapeMap).forEachReport {
                if (it.status == ShexStatus.nonconformant)
                    add(Violation(shexFile.name, it.focus.toString(), it.shapeExprLabel.toString(), it.reason.orEmpty()))
            }
        }
    }.orEmpty()

    private fun validateShacl(graph: Graph): List<Violation> = shacl?.let { shapes ->
        ShaclValidator.get().validate(shapes, graph).entries.map {
            // Property shapes are mostly blank nodes, so their path identifies them better
            val shape = it.resultPath()?.let { path -> "the shape of $path" } ?: it.source().toString()
            Violation(shaclFile.name, it.focusNode().toString(), shape, it.message())
        }
    }.orEmpty()
}
