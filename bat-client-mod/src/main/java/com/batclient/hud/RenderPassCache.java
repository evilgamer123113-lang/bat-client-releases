package com.batclient.hud;

/** One nullable query result, reusable only inside the current synchronous render scope. */
public final class RenderPassCache<T> {
    private int depth;
    private boolean valid,preview;
    private Object owner,settings;
    private T value;
    public void begin(){if(depth++==0)clear();}
    public void end(){if(depth<=0)throw new IllegalStateException("Unbalanced render scope");if(--depth==0)clear();}
    private void clear(){valid=false;owner=settings=null;value=null;}
    public boolean matches(Object owner,Object settings,boolean preview){return depth>0&&valid&&this.owner==owner&&this.settings==settings&&this.preview==preview;}
    public T value(){return value;}
    public void remember(Object owner,Object settings,boolean preview,T value){if(depth==0)return;this.owner=owner;this.settings=settings;this.preview=preview;this.value=value;valid=true;}
}
