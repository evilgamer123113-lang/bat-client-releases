# Protected mod build

Install build-only web tools with `npm ci --ignore-scripts`, then run
`gradlew.bat build`. Distribute only the remapped `build/libs/bat-client-mod-*.jar`.
Do not distribute `build/devlibs`, `build/protected`, source files, or
`build/private-obfuscation/mapping.txt`. Keep the mapping privately for crash diagnosis.

`gradlew.bat runClient -PprotectedClient` runs the obfuscated named JAR in the
development environment. Set `JAVA_TOOL_OPTIONS=-Dbatclient.test.menuNavigation=true`
for the opt-in native GL-state and multiplayer navigation regression probe.
Successful startup logs `MENU_NAVIGATION_AND_GL_STATE_OK reopen=5`; this probe
does not run in normal player releases and does not save its temporary server entry.

UI source remains editable in the repository. The build minifies HTML/CSS/JS
without source maps, then encrypts every bundled UI asset with AES-256-GCM.
Each packaging run uses a random 256-bit key and unique per-asset 96-bit nonces.
The asset name is authenticated as additional data, so swapping/corrupting payloads fails closed.
Ultralight requests assets through an in-memory, JAR-backed
filesystem; `.batclient-ui` is only a virtual URL base and is never created.
Existing `batclient-web` folders from older versions are not read or recreated;
users may delete those old caches themselves.

ProGuard renames implementation classes and members and strips debug metadata.
Fabric entry points, mixins, native callbacks, serialized fields, and enum values
have explicit keep rules. Development runs use readable Java classes for debugging.
Source-JAR generation is disabled. Fabric metadata and the packaged LICENSE apply
the Bat Client proprietary license to original material only.

The generated UI key source, plaintext minified browser fixtures and obfuscation
mapping stay under private `build/` directories. The generated key implementation
is obfuscated with the Java code. The runtime key and decrypted data can still be
recovered by someone analyzing the running client. Java/Fabric hooks remain runnable
obfuscated bytecode, not encrypted classes. This is copying deterrence, not DRM.
Never place a
backend signing key, shared privileged API token, password, or other long-lived
secret in the mod. The client-presence registry is server-side and handles only
ephemeral cosmetic announcements, not authenticated Minecraft identity. Existing
skin/cape services and their credentials are outside this mod build and unchanged.

Run `gradlew.bat testDistribution` to produce the shareable test ZIP under
`build/test-distribution/`. It contains only the release JAR in `mods/`, README,
license and SHA-256 checksum. Never send the project folder, generated key source,
private mappings, or the plaintext `build/packaged-web` fixtures to testers.
