package com.batclient.accounts;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.client.session.Session;
import java.util.concurrent.CompletableFuture;

/** Native session state only; never serialized or passed to the web UI. */
public record AccountSession(Session session, UserApiService api,
        CompletableFuture<ProfileResult> profile,
        CompletableFuture<UserApiService.UserProperties> properties) { }
