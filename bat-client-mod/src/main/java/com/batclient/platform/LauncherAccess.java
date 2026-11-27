package com.batclient.platform;

import java.nio.file.Path;
import java.util.Locale;

/** Checks the direct parent of the game JVM before initializing the mod. */
public final class LauncherAccess {
    private static final String LAUNCHER_PID = "BAT_CLIENT_LAUNCHER_PID";

    private LauncherAccess() {}

    public static void require() {
        if (Platforms.get().isDevelopmentEnvironment()) return;
        if (!ProcessHandle.current().parent().map(LauncherAccess::isBatLauncher).orElse(false)) {
            throw new IllegalStateException("Bat Client mod requires an updated Bat Client launcher. "
                + "Start this loadout from Bat Client or remove the companion mod from the loadout.");
        }
    }

    private static boolean isBatLauncher(ProcessHandle parent) {
        String expectedPid = System.getenv(LAUNCHER_PID);
        if (expectedPid == null || expectedPid.isBlank()) return false;
        if (!Long.toString(parent.pid()).equals(expectedPid)) return false;
        // On Windows with AV/security hooks (e.g. McAfee), parent.info().command() may be empty;
        // when present, verify it is a Bat Client launcher executable.
        return parent.info().command().map(command -> {
            String name = Path.of(command).getFileName().toString().toLowerCase(Locale.ROOT);
            return name.equals("bat-client-next")
                || name.equals("bat-client-next.exe")
                || name.equals("bat-client")
                || name.equals("bat-client.exe")
                || name.equals("bat client")
                || name.equals("bat client.exe");
        }).orElse(true);
    }
}
