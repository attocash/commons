# commons-js

Deprecated aggregate JavaScript package. New applications should import the individual
`@attocash/commons-*` packages listed in the [module guide](../README.md#choose-a-module).

## Replace aggregate imports

Install only the modules the application uses, keeping Commons versions aligned:

```sh
npm install @attocash/commons-core @attocash/commons-node-remote
```

Import each API from its owning package:

```javascript
import { AttoMnemonic } from "@attocash/commons-core";
import { AttoNodeClientAsyncBuilder } from "@attocash/commons-node-remote";
```

The current generated aggregate runtime does not re-export the individual packages' APIs. Its generated
TypeScript declarations alone do not establish that an aggregate import will work. Depend directly on the
owning package rather than treating `commons-js` as an alias for all modules.

## Find the replacement

| API | Package and guide |
| --- | --- |
| Mnemonics, keys, addresses, amounts, blocks, transactions | [commons-core](../commons-core/README.md) |
| Node monitors and subscriptions | [commons-node](../commons-node/README.md) |
| HTTP node client builder | [commons-node-remote](../commons-node-remote/README.md) |
| HTTP Work Server client builder | [commons-worker-remote](../commons-worker-remote/README.md) |
| Wallet builder and operations | [commons-wallet](../commons-wallet/README.md) |
| Mock services | [commons-test](../commons-test/README.md) |

For browser and CPU workers, check the respective [worker guides](../README.md#choose-a-module) before choosing a
JavaScript API; Kotlin support does not imply a public JavaScript factory.

The [migration guide](../MIGRATION.md#javascript-packages) covers package changes. The
[JavaScript client example](../examples/js-client/README.md) shows individual imports in an application.

## Build maintainers

This module retains the aggregate artifact definition. Shared linkage for split npm packages is handled by
[commons-npm-bundle](../commons-npm-bundle/README.md), not by aggregate imports.
See [the entry resource](src/jsMain/resources/index.mjs) and [build configuration](build.gradle.kts) for its current shape.

[License](../LICENSE).
