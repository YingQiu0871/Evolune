# Wear v1.1 Identity Migration

## Why a one-time reinstall is required

Evolune v1.0 published the Wear APK as:

```text
io.github.yingqiu0871.evolune.wear
```

Evolune v1.1 aligns Phone and Wear Data Layer identity. The new Wear APK is:

```text
io.github.yingqiu0871.evolune
```

Android cannot update one package name in place to another package name. The old and new Wear APKs are separate installed applications even though Evolune retains the same persistent Release certificate.

This guide describes the v1.1.0 main-line Wear identity. `v1.1.0` is the current published
stable release; `v1.0.0` remains the previous sealed release.

## Migration steps

1. Keep the Evolune Phone app installed. Do not clear its data.
2. Remove the legacy v1.0 Wear app `io.github.yingqiu0871.evolune.wear` from the watch.
3. Install the v1.1.0 Wear app `io.github.yingqiu0871.evolune`.
4. Re-add or reopen the Evolune Tile if the watch does not retain the Tile placement.
5. Keep the watch connected to the Phone while the new Wear app requests and rebuilds its dashboard state.

Phone Room v3 remains the source of truth. The legacy Wear package contains only rebuildable plan/concentration cache and short-lived request/action feedback preferences, so no Wear business-data migration or migration shim is required.

## Verification

After installation, the Tile must show a truthful transport state while it connects. It reaches the normal plan view when a fresh enabled-plan snapshot arrives, or the no-enabled-plan message only after Phone sends a valid explicit empty snapshot.

For paired Debug testing, build Phone and Wear in the same local signing environment. Both Debug APKs must resolve to `io.github.yingqiu0871.evolune.debug` and have identical Debug certificate fingerprints. Release verification must use the persistent Evolune Release certificate; Release credentials do not belong in source control or Debug CI.
