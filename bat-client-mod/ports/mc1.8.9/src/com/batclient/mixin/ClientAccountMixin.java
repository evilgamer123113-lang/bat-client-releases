package com.batclient.mixin;

import com.batclient.ClientAccountAccess;
import com.batclient.accounts.AccountSession;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Session;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(MinecraftClient.class)
public abstract class ClientAccountMixin implements ClientAccountAccess {
    @Shadow @Final @Mutable private Session session;
    @Shadow private static int currentFps;

    public int bat$currentFps() { return currentFps; }

    public String bat$accounts(String action, String value) {
        return com.batclient.accounts.ClientAccounts.action((MinecraftClient) (Object) this, action, value);
    }

    public AccountSession bat$captureAccount() {
        GameProfile profile = session.getProfile();
        profile.getProperties().putAll(((MinecraftClient) (Object) this).getSessionProperties());
        return new AccountSession(session, profile);
    }

    public void bat$applyAccount(AccountSession account) {
        this.session = account.session();
    }
}
