package io.qalipsis.plugins.jackson.csv

import io.qalipsis.api.annotations.Spec
import javax.validation.constraints.NotBlank

/**
 * Configuration to parse data from a CSV file.
 *
 * @author Eric Jessé
 */
@Spec
data class CsvParsingConfiguration internal constructor(
        internal var lineSeparator: @NotBlank String = System.lineSeparator(),
        internal var columnSeparator: Char = ',',
        internal var escapeChar: Char = '\\',
        internal var quoteChar: Char = '"',
        internal var allowComments: Boolean = false
)
