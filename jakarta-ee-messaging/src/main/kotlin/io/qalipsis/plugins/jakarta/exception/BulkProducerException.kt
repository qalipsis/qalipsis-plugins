package io.qalipsis.plugins.jakarta.exception

class BulkProducerException(
    private val errors: Collection<Throwable>
) : RuntimeException() {

    override val message: String
        get() = errors.joinToString(
            prefix = "There were some errors when producing messages:\n",
            separator = "\n"
        ) { it.message!! }
}