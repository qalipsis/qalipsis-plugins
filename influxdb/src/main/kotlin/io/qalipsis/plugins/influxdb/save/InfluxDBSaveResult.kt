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

package io.qalipsis.plugins.influxdb.save

import com.influxdb.client.write.Point

/**
 * Wrapper for the result of save points procedure in InfluxDB.
 *
 * @property input the data to save in InfluxDB
 * @property points the data formatted to be able to save in InfluxDB
 * @property meters meters of the save step
 *
 * @author Palina Bril
 */
class InfluxDBSaveResult<I>(
    val input: I,
    val points: List<Point>,
    val meters: InfluxDbSaveQueryMeters
)
