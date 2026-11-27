import com.batclient.hud.ZoomInterpolation;

class ZoomSmoke {
    public static void main(String[] args) {
        double target = ZoomInterpolation.target(true, 4);
        require(Math.abs(70 * target - 17.5) < 1e-9, "Camera can zoom below option minimum");
        require(ZoomInterpolation.target(false, 4) == 1, "Release restores normal FOV");
        double a = animate(60, 1, target), b = animate(240, 1, target);
        require(Math.abs(a - b) < .0001 && Math.abs(a - target) < .0001, "Frame-rate independent zoom-in");
        require(Math.abs(animate(60, target, 1) - 1) < .0001, "Smooth zoom-out restores FOV");
        require(ZoomInterpolation.target(true, 100) == .025, "Zoom level bounded");
        double deeper = ZoomInterpolation.scrollLevel(4, 2, 1, 20);
        require(deeper > 4, "Scroll up increases magnification");
        require(Math.abs(ZoomInterpolation.scrollLevel(deeper, -2, 1, 20) - 4) < 1e-9, "Scroll down reverses magnification");
        require(ZoomInterpolation.scrollLevel(19, 20, 2, 20) == 20, "Maximum scroll zoom bounded");
        require(ZoomInterpolation.scrollLevel(2, -20, 2, 20) == 1, "Scroll cannot invert camera FOV");
        require(ZoomInterpolation.step(1, .25, .05, 30) < ZoomInterpolation.step(1, .25, .05, 5), "Speed setting affects transition");
        require(ZoomInterpolation.mouseScale(1, 10) == 1, "Normal gameplay sensitivity unchanged");
        require(ZoomInterpolation.mouseScale(.05, 75) < ZoomInterpolation.mouseScale(.25, 75), "Scroll magnification reduces mouse speed further");
        require(ZoomInterpolation.mouseScale(.25, 25) < ZoomInterpolation.mouseScale(.25, 100), "Custom mouse sensitivity applied");
        System.out.println("ZOOM_SMOKE_OK");
    }
    static double animate(int fps, double start, double target) {
        for (int i = 0; i < fps; i++) start = ZoomInterpolation.step(start, target, 1.0 / fps);
        return start;
    }
    static void require(boolean pass, String message) { if (!pass) throw new AssertionError(message); }
}
