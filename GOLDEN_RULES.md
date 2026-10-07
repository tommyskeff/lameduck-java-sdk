# Golden rules

Read this before writing any code in this repository. These rules override defaults and house
style. Rules are never renumbered — a rule that stops applying is struck, not removed.

## Code

1. **No comments, no Javadoc.** Code explains itself through naming and structure. The only
   exceptions are machine-read: annotations such as `@Override` and `@SuppressWarnings`, and
   generated sources under `build/`. The rule covers the Gradle build scripts too.
2. **No dead scaffolding.** No TODO stubs, placeholder files, commented-out code or unused
   members.
3. **No inline imports.** Everything a file uses is imported at the top: no fully qualified
   `java.util.List` in a method body. The only exception is a name conflict, where one of the two
   clashing types is written out in full.
4. **The protos are a copy.** `src/main/proto` mirrors `proto/` in
   [Lameduck Controller](https://github.com/tommyskeff/lameduck-controller), minus the
   controller-only `fleet.proto`. A proto change lands there first and is copied here unchanged.

## API

5. **Never block the caller.** Every RPC returns a `CompletableFuture`. Game servers call the SDK
   from their main thread, so nothing in the public API waits on the network.
6. **Generated types are the vocabulary.** `PodState`, `Phase` and `DrainReason` from
   `dev.tommyjs.lameduck.v1` are used directly in the API; the SDK does not wrap them in its own
   copies.
7. **Callbacks say what thread they run on by not promising one.** Listeners are invoked on gRPC
   threads; the SDK never hops to an application thread. Applications schedule onto their own.
8. **Stay compatible with Java 17 and the UKSM shading.** The library compiles with
   `release = 17`, depends only on gRPC, protobuf and what they bring, and never relies on a class
   being at its original package name, because consumers relocate `io.grpc`, `com.google.protobuf`
   and `com.google.common`.

## Java

9. **Class names are formal and specific.** A class is named for the thing it is:
   `SidecarClient`, `DrainTracker`, `StateSubscription`. If the name would fit as a section
   heading in a design document, it is too vague to be a class.
10. **A plural noun names a class only when it holds static helpers for that noun.** A type that
    models something is singular. Helper and utility classes say so in their name —
    `FutureHelper`, not `Futures`.
11. **Types and members are `public` unless there is a reason to hide them.** Package-private is a
    deliberate decision about something that must not be used outside its package.
12. **No logging.** The SDK reports failures through futures and `onError`; it never writes to a
    logger. Applications log with their platform's own logger.
13. **Match the established formatting.** Four spaces, one declaration or statement per line,
    braces on the declaration line, a blank line between members and after the opening brace of
    a type. Wrap long argument lists at a natural boundary with one continuation indent. Never
    compress a constructor, method, conditional body or `try` block onto one line.

## Verification

14. **`./gradlew build` passes before every commit.** Every public method has a test against an
    in-process gRPC server or a pure unit test.

## Commits and pull requests

15. Conventional commit title, no body, no `Co-Authored-By` trailer, no generated-with footer.
16. **Pull request descriptions say what the change does, not how.** One short sentence, then a
    bullet list of behaviour a reviewer cares about. No attribution footer.
17. **READMEs change only when asked.** Never edit or add a README as a side effect of other
    work.
