package eu.ehri_project.ehri_kg.consumers

import com.herminiogarcia.shexml.streaming.StreamMappingLauncher
import com.herminiogarcia.shexml.streaming.helpers.ReactiveConverters
import com.herminiogarcia.shexml.streaming.model.KafkaOptions
import eu.ehri_project.ehri_kg.helpers.SourceHelper
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import org.apache.jena.query.Dataset
import scala.Option
import java.net.URLEncoder
import java.time.Duration


class EHRISSEConsumer(
    mappingRulesPath: String,
    val lastEventId: String? = null,
    val sseEndpoint: String? = null
) {
    val mappingRules: String = SourceHelper.readFile(mappingRulesPath)

    fun processEvents(): Single<Flowable<Dataset>> {
        return ReactiveConverters.convertToRxJava(
            StreamMappingLauncher(
                false,
                true,
                KafkaOptions(Option.empty<String>(), Option.empty<Duration>(), false)
            ).launchMapping(buildMappingRules())
        ).map { it.toFlowable(BackpressureStrategy.BUFFER) }
    }

    fun buildMappingRules(): String {
        if (sseEndpoint == null && lastEventId == null) return mappingRules
        return mappingRules.lines().joinToString("\n") {
            if (it.startsWith("STREAM")) rewriteStreamLine(it) else it
        }
    }

    fun streamUrl(): String {
        val line = buildMappingRules().lines().firstOrNull { it.startsWith("STREAM") }
            ?: throw IllegalArgumentException("No STREAM declaration found in the SSE mapping rules")
        return line.substringAfter('<').substringBefore('>')
    }

    private fun rewriteStreamLine(line: String): String {
        val start = line.indexOf('<')
        val end = line.indexOf('>', start)
        val url = sseEndpoint ?: line.substring(start + 1, end)
        val finalUrl = lastEventId?.let {
            val separator = if ('?' in url) '&' else '?'
            "$url${separator}Last-Event-Id=${URLEncoder.encode(it, Charsets.UTF_8)}"
        } ?: url
        return line.substring(0, start + 1) + finalUrl + line.substring(end)
    }

}