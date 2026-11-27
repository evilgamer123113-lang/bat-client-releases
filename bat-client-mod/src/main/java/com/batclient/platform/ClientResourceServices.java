package com.batclient.platform;

import java.util.ServiceLoader;

public final class ClientResourceServices {
    private ClientResourceServices() {}
    private static final class Holder {
        static final ClientResources INSTANCE=find();
        private static ClientResources find(){
            var providers=ServiceLoader.load(ClientResources.class).iterator();
            if(!providers.hasNext())throw new IllegalStateException("Missing Bat resource adapter");
            ClientResources result=providers.next();
            if(providers.hasNext())throw new IllegalStateException("Ambiguous Bat resource adapters");
            return result;
        }
    }
    public static ClientResources get(){return Holder.INSTANCE;}
}
