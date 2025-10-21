package eu.ehri_project.ehri_kg.consumers

import com.herminiogarcia.shexml.streaming.StreamMappingLauncher
import com.herminiogarcia.shexml.streaming.helpers.ReactiveConverters
import eu.ehri_project.ehri_kg.helpers.SourceHelper
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.core.Observable
import org.apache.jena.query.Dataset

import java.io.File

class EHRISSEConsumer {
    val mappingRules: String = SourceHelper.readFile("src/main/resources/ehri_sse_mapping.shexml")

    fun processEvents(): Single<Observable<Dataset>> {
        return ReactiveConverters.convertToRxJava(
            StreamMappingLauncher(false, true).launchMapping(mappingRules))
    }

}