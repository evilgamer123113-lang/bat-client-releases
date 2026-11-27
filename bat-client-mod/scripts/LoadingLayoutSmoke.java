import com.batclient.hud.LoadingLayout;
public class LoadingLayoutSmoke {
    public static void main(String[] args){
        int checks=0;
        for(int width:new int[]{854,1280,1680,1920,3840})for(int scale:new int[]{1,2,3,4}){
            int height=width*9/16;var layout=LoadingLayout.of(width/scale,height/scale,scale,2144,512);
            if(layout.width()*scale>480||layout.barWidth()*scale>384||layout.barWidth()>=layout.width()||layout.barHeight()>3||layout.barTop()<=layout.top()+layout.height()||Math.abs(layout.left()*2+layout.width()-width/scale)>1)throw new AssertionError("fullscreen sizing "+width+"/"+scale);
            checks++;
        }
        System.out.println("LOADING_LAYOUT_OK: "+checks+" viewport/GUI-scale cases");
    }
}
