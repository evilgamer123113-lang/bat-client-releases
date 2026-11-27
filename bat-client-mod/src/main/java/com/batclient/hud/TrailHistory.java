package com.batclient.hud;

import java.util.*;

/** Bounded session-only observations, never an inferred path through unseen terrain. */
public final class TrailHistory {
    public record Point(double x,double y,double z,long time,boolean breakBefore){
        public double distanceSquared(double a,double b,double c){double dx=x-a,dy=y-b,dz=z-c;return dx*dx+dy*dy+dz*dz;}
    }
    private final LinkedHashMap<String,ArrayDeque<Point>> worlds=new LinkedHashMap<>(8,.75f,true);
    private String active="";private boolean gap=true;
    public void context(String key){if(!Objects.equals(active,key)){active=key;gap=true;}}
    public void interrupt(){gap=true;}
    public List<Point> points(String key,long now,long lifetime,int maximum){var q=worlds.get(key);if(q==null)return List.of();prune(q,now,lifetime,maximum);return List.copyOf(q);}
    private void prune(ArrayDeque<Point> q,long now,long lifetime,int maximum){int cap=Math.clamp(maximum,32,1024);while(!q.isEmpty()&&(now-q.getFirst().time()>=lifetime||q.size()>cap))q.removeFirst();}
    public boolean sample(String key,double x,double y,double z,long now,long lifetime,int maximum,double spacing){
        if(key==null||key.isEmpty()||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))return false;context(key);
        var q=worlds.computeIfAbsent(key,k->new ArrayDeque<>());while(worlds.size()>8)worlds.remove(worlds.keySet().iterator().next());prune(q,now,lifetime,maximum);
        var last=q.peekLast();double step=Math.max(.5,Math.min(4,spacing));
        if(last!=null&&!gap&&last.distanceSquared(x,y,z)<step*step)return false;
        boolean split=gap||last==null||last.distanceSquared(x,y,z)>16*16;
        q.addLast(new Point(x,y,z,now,split));gap=false;prune(q,now,lifetime,maximum);return true;
    }
    public void clear(String key){worlds.remove(key);if(Objects.equals(active,key))gap=true;}
    public void clearAll(){worlds.clear();active="";gap=true;}
    public static List<Point> latestRoute(List<Point> points){if(points.isEmpty())return List.of();int start=points.size()-1;while(start>0&&!points.get(start).breakBefore())start--;return List.copyOf(points.subList(start,points.size()));}
}
