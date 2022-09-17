/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
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