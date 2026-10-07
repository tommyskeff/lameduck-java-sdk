# Lameduck Java SDK

The Lameduck Java SDK lets an application running in a [Lameduck](https://github.com/tommyskeff/lameduck-controller) `DrainSet` report its load and react when it is told to drain.

## Install

Requires Java 17.

```kotlin
repositories {
    maven("https://repo.tommyjs.dev/repository/maven-releases/")
}

dependencies {
    implementation("dev.tommyjs:lameduck-sdk:0.1.0")
}
```

## Usage

```java
SidecarClient sidecar = SidecarClient.fromEnvironment();

sidecar.watchDrain(new DrainListener() {
    @Override
    public void onDrain(DrainReason reason, Instant deadline) {
        server.stopAcceptingGames();
        server.broadcast("This server restarts at " + deadline);
    }

    @Override
    public void onDrainCancelled() {
        server.resumeAcceptingGames();
    }
});

server.onPlayerCountChanged(count -> sidecar.setLoad(count));

server.onEmpty(() -> {
    if (server.isDraining()) {
        sidecar.markDrained();
    }
});
```
