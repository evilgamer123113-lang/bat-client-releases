package com.batclient;

import java.util.regex.Pattern;

/** Explicit labeled coordinates only. A click opens a confirmation editor; never saves from chat automatically. */
public final class WaypointChat {
    private static final Pattern XYZ=Pattern.compile("(?i)(?<![a-z])X\\s*:\\s*(-?\\d{1,8})\\s*[,;]?\\s+Y\\s*:\\s*(-?\\d{1,5})\\s*[,;]?\\s+Z\\s*:\\s*(-?\\d{1,8})(?!\\d)");
    private WaypointChat(){}
    public static int[] parse(String message){if(message==null||message.length()>4096)return null;var m=XYZ.matcher(message);if(!m.find())return null;try{int x=Integer.parseInt(m.group(1)),y=Integer.parseInt(m.group(2)),z=Integer.parseInt(m.group(3));return Math.abs(x)<=29999984&&y>=-2048&&y<=4096&&Math.abs(z)<=29999984?new int[]{x,y,z}:null;}catch(NumberFormatException e){return null;}}
    public static boolean click(String message){if(!VisualModules.enabled("waypoints")||!HudConfig.bool(VisualModules.settings("waypoints"),"chat",true))return false;var p=parse(message);if(p==null)return false;Waypoints.chatDraft(p[0],p[1],p[2]);return true;}
}
