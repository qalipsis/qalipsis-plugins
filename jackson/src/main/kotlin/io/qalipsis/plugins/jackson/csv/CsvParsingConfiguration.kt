package io.qalipsis.plugins.jackson.csv

import io.qalipsis.api.annotations.Spec
import javax.validation.constraints.NotEmpty

/**
 * Configuration to parse data from a CSV file.
 *
 * @author Eric Jessé
 */
@Spec
internal data class CsvParsingConfiguration internal constructor(
    var lineSeparator: @NotEmpty String = System.lineSeparator(),
    var columnSeparator: Char = ',',
    var escapeChar: Char = '\\',
    var quoteChar: Char = '"',
    var allowComments: Boolean = false
)
