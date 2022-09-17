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

package io.qalipsis.plugins.r2dbc.jasync.search

/**
 * Qalispsis representation of a Jasync Batch Search result.
 * @property input are the records that can from the previous step.
 * @property records list of the Datasource records.
 * @property meters meters form the search operation.
 *
 * @author Alex Averyanov
 */

class JasyncSearchBatchResults <I, O> (
    val input: I,
    val records: List<JasyncSearchRecord<O>>,
    val meters: JasyncSearchMeters
) : Iterable<JasyncSearchRecord<O>> {

    override fun iterator(): Iterator<JasyncSearchRecord<O>> {
        return records.iterator()
    }
}