/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.qalipsis.plugins.sql.save

/**
 * Wrapper for the result of save operation into a SQL database.
 *
 * @property input are the records that came from the previous step.
 * @property resultIds are the ids created by the database.
 * @property sqlSaveStepMeters the metrics for the SQL save operation
 *
 * @author Carlos Vieira
 */
data class SqlSaveResult<I> (
    val input: I,
    val resultIds: List<Long> = emptyList(),
    val sqlSaveStepMeters: SqlQueryMeters
)
