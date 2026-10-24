# commons-npm-bundle

Internal Kotlin/JS linkage project used to build the split `@attocash/commons-*` npm packages.
Application code should depend on the [public modules](../README.md#choose-a-module).

## Purpose

Kotlin/JS generates internal symbol names that must agree between packages. Linking every package independently
can produce incompatible names even when the packages compile and their npm dependency versions match.
This project links the publishable modules together once, producing the canonical JavaScript files that are then
split into individual npm packages.

It has no public API, publication plugin, or runtime dependency for applications. Its only source is a
[compilation marker](src/jsMain/kotlin/cash/atto/commons/npm/NpmBundle.kt).

## Packaging flow

1. Each public module assembles its JavaScript package.
2. `:commons-npm-bundle:compileProductionLibraryKotlinJs` links the shared dependency graph.
3. The [externalization task](../buildSrc/src/main/kotlin/AttoNpmPackageExternalization.kt) copies matching canonical
   `.mjs` files and source maps into each package's staging directory.
4. It rewrites cross-module imports to `@attocash/commons-*` package imports and adds matching npm dependencies.
5. Shared Kotlin runtime files are hosted by `@attocash/commons-core`; duplicate internal files are removed.
6. It checks that relative internal Commons imports have been eliminated before packing.

The graph is declared in [build.gradle.kts](build.gradle.kts). Update it whenever a module is added to or removed
from npm publication.

## Verify a packaging change

From the repository root, pack a unique local version:

```sh
./gradlew packJsPackage -Pversion=0.0.0-local.1 -Psigning.skip=true
```

Inspect the embedded `package/package.json` in each relevant tarball under `<module>/build/packages/`.
Confirm the package name, version, and internal dependencies, then install the local tarballs together in a clean
Node project. Import the public APIs and exercise their Promise methods; generated declarations and Gradle tests
alone do not verify the npm consumer boundary.

Repeat with a second unique version after changing the build to catch stale package metadata or reused artifacts.
The [JavaScript example](../examples/js-client/README.md) explains how to install local packages.

Keep the single linkage step unless its replacement has been verified across the same clean-consumer boundaries.
It must remain separate from the deprecated [aggregate package](../commons-js/README.md).

[All modules](../README.md) · [License](../LICENSE).
