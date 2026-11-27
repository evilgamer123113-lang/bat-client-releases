import com.batclient.hud.SkinAlphaMask;
import com.batclient.hud.RenderPassCache;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public final class CustomRenderingSmoke {
    public static void main(String[] args) {
        Random random=new Random(44283);int[] pixels=new int[4096];AtomicInteger reads=new AtomicInteger();
        for(int round=0;round<32;round++){
            for(int i=0;i<pixels.length;i++)pixels[i]=random.nextInt();
            SkinAlphaMask mask=new SkinAlphaMask((x,y)->{reads.incrementAndGet();return pixels[y*64+x];});
            for(int y=0;y<64;y++)for(int x=0;x<64;x++)if(mask.opaque(x,y)!=((pixels[y*64+x]>>>24)>=128))throw new AssertionError("alpha/edge mismatch");
            if(mask.opaque(-1,0)||mask.opaque(64,0)||mask.opaque(0,-1)||mask.opaque(0,64))throw new AssertionError("outside skin mask");
            boolean first=mask.opaque(0,0);pixels[0]=~pixels[0];if(mask.opaque(0,0)!=first)throw new AssertionError("retained mutable pixels");
            SkinAlphaMask recolored=new SkinAlphaMask((x,y)->(mask.opaque(x,y)?0xFF000000:0)|0x123456);
            if(!mask.sameOpacity(recolored))throw new AssertionError("RGB-only changes invalidate geometry");
        }
        if(reads.get()!=32*4096)throw new AssertionError("pixel source read outside capture");
        RenderPassCache<Object> cache=new RenderPassCache<>();Object owner=new Object(),settings=new Object(),value=new Object();
        cache.remember(owner,settings,false,value);if(cache.matches(owner,settings,false))throw new AssertionError("cached outside frame");
        int queries=0;
        cache.begin();
        try{
            for(int draw=0;draw<3;draw++)if(!cache.matches(owner,settings,false)){queries++;cache.remember(owner,settings,false,null);}
            if(queries!=1||cache.value()!=null)throw new AssertionError("miss raycast was not reused");
            if(cache.matches(owner,new Object(),false)||cache.matches(owner,settings,true))throw new AssertionError("preview/settings leaked");
            cache.begin();try{if(!cache.matches(owner,settings,false))throw new AssertionError("nested scope discarded query");}finally{cache.end();}
        }finally{cache.end();}
        if(cache.matches(owner,settings,false)||cache.value()!=null)throw new AssertionError("frame retained world/query");
        cache.begin();try{if(cache.matches(owner,settings,false))throw new AssertionError("new frame stale");cache.remember(owner,settings,false,value);}finally{cache.end();}
        System.out.println("CUSTOM_RENDERING_OK: 131072 alpha comparisons, immutable mask, bounded pixel reads, one nullable query per render pass, scope/preview invalidation");
    }
}
