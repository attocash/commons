# SnaKt verification

The Base32 proof checks that the decoder allocates one byte for every eight complete bits, including the 38 bytes in a 61-symbol address payload. Its general bound covers symbol counts whose multiplication by five fits in a Kotlin `Int`.

The proof compiles the production source with SnaKt’s Kotlin 2.3.0 compiler. The library build uses its existing Kotlin toolchain. A failed proof is a compiler warning, which this build treats as an error.

The pull-request and main pipeline test jobs run SnaKt after the regular Gradle tests.

## Run locally

Use JDK 21 and set `Z3_EXE` to the absolute path of the [Z3 4.8.7 executable](https://github.com/Z3Prover/z3/releases/tag/z3-4.8.7). From the repository root, build the pinned verifier once.

```sh
mkdir -p build/snakt
git clone https://github.com/JetBrains/SnaKt.git build/snakt/SnaKt
git -C build/snakt/SnaKt checkout --detach e8d86408298192f121ba835a4f5d87ecf388356d
build/snakt/SnaKt/gradlew -Dmaven.repo.local="$PWD/build/snakt/maven" -p build/snakt/SnaKt publishToMavenLocal
```

Run the proof after changing the code or its contract.

```sh
build/snakt/SnaKt/gradlew -Dmaven.repo.local="$PWD/build/snakt/maven" -p verification/snakt compileKotlin --rerun-tasks
```
