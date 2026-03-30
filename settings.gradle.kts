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

pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "qalipsis-plugins"

include("qalipsis-plugin-cassandra")
project(":qalipsis-plugin-cassandra").projectDir = file("cassandra")

include("qalipsis-plugin-elasticsearch")
project(":qalipsis-plugin-elasticsearch").projectDir = file("elasticsearch")

include("qalipsis-plugin-graphite")
project(":qalipsis-plugin-graphite").projectDir = file("graphite")

include("qalipsis-plugin-http")
project(":qalipsis-plugin-http").projectDir = file("http")

include("qalipsis-plugin-influxdb")
project(":qalipsis-plugin-influxdb").projectDir = file("influxdb")

include("qalipsis-plugin-jackson")
project(":qalipsis-plugin-jackson").projectDir = file("jackson")

include("qalipsis-plugin-jakarta-ee-messaging")
project(":qalipsis-plugin-jakarta-ee-messaging").projectDir = file("jakarta-ee-messaging")

include("qalipsis-plugin-jms")
project(":qalipsis-plugin-jms").projectDir = file("jms")

include("qalipsis-plugin-kafka")
project(":qalipsis-plugin-kafka").projectDir = file("kafka")

include("qalipsis-plugin-mail")
project(":qalipsis-plugin-mail").projectDir = file("mail")

include("qalipsis-plugin-mongodb")
project(":qalipsis-plugin-mongodb").projectDir = file("mongodb")

include("qalipsis-plugin-netty")
project(":qalipsis-plugin-netty").projectDir = file("netty")

include("qalipsis-plugin-r2dbc-jasync")
project(":qalipsis-plugin-r2dbc-jasync").projectDir = file("r2dbc-jasync")

include("qalipsis-plugin-rabbitmq")
project(":qalipsis-plugin-rabbitmq").projectDir = file("rabbitmq")

include("qalipsis-plugin-redis-lettuce")
project(":qalipsis-plugin-redis-lettuce").projectDir = file("redis-lettuce")

include("qalipsis-plugin-slack")
project(":qalipsis-plugin-slack").projectDir = file("slack")

include("qalipsis-plugin-sql")
project(":qalipsis-plugin-sql").projectDir = file("sql")

include("qalipsis-plugin-timescaledb")
project(":qalipsis-plugin-timescaledb").projectDir = file("timescaledb")

include("qalipsis-platform")
project(":qalipsis-platform").projectDir = file("user-bom")
