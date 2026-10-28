package eu.ehri_project.ehri_kg.shexml

import com.herminiogarcia.shexml.MappingLauncher
import com.herminiogarcia.shexml.helper.ParallelExecutionConfigurator
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.jena.query.Dataset
import java.nio.file.Path

class ShExMLMappingLauncherProxy {

    private val logger = KotlinLogging.logger {}

    fun convert(mappingRules: String, fileInput: String): Dataset {
        logger.info { "Transforming received data to RDF using ShExML" }
        logger.trace { "Mapping rules $mappingRules" }
        // ShExML reads <stdin> sources from System.in, so take the lock shexml-streaming takes for the same, in case
        // it maps an SSE message meanwhile
        synchronized(MappingLauncher::class.java) {
            val original = System.`in`
            System.setIn(fileInput.byteInputStream())
            try {
                return MappingLauncher("", "", "", true, true, ParallelExecutionConfigurator.empty(), Path.of(""))
                    .launchMapping(mappingRules)
            } finally {
                System.setIn(original)
            }
        }
    }

}