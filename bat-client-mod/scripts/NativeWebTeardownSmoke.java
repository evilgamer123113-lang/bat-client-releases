import io.github.nilsen84.ultralight.*;

/** Standalone native teardown diagnostic. No Minecraft, GL context, user data or forced JVM exit. */
public final class NativeWebTeardownSmoke {
    public static void main(String[] args)throws Exception {
        String mode=args.length==0?"load-only":args[0];
        UltralightLoader.INSTANCE.load();
        if(!mode.equals("load-only")){
            UltralightRenderer renderer=Ultralight.INSTANCE.createRenderer(null,null,null);
            UltralightView sentinel=mode.equals("sentinel-view")?renderer.createView(1,1):null;
            if(!mode.equals("renderer-only")){
                UltralightView view=renderer.createView(128,64);
                if(mode.equals("callback-view")||mode.equals("pump-after-close")||mode.equals("unclosed-callback-view")){
                    view.loadUrl("data:text/html,<html><body>Bat native teardown diagnostic</body></html>");
                    for(int i=0;i<40;i++){renderer.update();renderer.refreshDisplay();renderer.render();Thread.sleep(10);}
                    view.bindFunction("batDiagnostic",values->"ok");
                    if(!view.evaluateScript("batDiagnostic()").equals("ok"))throw new AssertionError("Native callback failed");
                }
                if(!mode.startsWith("unclosed-")){view.setLoadListener(null);view.setViewListener(null);view.close();}
                if(mode.equals("pump-after-close"))for(int i=0;i<10;i++){renderer.update();renderer.refreshDisplay();renderer.render();Thread.sleep(10);}
            }
        }
        System.out.println("NATIVE_WEB_FIXTURE_COMPLETED: "+mode+"; natural JVM return follows");
    }
}
