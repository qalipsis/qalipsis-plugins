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

package io.qalipsis.plugins.r2dbc.jasync.poll

import io.qalipsis.api.steps.datasource.DatasourceRecord

/**
 * Qalispsis representation of a Jasync Poll result.
 *
 * @property records list of the Datasource records.
 * @property meters meters form the poll operation.
 *
 * @author Alex Averyanov
 */

class JasyncPollResults <O> (
    val records: List<DatasourceRecord<O>>,
    val meters: JasyncPollMeters
) : Iterable<DatasourceRecord<O>> {

    override fun iterator(): Iterator<DatasourceRecord<O>> {
        return records.iterator()
    }
}