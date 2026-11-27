package com.batclient.api.waypoints;

import java.util.UUID;

/** Reusable builder; createWaypoint snapshots its values for each player. */
public final class WaypointBuilder {
    final int x,y,z;
    String name="Waypoint",color="#75CFFF",group="Server";
    UUID worldId;
    int duration,opacity=100;
    boolean beam=true,chroma;
    public WaypointBuilder(int x,int y,int z){if(Math.abs((long)x)>29999984||Math.abs((long)z)>29999984||y< -2048||y>4096)throw new IllegalArgumentException("Coordinates outside supported world bounds");this.x=x;this.y=y;this.z=z;}
    private static String label(String value,int max){if(value==null||value.isBlank()||value.length()>max||value.chars().anyMatch(c->Character.isISOControl(c)||c==167))throw new IllegalArgumentException("Invalid marker label");return value.strip();}
    public WaypointBuilder withName(String value){name=label(value,48);return this;}
    public WaypointBuilder withGroup(String value){group=label(value,24);return this;}
    public WaypointBuilder withColor(int red,int green,int blue){if(red<0||red>255||green<0||green>255||blue<0||blue>255)throw new IllegalArgumentException("RGB channels must be 0–255");color=String.format(java.util.Locale.ROOT,"#%02X%02X%02X",red,green,blue);return this;}
    public WaypointBuilder withColor(int red,int green,int blue,int alpha){if(alpha<0||alpha>255)throw new IllegalArgumentException("Alpha must be 0–255");withColor(red,green,blue);return withOpacity(Math.round(alpha*100f/255));}
    public WaypointBuilder withOpacity(int percent){if(percent<0||percent>100)throw new IllegalArgumentException("Opacity must be 0–100");opacity=percent;return this;}
    public WaypointBuilder withChroma(boolean value){chroma=value;return this;}
    public WaypointBuilder withBeam(boolean value){beam=value;return this;}
    public WaypointBuilder withDuration(int seconds){if(seconds<0||seconds>604800)throw new IllegalArgumentException("Duration must be 0–604800 seconds; 0 is permanent for this connection");duration=seconds;return this;}
    public WaypointBuilder withWorldId(UUID value){worldId=java.util.Objects.requireNonNull(value);return this;}
}
