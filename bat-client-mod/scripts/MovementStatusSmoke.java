import com.batclient.hud.MovementStatus;

public class MovementStatusSmoke {
    static int checks;
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;}
    public static void main(String[] args){
        check(new MovementStatus(false,false,false,false,false,false,false,false,false,1).label(true).equals("Standing"),"stationary");
        check(new MovementStatus(false,false,false,false,false,false,true,false,false,1).label(true).equals("Walking"),"walking");
        check(new MovementStatus(false,false,false,false,false,true,true,true,false,1).label(true).equals("Sprinting (Toggled)"),"toggled sprint");
        check(new MovementStatus(false,false,false,false,false,true,true,false,true,1).label(true).equals("Sprinting (Held)"),"held sprint");
        check(new MovementStatus(false,false,false,false,false,true,true,false,false,1).label(true).equals("Sprinting"),"vanilla sprint");
        check(new MovementStatus(false,false,true,false,false,true,true,true,false,1).label(true).equals("Flying 1x"),"flying takes precedence over sprint");
        check(new MovementStatus(false,false,true,false,false,false,true,false,false,2.5f).label(true).equals("Flying 2.5x"),"custom ability speed");
        check(new MovementStatus(false,false,true,false,false,false,true,false,false,1).label(false).equals("Flying"),"optional multiplier");
        check(new MovementStatus(false,false,true,false,false,false,true,false,false,Float.NaN).label(true).equals("Flying 1x"),"malformed multiplier");
        check(new MovementStatus(false,true,true,false,false,true,true,true,false,1).label(true).equals("Gliding"),"elytra precedence");
        check(new MovementStatus(true,true,true,true,true,true,true,true,false,1).label(true).equals("Riding"),"vehicle precedence");
        check(new MovementStatus(false,false,false,true,false,true,true,true,false,1).label(true).equals("Swimming"),"swimming precedence");
        check(new MovementStatus(false,false,false,false,true,true,true,true,false,1).label(true).equals("Sneaking"),"sneaking precedence");
        System.out.println("MOVEMENT_STATUS_OK: "+checks+" movement labels, flight multipliers and precedence checks");
    }
}
