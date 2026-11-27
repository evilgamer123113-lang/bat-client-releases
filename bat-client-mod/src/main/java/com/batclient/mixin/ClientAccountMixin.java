package com.batclient.mixin;

import com.batclient.ClientAccountAccess;
import com.batclient.accounts.AccountSession;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.SocialInteractionsManager;
import net.minecraft.client.realms.RealmsClient;
import net.minecraft.client.realms.RealmsPeriodicCheckers;
import net.minecraft.client.session.ProfileKeys;
import net.minecraft.client.session.Session;
import net.minecraft.client.session.report.AbuseReportContext;
import net.minecraft.client.session.report.ReporterEnvironment;
import net.minecraft.client.session.telemetry.TelemetryManager;
import org.spongepowered.asm.mixin.*;
import java.util.concurrent.CompletableFuture;

@Mixin(MinecraftClient.class)
public abstract class ClientAccountMixin implements ClientAccountAccess {
    @Shadow @Final @Mutable private Session session;
    @Shadow @Final @Mutable private UserApiService userApiService;
    @Shadow @Final @Mutable private CompletableFuture<ProfileResult> gameProfileFuture;
    @Shadow @Final @Mutable private CompletableFuture<UserApiService.UserProperties> userPropertiesFuture;
    @Shadow @Final @Mutable private SocialInteractionsManager socialInteractionsManager;
    @Shadow @Final @Mutable private ProfileKeys profileKeys;
    @Shadow @Final @Mutable private TelemetryManager telemetryManager;
    @Shadow @Final @Mutable private RealmsPeriodicCheckers realmsPeriodicCheckers;
    @Shadow private AbuseReportContext abuseReportContext;

    public String bat$accounts(String action,String value) {
        return com.batclient.accounts.ClientAccounts.action((MinecraftClient)(Object)this,action,value);
    }

    public AccountSession bat$captureAccount() {
        return new AccountSession(session,userApiService,gameProfileFuture,userPropertiesFuture);
    }
    public void bat$applyAccount(AccountSession account) {
        MinecraftClient client=(MinecraftClient)(Object)this;
        if(!client.isOnThread() || client.world!=null || client.getNetworkHandler()!=null || client.getServer()!=null)
            throw new IllegalStateException("Return to the main menu before switching accounts.");
        // Replace account-bound services together, including signing keys and report identity.
        session=account.session();userApiService=account.api();
        gameProfileFuture=account.profile();userPropertiesFuture=account.properties();
        socialInteractionsManager=new SocialInteractionsManager(client,userApiService);
        profileKeys=ProfileKeys.create(userApiService,session,client.runDirectory.toPath());
        abuseReportContext=AbuseReportContext.create(ReporterEnvironment.ofIntegratedServer(),userApiService);
        telemetryManager.close();
        telemetryManager=new TelemetryManager(client,userApiService,session);
        realmsPeriodicCheckers=new RealmsPeriodicCheckers(RealmsClient.createRealmsClient(client));
        client.inGameHud.getChatHud().clear(true);
    }
}
