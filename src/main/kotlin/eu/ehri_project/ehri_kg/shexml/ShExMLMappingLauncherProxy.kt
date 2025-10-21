package eu.ehri_project.ehri_kg.shexml

import com.herminiogarcia.shexml.MappingLauncher
import com.herminiogarcia.shexml.helper.ParallelExecutionConfigurator
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.jena.query.Dataset

class ShExMLMappingLauncherProxy {

    private val logger = KotlinLogging.logger {}

    fun convert(mappingRules: String, fileInput: String): Dataset {
        logger.info { "Transforming received data to RDF using ShExML" }
        logger.debug { "Mapping rules $mappingRules" }
        val inputStream = fileInput.byteInputStream()
        System.setIn(inputStream)
        inputStream.close()
        val result = MappingLauncher("", "", "", true, true, ParallelExecutionConfigurator.empty())
            .launchMapping(mappingRules)
        System.setIn(System.`in`)
        return result
    }

}