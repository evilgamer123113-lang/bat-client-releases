package com.batclient.platform;

import java.util.Iterator;
import java.util.ServiceLoader;

public final class ClientHookServices {
    private ClientHookServices() { }
    private static final class Holder {
        private static final ClientHooks HOOKS=load();
        private static ClientHooks load(){
            Iterator<ClientHooks> providers=ServiceLoader.load(ClientHooks.class,ClientHookServices.class.getClassLoader()).iterator();
            if(!providers.hasNext())throw new IllegalStateException("Missing Bat Client event adapter");
            ClientHooks hooks=providers.next();
            if(providers.hasNext())throw new IllegalStateException("Multiple Bat Client event adapters");
            return hooks;
        }
    }
    public static ClientHooks get(){return Holder.HOOKS;}
}
