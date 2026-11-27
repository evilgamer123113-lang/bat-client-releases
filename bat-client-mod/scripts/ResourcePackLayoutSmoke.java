import com.batclient.hud.ResourcePackCardLayout;

public class ResourcePackLayoutSmoke {
    static int checks;
    static void check(boolean condition,String message) { if(!condition)throw new AssertionError(message);checks++; }
    public static void main(String[] args) {
        for(String side:new String[]{"Left","Right","Above"})
            for(int textW:new int[]{0,75,640}) for(int textH:new int[]{0,10,240})
                for(int icon:new int[]{0,16,64}) for(int gap:new int[]{0,6,20}) {
                    var box=ResourcePackCardLayout.resolve(textW,textH,icon,gap,side);
                    check(box.textX()>=0&&box.textY()>=0&&box.textX()+textW<=box.width()&&box.textY()+textH<=box.height(),"text inside background");
                    check(box.iconX()>=0&&box.iconY()>=0&&box.iconX()+icon<=box.width()&&box.iconY()+icon<=box.height(),"icon inside background");
                    if(icon>0&&textH>0)check(box.iconX()+icon<=box.textX()||box.textX()+textW<=box.iconX()||box.iconY()+icon<=box.textY(),"icon and text never overlap");
                }
        var empty=ResourcePackCardLayout.resolve(0,0,0,20,"Left");check(empty.width()==0&&empty.height()==0,"all hidden collapses background");
        var text=ResourcePackCardLayout.resolve(100,30,0,20,"Above");check(text.width()==100&&text.height()==30&&text.textY()==0,"hidden icon has no gap");
        var icon=ResourcePackCardLayout.resolve(0,0,32,20,"Left");check(icon.width()==32&&icon.height()==32,"icon-only has no text gap");
        System.out.println("RESOURCE_PACK_LAYOUT_OK: "+checks+" bounds, non-overlap and hidden-element checks");
    }
}
