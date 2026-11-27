package com.batclient;

public final class BatClientMod {
    public static final org.slf4j.Logger LOGGER = (org.slf4j.Logger)java.lang.reflect.Proxy.newProxyInstance(
        BatClientMod.class.getClassLoader(),new Class<?>[]{org.slf4j.Logger.class},(proxy,method,args)->{
            if(method.getName().equals("warn")||method.getName().equals("error"))
                System.err.println("PERSISTENCE_FIXTURE: "+java.util.Arrays.deepToString(args));
            if(method.getReturnType()==boolean.class)return false;
            return null;
        });
}
