# commons-npm-bundle

Internal Kotlin/JS linkage project for the split `@attocash/commons-*` npm packages.

This project is not a public library. It exists only so the npm publication build can compile all published Commons JS modules in one Kotlin/JS link context before the modules are split into separate npm packages.

## Why this exists

The Commons npm artifacts are published as multiple packages:

- `@attocash/commons-core`
- `@attocash/commons-node`
- `@attocash/commons-node-remote`
- `@attocash/commons-test`
- `@attocash/commons-wallet`
- `@attocash/commons-worker`
- `@attocash/commons-worker-remote`
- `@attocash/commons-worker-web`

Those packages depend on each other at the npm level. For example, `@attocash/commons-node-remote` should depend on and import `@attocash/commons-core` instead of embedding another local copy of core.

The package metadata alone is not enough for Kotlin/JS. Kotlin/JS generated JavaScript contains mangled export names and imports that must agree across modules. If each split package is linked independently, two packages can compile successfully and still disagree about the generated names expected from the other package. The result is a runtime import failure such as:

```text
The requested module '@attocash/commons-core/commons-commons-core.mjs' does not provide an export named '...'
```

This project prevents that by giving all npm-published Commons JS modules one canonical Kotlin/JS linkage output.

## How it works

`commons-npm-bundle` declares API dependencies on every Commons module that is published to npm. It then runs the Kotlin/JS production library compiler once over that combined dependency graph.

The generated output is used by `ExternalizeCommonsNpmPackageDependencies` in `buildSrc`:

1. Each publishable module first assembles its normal JS npm package.
2. `commons-npm-bundle:compileProductionLibraryKotlinJs` produces the canonical linked module files under the root build directory.
3. The externalization task copies matching canonical `.mjs` and `.mjs.map` files back into each module's package staging directory.
4. Imports between Commons modules are rewritten from local relative files to package imports such as `@attocash/commons-core/commons-commons-core.mjs`.
5. Shared Kotlin runtime files are hosted by `@attocash/commons-core`; non-core packages import those runtime files from core and declare an exact npm dependency on core.
6. Internal module files that should now come from another npm package are removed from the staged package.
7. The task validates that no relative internal Commons imports remain before packing or publishing.

The important part is step 2: all modules agree on the same generated Kotlin/JS names before they are split into npm packages.

## What this project must not do

- It must not apply `org.jetbrains.kotlin.npm-publish`.
- It must not be added to public installation instructions.
- It must not contain product API.
- It must not become a runtime dependency for Gradle, JVM, Wasm, or user code.

The only Kotlin source is a small marker so the Kotlin/JS target has a source file to compile.

## Maintenance rules

When adding a new Commons module that is published to npm:

1. Add the module to `settings.gradle.kts` as usual.
2. Apply the npm publish plugin in that module.
3. Add it as an `api(project(":..."))` dependency here.
4. Make sure `ExternalizeCommonsNpmPackageDependencies` still recognizes any new shared runtime files if Kotlin starts generating additional runtime modules.
5. Run `./gradlew packJsPackage -Pversion=<version>` and test the local tarballs from `examples/js-client`.

When removing or renaming a published npm module, update this project's dependency list at the same time.

## When this can be removed

This module can be removed only if the split npm packages no longer need a shared Kotlin/JS linkage context. That would require one of these to be true:

- Kotlin/JS provides stable cross-package generated names for independently linked modules in this publication shape.
- The npm packages are no longer split.
- The build switches to another explicit linker step that provides the same canonical output without a Gradle project.

Until then, removing this project is likely to bring back runtime import/export mismatches even when `package.json` dependencies look correct.
