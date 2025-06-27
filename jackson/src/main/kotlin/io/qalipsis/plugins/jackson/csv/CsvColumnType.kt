/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.plugins.jackson.csv

import io.qalipsis.api.annotations.Spec

/**
 * @author Eric Jessé
 */
@Spec
internal enum class CsvColumnType(val converter: String?.() -> Any? = { this }) {
    NULLABLE_STRING({ if (this.isNullOrEmpty()) null else this }),
    STRING({ (if (this.isNullOrEmpty()) null else this) ?: error("The value cannot be empty") }),
    NULLABLE_INTEGER({ this?.toIntOrNull() }),
    INTEGER({ this?.toIntOrNull() ?: error("The value cannot be converted to a non-null integer") }),
    NULLABLE_BIG_INTEGER({ this?.toBigIntegerOrNull() }),
    BIG_INTEGER({ this?.toBigIntegerOrNull() ?: error("The value cannot be converted to a non-null big integer") }),
    NULLABLE_DOUBLE({ this?.toDoubleOrNull() }),
    DOUBLE({ this?.toDoubleOrNull() ?: error("The value cannot be converted to a non-null double") }),
    NULLABLE_BIG_DECIMAL({ this?.toBigDecimalOrNull() }),
    BIG_DECIMAL({ this?.toBigDecimalOrNull() ?: error("The value cannot be converted to a non-null big integer") }),
    NULLABLE_LONG({ this?.toLongOrNull() }),
    LONG({ this?.toLongOrNull() ?: error("The value cannot be converted to a non-null long") }),
    NULLABLE_FLOAT({ this?.toFloatOrNull() }),
    FLOAT({ this?.toFloatOrNull() ?: error("The value cannot be converted to a non-null float") }),
    NULLABLE_BOOLEAN({ if (this.isNullOrBlank()) null else this.toBoolean() }),
    BOOLEAN({
        (if (this.isNullOrBlank()) null else this.toBoolean()) ?: error(
                "The value cannot be converted to a non-null boolean")
    })
}