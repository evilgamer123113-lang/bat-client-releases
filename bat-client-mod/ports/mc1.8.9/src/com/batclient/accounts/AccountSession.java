package com.batclient.accounts;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.util.Session;

/** Per-account runtime session holder for 1.8 / 1.8.9. */
public final class AccountSession {
    private final Session session;
    private final GameProfile profile;

    public AccountSession(Session session, GameProfile profile) {
        this.session = session;
        this.profile = profile;
    }

    public Session session() { return session; }
    public GameProfile profile() { return profile; }
}
