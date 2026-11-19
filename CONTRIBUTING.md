# Contributing to the QALIPSIS plugins

Thank you for considering a contribution to the QALIPSIS plugins. This repository holds the steps that connect
QALIPSIS scenarios to protocols and data stores — HTTP, Kafka, Cassandra, MongoDB, and more.

---

## Getting started

```bash
./gradlew build                                   # all the plugins
./gradlew :qalipsis-plugin-kafka:test             # unit tests of one plugin
./gradlew :qalipsis-plugin-kafka:integrationTest  # integration tests of one plugin (Testcontainers)
./gradlew :qalipsis-plugin-kafka:scenariosTest    # end-to-end scenarios of one plugin
./gradlew :qalipsis-plugin-kafka:checkLicense     # licenses of the dependencies of one plugin
```

To create a plugin, follow [NEW_PLUGIN_GUIDE.md](./NEW_PLUGIN_GUIDE.md).

A plugin depends on `qalipsis-api-dsl` and `qalipsis-api-common` only. The runtime modules — `qalipsis-head`,
`qalipsis-factory`, `qalipsis-runtime` — may appear in `testRuntimeOnly` to execute the tests, never in a
configuration that ships with the plugin. This rule is architectural, and it is also what keeps the plugins
distributable under the Apache License 2.0.

---

## Pull requests

- One change per pull request, with the tests covering it.
- Run `build`, `integrationTest` and `checkLicense` for every plugin you touched before requesting a review.
- When a new dependency requires a license not yet allowed in
  [`build-config/allowed-licenses.json`](./build-config/allowed-licenses.json), explain why in the pull request;
  the maintainers decide whether it is acceptable.

---

## Licensing

This repository is distributed under the [Apache License 2.0](./LICENSE). As stated in its section 5, every
contribution you submit is licensed under the same terms. The project does not accept contributions under any
other terms.

The Apache License covers the content of this repository only. Running a scenario relies on the QALIPSIS
runtime — `qalipsis-head`, `qalipsis-factory`, `qalipsis-runtime`, `qalipsis-shared`, `qalipsis-cluster` and
`qalipsis-webapp` — which is dual-licensed under the GNU AGPL 3.0 **or** a
commercial license, at the licensee's choice: see
[LICENSE-COMMERCIAL.md](https://github.com/qalipsis/qalipsis-oss/blob/main/LICENSE-COMMERCIAL.md) in
`qalipsis-oss`. The QALIPSIS API modules this repository compiles against are under the Apache License 2.0.

---

## Contributor License Agreement

Before your first pull request can be merged, you must sign
the [QALIPSIS Individual Contributor License Agreement](./cla/individual-cla.md). The CLA
bot posts the signing link as a comment on the pull request; signing takes a minute and is remembered for all your
later contributions to the QALIPSIS repositories. Contributing on behalf of an employer requires
the [Corporate Contributor License Agreement](./cla/corporate-cla.md), signed by someone entitled to bind the company.

The CLA does **not** transfer your copyright. You keep it, and grant AERIS IT Solutions GmbH a non-exclusive right to
distribute your contribution — under the Apache License 2.0, and in the commercial and SaaS editions of QALIPSIS.

---

## What you may contribute

- **Your own work only.** Submit only content you wrote, or have the right to submit. If your employer holds rights
  on your work, the corporate CLA must be signed first. You remain responsible for everything you submit, including
  content produced with code generators or AI assistants: review it as if you had written it yourself, and make sure
  it does not reproduce third-party code.
- **No code from the QALIPSIS runtime modules.** Do not copy or adapt code from `qalipsis-head`, `qalipsis-factory`,
  `qalipsis-runtime`, `qalipsis-shared`, `qalipsis-cluster` or `qalipsis-webapp`: these modules
  are dual-licensed (AGPL 3.0 or commercial), and their code cannot enter an Apache-licensed repository. Depend on
  the API modules instead; if something you need is missing there, open an issue on
  [`qalipsis-oss`](https://github.com/qalipsis/qalipsis-oss/issues).
- **Third-party code** only under a license compatible with the Apache License 2.0 (Apache, MIT, BSD, …), with its
  original copyright notice kept, and mentioned explicitly in the pull request description. Never copy code
  licensed under the GPL, LGPL or AGPL, nor code published without a license.
- **Dependencies** must not bring a strong copyleft license — GPL without Classpath Exception, AGPL — into the
  dependency graph. `./gradlew checkLicense` verifies them against\n  [
  `build-config/allowed-licenses.json`](./build-config/allowed-licenses.json).
- **No secrets nor personal data**: no credentials, tokens, private hostnames or real user data, even in tests or
  configuration samples. Use placeholders.
- **Trademarks**: as stated in section 6 of the Apache License, the QALIPSIS name and logo are not licensed. Use them
  only to describe the origin of the code, not to name or promote a derived product.

---

## License headers

Every new source file starts with the Apache License header: copy it from a neighbouring file and set the current
year in the copyright line. Do not alter the header of an existing file, except to correct it.
