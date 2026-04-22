#!/usr/bin/env python3
"""
Generate meter and event payloads from metrics-and-events.csv.

Usage:
  python generate_payloads.py --init    # Read metrics-and-events.csv, generate recap + payloads + curl
  python generate_payloads.py           # Read payloads-recap.csv and regenerate payloads.txt + curl-calls.sh
"""

import csv
import json
import re
import sys

# --- Plugin prefixes (longest first for correct matching) ---
# Both dash and dot variants are needed for multi-word prefixes.
PLUGIN_PREFIXES = [
    ('netty-with-http', 'Netty HTTP (query)'),
    ('netty.with-http', 'Netty HTTP (query)'),
    ('netty-with-tcp', 'Netty TCP (query)'),
    ('netty.with-tcp', 'Netty TCP (query)'),
    ('redis-lettuce', 'Redis'),
    ('redis.lettuce', 'Redis'),
    ('elasticsearch', 'Elasticsearch'),
    ('r2dbc-jasync', 'R2DBC Jasync'),
    ('r2dbc.jasync', 'R2DBC Jasync'),
    ('http-apache', 'Apache HTTP'),
    ('http.apache', 'Apache HTTP'),
    ('netty-http', 'Netty HTTP'),
    ('netty.http', 'Netty HTTP'),
    ('netty-mqtt', 'Netty MQTT'),
    ('netty.mqtt', 'Netty MQTT'),
    ('netty-tcp', 'Netty TCP'),
    ('netty.tcp', 'Netty TCP'),
    ('netty-udp', 'Netty UDP'),
    ('netty.udp', 'Netty UDP'),
    ('cassandra', 'Cassandra'),
    ('graphite', 'Graphite'),
    ('influxdb', 'InfluxDB'),
    ('rabbitmq', 'RabbitMQ'),
    ('jakarta', 'Jakarta EE Messaging'),
    ('mongodb', 'MongoDB'),
    ('kafka', 'Kafka'),
    ('jms', 'JMS'),
    ('sql', 'SQL'),
]

# (count_color, avg_duration_color, max_duration_color) per plugin
PLUGIN_COLORS = {
    'Cassandra':            ('#E57373', '#FFCDD2', '#C62828'),
    'Elasticsearch':        ('#FFB74D', '#FFE0B2', '#E65100'),
    'Graphite':             ('#AED581', '#DCEDC8', '#558B2F'),
    'InfluxDB':             ('#4DB6AC', '#B2DFDB', '#00695C'),
    'Jakarta EE Messaging': ('#7986CB', '#C5CAE9', '#283593'),
    'JMS':                  ('#4FC3F7', '#B3E5FC', '#0277BD'),
    'Kafka':                ('#9575CD', '#D1C4E9', '#4527A0'),
    'MongoDB':              ('#81C784', '#C8E6C9', '#2E7D32'),
    'Apache HTTP':           ('#F06292', '#F8BBD0', '#AD1457'),
    'Netty HTTP':           ('#F06292', '#F8BBD0', '#AD1457'),
    'Netty HTTP (query)':   ('#EC407A', '#F8BBD0', '#880E4F'),
    'Netty MQTT':           ('#BA68C8', '#E1BEE7', '#6A1B9A'),
    'Netty TCP':            ('#FF8A65', '#FFCCBC', '#BF360C'),
    'Netty TCP (query)':    ('#EF5350', '#FFCDD2', '#B71C1C'),
    'Netty UDP':            ('#A1887F', '#D7CCC8', '#4E342E'),
    'R2DBC Jasync':         ('#DCE775', '#F0F4C3', '#827717'),
    'RabbitMQ':             ('#FFD54F', '#FFF9C4', '#F57F17'),
    'Redis':                ('#4DD0E1', '#B2EBF2', '#00838F'),
    'SQL':                  ('#42A5F5', '#BBDEFB', '#1565C0'),
}

OPERATION_GERUND = {
    'poll': 'Polling',
    'save': 'Saving',
    'search': 'Searching',
    'consume': 'Consuming',
    'produce': 'Producing',
    'query': 'Querying',
    'publish': 'Producing',
    'subscribe': 'Consuming',
}

UPPERCASE_WORDS = {'http', 'https', 'tls', 'tcp', 'udp', 'mqtt', 'hscan', 'sscan', 'zscan', 'scan'}

# Normalize plural status words to singular for consistency
NORMALIZE_WORDS = {
    'failures': 'failure',
    'successes': 'success',
    'errors': 'error',
}

# Redundant gerund+participle pairs to simplify:
# "Producing produced X" → "Produced X", "Consuming consumed X" → "Consumed X", etc.
REDUNDANT_GERUND_FOLLOWING = {
    ('Producing', 'produced'): 'Produced',
    ('Producing', 'producing'): 'Producing',
    ('Consuming', 'consumed'): 'Consumed',
    ('Consuming', 'consuming'): 'Consuming',
    ('Producing', 'sending'): 'Sending',
}

# Event value types that map to the 'number' field
NUMBER_TYPES = {'Int', 'AtomicInteger', 'Long', 'Double', 'Number'}

# Variable expansion rules keyed by (variable_pattern, source_file)
VARIABLE_EXPANSIONS = {
    # Netty TCP-based connections (HTTP, TCP)
    ('${stepQualifier}', 'StepBasedTcpMonitoringCollector.kt'): ['http', 'with-http', 'tcp', 'with-tcp'],
    # Netty socket request/response handling (HTTP, TCP)
    ('${stepQualifier}', 'StepContextBasedSocketMonitoringCollector.kt'): ['http', 'with-http', 'tcp', 'with-tcp'],
    # Netty UDP
    ('${stepQualifier}', 'UdpMonitoringCollector.kt'): ['udp'],
    # Netty HTTP-specific meter prefix (meterPrefix = "netty-" + stepQualifier)
    ('${meterPrefix}', 'HttpStepContextBasedSocketMonitoringCollector.kt'): ['netty-http', 'netty-with-http'],
    # Netty HTTP-specific event prefix (eventPrefix = "netty." + stepQualifier)
    ('${eventPrefix}', 'HttpStepContextBasedSocketMonitoringCollector.kt'): ['netty.http', 'netty.with-http'],
    # Redis Lettuce step qualifiers (save, streams)
    ('${stepQualifier}', 'LettuceMonitoringCollector.kt'): ['save', 'streams'],
    # Redis scan methods (from RedisLettuceScanMethod enum)
    ('$redisMethod', 'PollResultSetBatchConverter.kt'): ['scan', 'sscan', 'hscan', 'zscan'],
    ('$redisMethod', 'PollResultSetSingleConverter.kt'): ['scan', 'sscan', 'hscan', 'zscan'],
    # Cassandra step types
    ('${stepType}', 'CassandraQueryClientImpl.kt'): ['poll', 'search'],
    # Elasticsearch monitoring types
    ('$monitoringType', 'ElasticsearchOperationsImpl.kt'): ['meters', 'events'],
}


# --- Shared helpers ---

def get_color(plugin_display, color_index=0):
    colors = PLUGIN_COLORS.get(plugin_display, ('#CCCCCC', '#EEEEEE', '#999999'))
    return colors[color_index]


def get_plugin_and_rest(name):
    """Match plugin prefix with either - or . separator."""
    for prefix, display in PLUGIN_PREFIXES:
        for sep in ('-', '.'):
            if name.startswith(prefix + sep):
                return display, name[len(prefix) + 1:]
    return name, ''


def humanize_name(rest):
    """Humanize the rest part of a metric/event name."""
    tokens = re.split(r'[.\-]', rest)
    words = []
    for token in tokens:
        if not token:
            continue
        # Normalize plural status words to singular
        token = NORMALIZE_WORDS.get(token, token)
        if token.lower() in UPPERCASE_WORDS:
            words.append(token.upper())
        elif len(words) == 0 and token in OPERATION_GERUND:
            words.append(OPERATION_GERUND[token])
        elif len(words) == 0:
            words.append(token.capitalize())
        else:
            words.append(token)

    # Clean up redundant gerund + participle pairs
    if len(words) >= 2:
        key = (words[0], words[1])
        if key in REDUNDANT_GERUND_FOLLOWING:
            words = [REDUNDANT_GERUND_FOLLOWING[key]] + words[2:]

    return ' '.join(words)


def expand_variables(name, source_file):
    """Expand variables in name using source_file context. Returns list of concrete names."""
    variables = re.findall(r'\$\{?\w+\}?', name)
    if not variables:
        return [name]

    names = [name]
    for var in variables:
        key = (var, source_file)
        if key not in VARIABLE_EXPANSIONS:
            return []  # Unknown variable combination, skip
        values = VARIABLE_EXPANSIONS[key]
        new_names = []
        for n in names:
            for v in values:
                new_names.append(n.replace(var, v, 1))
        names = new_names
    return names


def parse_value_type(value_type):
    """Parse value_type and return list of field names for data series creation."""
    if not value_type or value_type.strip().lower() in ('', 'none', 'null'):
        return []

    vt = value_type.strip()

    # Handle Array<...> types
    if vt.startswith('Array<') and vt.endswith('>'):
        inner = vt[6:-1]
        types = [t.strip() for t in inner.split(',')]
    else:
        types = [vt]

    fields = []
    seen = set()
    for t in types:
        if t == 'Duration':
            if 'duration_nano' not in seen:
                fields.append('duration_nano')/res
                seen.add('duration_nano')
        elif t in NUMBER_TYPES:
            if 'number' not in seen:
                fields.append('number')
                seen.add('number')
        # Ignore Throwable, Object, String, etc.

    return fields


# --- Data series generation ---

def generate_meter_data_series(meter_name, meter_type):
    """Generate data series entries for a meter."""
    plugin_display, rest = get_plugin_and_rest(meter_name)
    human = humanize_name(rest)
    entries = []

    if meter_type == 'counter':
        entries.append({
            'displayName': f'[{plugin_display}] {human} count',
            'dataType': 'METERS',
            'valueName': meter_name,
            'fieldName': 'count',
            'aggregationOperation': 'MAX',
            'color': get_color(plugin_display, 0),
        })
    elif meter_type in ('timer', 'summary'):
        entries.append({
            'displayName': f'[{plugin_display}] {human} count',
            'dataType': 'METERS',
            'valueName': meter_name,
            'fieldName': 'count',
            'aggregationOperation': 'MAX',
            'color': get_color(plugin_display, 0),
        })
        entries.append({
            'displayName': f'[{plugin_display}] {human} avg duration',
            'dataType': 'METERS',
            'valueName': meter_name,
            'fieldName': 'mean',
            'aggregationOperation': 'AVERAGE',
            'color': get_color(plugin_display, 1),
        })
        entries.append({
            'displayName': f'[{plugin_display}] {human} max duration',
            'dataType': 'METERS',
            'valueName': meter_name,
            'fieldName': 'max',
            'aggregationOperation': 'MAX',
            'color': get_color(plugin_display, 2),
        })
    elif meter_type == 'gauge':
        entries.append({
            'displayName': f'[{plugin_display}] {human}',
            'dataType': 'METERS',
            'valueName': meter_name,
            'fieldName': 'value',
            'aggregationOperation': 'MAX',
            'color': get_color(plugin_display, 0),
        })

    return entries


def generate_event_data_series(event_name, value_type):
    """Generate data series entries for an event."""
    plugin_display, rest = get_plugin_and_rest(event_name)
    human = humanize_name(rest)
    entries = []

    fields = parse_value_type(value_type)
    seen_fields = set()

    for field in fields:
        if field in seen_fields:
            continue
        seen_fields.add(field)

        if field == 'duration_nano':
            entries.append({
                'displayName': f'[{plugin_display}] {human} P99 duration - Events',
                'dataType': 'EVENTS',
                'valueName': event_name,
                'fieldName': 'duration_nano',
                'aggregationOperation': 'PERCENTILE_99',
                'color': get_color(plugin_display, 2),
            })
            entries.append({
                'displayName': f'[{plugin_display}] {human} avg duration - Events',
                'dataType': 'EVENTS',
                'valueName': event_name,
                'fieldName': 'duration_nano',
                'aggregationOperation': 'AVERAGE',
                'color': get_color(plugin_display, 1),
            })
        elif field == 'number':
            entries.append({
                'displayName': f'[{plugin_display}] {human} - Events',
                'dataType': 'EVENTS',
                'valueName': event_name,
                'fieldName': 'number',
                'aggregationOperation': 'SUM',
                'color': get_color(plugin_display, 0),
            })

    return entries


# --- CSV I/O ---

def read_metrics_and_events_csv(filename='metrics-and-events.csv'):
    """Read the unified metrics-and-events CSV, expand variables, generate data series entries."""
    entries = []
    seen_rows = set()
    seen_entries = set()

    with open(filename, 'r') as f:
        reader = csv.DictReader(f)
        for row in reader:
            category = row['category'].strip()
            name = row['name'].strip()
            meter_type = (row['type'].strip().lower() if row.get('type') else '')
            value_type = (row['value_type'].strip() if row.get('value_type') else '')
            source_file = (row['source_file'].strip() if row.get('source_file') else '')

            # Skip code expressions used as event names
            if '[' in name or '(' in name:
                continue

            # Deduplicate input rows
            dedup_key = (category, name, meter_type, value_type, source_file)
            if dedup_key in seen_rows:
                continue
            seen_rows.add(dedup_key)

            # Expand variables to concrete names
            concrete_names = expand_variables(name, source_file)

            for concrete_name in concrete_names:
                if category == 'meter':
                    new_entries = generate_meter_data_series(concrete_name, meter_type)
                elif category == 'event':
                    new_entries = generate_event_data_series(concrete_name, value_type)
                else:
                    continue

                # Deduplicate generated entries
                for entry in new_entries:
                    entry_key = (entry['dataType'], entry['valueName'], entry['fieldName'],
                                 entry['aggregationOperation'])
                    if entry_key not in seen_entries:
                        seen_entries.add(entry_key)
                        entries.append(entry)

    return entries


RECAP_FIELDS = ['displayName', 'dataType', 'valueName', 'fieldName', 'aggregationOperation', 'color']


def write_recap_csv(entries, filename='payloads-recap.csv'):
    with open(filename, 'w', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=RECAP_FIELDS, delimiter=';')
        writer.writeheader()
        writer.writerows(entries)
    print(f'Written {len(entries)} entries to {filename}')


def read_recap_csv(filename='payloads-recap.csv'):
    entries = []
    with open(filename, 'r') as f:
        reader = csv.DictReader(f, delimiter=';')
        for row in reader:
            entries.append(row)
    return entries


def generate_payloads(entries):
    payloads = []
    for entry in entries:
        payload = {
            "displayName": entry['displayName'],
            "dataType": entry.get('dataType', 'METERS'),
            "sharingMode": "WRITE",
            "valueName": entry['valueName'],
            "fieldName": entry['fieldName'],
            "filters": [],
            "color": entry['color'],
            "colorOpacity": 100,
            "timeframeUnit": "PT5S",
            "aggregationOperation": entry['aggregationOperation'],
        }
        payloads.append(json.dumps(payload, ensure_ascii=False))
    return payloads


def write_payloads(payloads, filename='payloads.txt'):
    with open(filename, 'w') as f:
        for p in payloads:
            f.write(p + '\n')
    print(f'Written {len(payloads)} payloads to {filename}')


def write_curl_calls(payloads, filename='curl-calls.sh'):
    with open(filename, 'w') as f:
        f.write('#!/bin/bash\n\n')
        for p in payloads:
            f.write(f"""curl 'https://cloud.test.qalipsis.io/api/data-series' \\
  -X POST \\
  -H 'Accept: application/json' \\
  -H 'Accept-Encoding: gzip, deflate, br, zstd' \\
  -H 'authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6IjkyNzhUOWV6Yy1EcWlXeWp3bWVHRSJ9.eyJpc3MiOiJodHRwczovL3FhbGlwc2lzLWRldi5ldS5hdXRoMC5jb20vIiwic3ViIjoiYXV0aDB8NjM1YjllMjE3NmQ2ZTdkOGQ4NTYzN2Q1IiwiYXVkIjpbImh0dHBzOi8vcWFsaXBzaXMtZGV2LmV1LmF1dGgwLmNvbS9hcGkvdjIvIiwiaHR0cHM6Ly9xYWxpcHNpcy1kZXYuZXUuYXV0aDAuY29tL3VzZXJpbmZvIl0sImlhdCI6MTc3MzI0MTM1MSwiZXhwIjoxNzczMzI3NzUxLCJzY29wZSI6Im9wZW5pZCBwcm9maWxlIGVtYWlsIiwiYXpwIjoiejhlalJ2cmZsUWZRekprcmpDUjBQTjF6d0g3OXpVMW4ifQ.IWvDzF06Ga4VC7PO4K1dbZojmvfMxXOW-aTkU-7aCPrc89vlG4puSGbVUFsBI9n6hRcuMZqnhrilvpBduszdLJMdwNoN-0XLUalMmoh4NSO8x5hkhsTxzpOemxhWMpVFPA0ekK779xTX1kv5E-1gGGU0YErwn9_sGtdbCIfZqyPQGjSZo-IuT35Rl2_xtTXCAc6iRhjOElSi8yHFuwVYvyj6EC_5r-hexVFHeq2ew_Rq5g22dOSB8yt0Ef0sWR0zF5WSgONFES_jgoRDNvVArm6v8f78OuNpZEFtgbZhjfv5StIehEek91tia52_B5291Ci-4metX5uq8X6heKP4Tw' \\
  -H 'content-type: application/json' \\
  -H 'x-tenant: _qalipsis_ten_' \\
  --data-raw '{p}'

""")
    print(f'Written {len(payloads)} curl calls to {filename}')


def main():
    if '--init' in sys.argv:
        print('Reading metrics-and-events.csv...')
        entries = read_metrics_and_events_csv()
        print(f'  {len(entries)} data series entries')
        write_recap_csv(entries)
        payloads = generate_payloads(entries)
        write_payloads(payloads)
        write_curl_calls(payloads)
    else:
        print('Reading payloads-recap.csv...')
        entries = read_recap_csv()
        payloads = generate_payloads(entries)
        write_payloads(payloads)
        write_curl_calls(payloads)


if __name__ == '__main__':
    main()
