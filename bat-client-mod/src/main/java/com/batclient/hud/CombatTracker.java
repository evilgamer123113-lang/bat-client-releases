package com.batclient.hud;

/** Pending client ray measurement paired once with a matching server damage confirmation. */
public final class CombatTracker {
    private int pending=-1,target=-1,combo;
    private long attempted,lastCombo,lastReach;
    private double pendingReach,reach=Double.NaN;
    public void attack(int entity,double distance,long now) {
        pending=Double.isFinite(distance)&&distance>=0?entity:-1;pendingReach=distance;attempted=now;
    }
    public void confirm(int entity,long now,long comboTimeout,boolean countCombo,boolean recordReach) {
        if(entity!=pending||now<attempted||now-attempted>1500)return;
        pending=-1;
        if(countCombo){combo=target==entity&&now-lastCombo<=comboTimeout?combo+1:1;target=entity;lastCombo=now;}
        if(recordReach){reach=pendingReach;lastReach=now;}
    }
    public int combo(long now,long timeout){return now-lastCombo>timeout?0:combo;}
    public double reach(long now,long timeout){return now-lastReach>timeout?Double.NaN:reach;}
    public void damaged(){combo=0;target=-1;pending=-1;}
    public void reset(){damaged();reach=Double.NaN;lastCombo=lastReach=0;}
}
