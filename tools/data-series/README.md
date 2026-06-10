# generate_payloads.py

Generates QALIPSIS Cloud data series payloads from `metrics-and-events.csv` files produced by the plugin build.

## Prerequisites

Build the plugins first so the CSV files exist under `<module>/build/docs/analysis/`:

```bash
# From qalipsis-plugins root
./gradlew build

# Or for a single plugin
./gradlew :qalipsis-plugin-kafka:build
```

## Usage

Run from any directory — the script locates plugin modules automatically relative to its own path.

### Full regeneration from build artifacts

```bash
python generate_payloads.py --init
```

Scans all plugin modules, reads their `build/docs/analysis/metrics-and-events.csv`, and writes:

- `payloads-recap.csv` — intermediate flat CSV of all data series
- `payloads.txt` — one JSON payload per line
- `curl-calls.sh` — ready-to-run curl commands for the QALIPSIS Cloud API

### Restrict to specific plugins

```bash
python generate_payloads.py --init --plugins kafka,netty,cassandra
```

Only processes the listed plugin module directories. Module names match the directory names under `qalipsis-plugins/` (
e.g. `redis-lettuce`, `jakarta-ee-messaging`).

### Omit event data series

```bash
python generate_payloads.py --init --skip-events
python generate_payloads.py --skip-events
```

Excludes entries with `dataType=EVENTS` from the output. Works with or without `--init`.

### Regenerate from existing recap (no build required)

```bash
python generate_payloads.py
```

Reads `payloads-recap.csv` from the current directory and regenerates `payloads.txt` and `curl-calls.sh`. Useful after
manually editing the recap CSV (e.g. adjusting colors or display names) without re-running the full build.

## Output files

| File                 | Description                                                                                                   |
|----------------------|---------------------------------------------------------------------------------------------------------------|
| `payloads-recap.csv` | Semicolon-delimited; one row per data series. Edit here to tweak display names or colors before regenerating. |
| `payloads.txt`       | One JSON object per line, ready to POST to the data series API.                                               |
| `curl-calls.sh`      | Shell script with one `curl` call per payload. Replace `<-my-token->` and `_qalipsis_ten_` before running.    |
