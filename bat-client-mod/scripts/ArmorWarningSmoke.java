import com.batclient.hud.ArmorWarning;
public class ArmorWarningSmoke {
    public static void main(String[] args) {
        check(ArmorWarning.low(100,85,true,15),"threshold inclusive");
        check(!ArmorWarning.low(100,84,true,15),"healthy armor");
        check(!ArmorWarning.low(100,99,false,15),"disabled warning");
        check(!ArmorWarning.low(0,0,true,15),"non damageable");
        check(!ArmorWarning.low(100,-1,true,15),"invalid damage");
        check(ArmorWarning.low(100,110,true,15),"exhausted durability");
        check(ArmorWarning.low(100,85,true,Float.NaN),"invalid threshold fallback");
        check(!ArmorWarning.low(100,40,true,100),"threshold upper clamp");
        check(ArmorWarning.visible(true,0)&&!ArmorWarning.visible(true,700)&&ArmorWarning.visible(true,1000),"flash phases");
        check(ArmorWarning.visible(false,700),"steady warning");
        System.out.println("ARMOR_WARNING_OK: 10 checks");
    }
    static void check(boolean ok,String message) { if(!ok)throw new AssertionError(message); }
}
