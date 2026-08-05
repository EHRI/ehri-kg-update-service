package eu.ehri_project.ehri_kg.consumers

import com.herminiogarcia.shexml.streaming.StreamMappingLauncher
import com.herminiogarcia.shexml.streaming.helpers.ReactiveConverters
import eu.ehri_project.ehri_kg.helpers.SourceHelper
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import org.apache.jena.query.Dataset


class EHRISSEConsumer(mappingRulesPath: String, val lastEventId: String? = null) {
    val mappingRules: String = SourceHelper.readFile(mappingRulesPath)

    fun processEvents(): Single<Flowable<Dataset>> {
        val finalMappingRules = lastEventId?.let {
            mappingRules.lines().joinToString("\n") {
                if (it.startsWith("STREAM"))
                    it.replaceFirst(">", "?Last-Event-Id=$lastEventId>")
                else it
            }
        } ?: mappingRules
        return ReactiveConverters.convertToRxJava(
            StreamMappingLauncher(false, true).launchMapping(finalMappingRules)
        ).map { it.toFlowable(BackpressureStrategy.BUFFER) }
    }

}