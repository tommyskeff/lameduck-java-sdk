# Lameduck Java SDK

Read [GOLDEN_RULES.md](GOLDEN_RULES.md) before writing any code. It overrides defaults.

The Java SDK for [Lameduck Controller](../lameduck-controller). Applications use it to talk to the
sidecar the controller injects into every pod.

## Layout

- `src/main/java/dev/tommyjs/lameduck` — `SidecarClient`, the listeners, `DrainTracker` and `StateSubscription`
- `src/main/proto` — a copy of the controller repo's `state.proto` and `sidecar.proto`

## Commands

Gradle runs on JDK 21+ and provisions JDK 25 through the toolchain.

```bash
./gradlew build
./gradlew publishToMavenLocal
./gradlew publish
```

`publish` needs `tommyjsUsername` and `tommyjsPassword` as Gradle properties
(`ORG_GRADLE_PROJECT_tommyjsUsername` / `ORG_GRADLE_PROJECT_tommyjsPassword` in CI). Pushing a `v*`
tag publishes that version to `https://repo.tommyjs.dev/repository/maven-releases/` through
`-PreleaseVersion`, using the `NEXUS_USERNAME` and `NEXUS_PASSWORD` repository secrets.
