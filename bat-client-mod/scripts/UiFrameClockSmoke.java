import com.batclient.web.UiFrameClock;

class UiFrameClockSmoke {
    public static void main(String[] args) {
        for (int sourceFps : new int[]{30, 60, 90, 144, 240}) {
            UiFrameClock clock = new UiFrameClock();
            int rendered = 0;
            for (int frame = 0; frame < sourceFps; frame++) {
                if (clock.shouldRender(frame * 1_000_000_000L / sourceFps, false)) rendered++;
            }
            if (Math.abs(rendered - Math.min(60, sourceFps)) > 1)
                throw new AssertionError(sourceFps + " game FPS produced " + rendered + " UI frames");
        }
        UiFrameClock clock = new UiFrameClock();
        if (!clock.shouldRender(0, false) || clock.shouldRender(1, false)) throw new AssertionError("Idle pacing");
        clock.invalidate();
        if (!clock.shouldRender(2, false)) throw new AssertionError("Immediate input response");
        if (!clock.shouldRender(3, true)) throw new AssertionError("Loading/resize bypass");
        if (!clock.shouldRender(9_000_000_000L, false) || clock.shouldRender(9_000_000_001L, false))
            throw new AssertionError("No catch-up burst after inactivity");
        System.out.println("UI_FRAME_CLOCK_OK");
        UiFrameClock jittered = new UiFrameClock();
        int frames=0;
        for(int i=0;i<60;i++)if(jittered.shouldRender(i*1_000_000_000L/60-(i%2==1?1_000_000:0),false))frames++;
        if(frames!=60)throw new AssertionError("Small frame jitter dropped UI frames: "+frames);
        System.out.println("UI_FRAME_JITTER_OK: 60 frames");
    }
}
