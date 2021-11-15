package io.qalipsis.plugins.jackson.xml

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.plugins.jackson.AbstractJacksonStepSpecification
import io.qalipsis.plugins.jackson.JacksonScenarioSpecification
import io.qalipsis.plugins.jackson.JacksonStepSpecification
import kotlin.reflect.KClass

/**
 * Specification for a [io.qalipsis.api.steps.datasource.IterativeDatasourceStep] for XML files.
 *
 * @property targetClass class to which the output lines should be mapped.
 *
 * @author Maxim Golokhov
 */
@Spec
data class XmlReaderStepSpecification<O : Any>(internal var targetClass: KClass<O>) :
    AbstractJacksonStepSpecification<O, XmlReaderStepSpecification<O>>() {

    internal var mapperConfiguration: (XmlMapper) -> Unit = {}

    /**
     * Tweaks the configuration of the [XmlMapper] used underneath.
     * Modules for Kotlin, Java time and JDK8 are registered before this closure is applied.
     *
     * @param mapperConfiguration lambda to configure [XmlMapper]
     */
    fun mapper(mapperConfiguration: (XmlMapper) -> Unit): XmlReaderStepSpecification<O> {
        this.mapperConfiguration = mapperConfiguration
        return this
    }
}

/**
 * Reads a XML resource (file, classpath resource, URL) and returns each item as an instance of mappingClass.
 *
 * @author Maxim Golokhov
 */
fun <OUTPUT : Any> JacksonScenarioSpecification.xmlToObject(
        mappingClass: KClass<OUTPUT>,
        configurationBlock: XmlReaderStepSpecification<OUTPUT>.() -> Unit
): XmlReaderStepSpecification<OUTPUT> {
    val step = XmlReaderStepSpecification(mappingClass)
    step.configurationBlock()
    (this as StepSpecificationRegistry).add(step)
    return step
}

/**
 * Reads a XML resource (file, classpath resource, URL) and returns each item as an instance of mappingClass.
 *
 * @author Maxim Golokhov
 */
fun <OUTPUT : Any> JacksonStepSpecification<*, *, *>.xmlToObject(
        mappingClass: KClass<OUTPUT>,
        configurationBlock: XmlReaderStepSpecification<OUTPUT>.() -> Unit
): XmlReaderStepSpecification<OUTPUT> {
    val step = XmlReaderStepSpecification(mappingClass)
    step.configurationBlock()
    this.add(step)
    return step
}

