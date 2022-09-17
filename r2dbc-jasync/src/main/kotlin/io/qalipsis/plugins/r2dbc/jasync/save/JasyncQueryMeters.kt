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

package io.qalipsis.plugins.r2dbc.jasync.save

import java.time.Duration

/**
 * Records the metrics for the Jasync poll operation.
 *
 * @property totalDocuments total number of documents
 * @property timeToResponse the time that takes on a successfully being save a records to database.
 * @property successSavedDocuments number of documents successfully save to the database.
 *
 * @author Alex Averyanov
 */
data class JasyncQueryMeters(
    val totalDocuments: Int = 0,
    val timeToResponse: Duration,
    val successSavedDocuments: Int = 0
)