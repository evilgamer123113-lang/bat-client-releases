/** Private benchmark navigation: stop before cliffs/unknown chunks, climb, then advance. */
public final class FlightRouteController {
    public record Motion(double forward, double vertical) { }
    public static Motion next(double y, int highestAhead, boolean loaded) {
        if (!loaded) return new Motion(0, 0);
        double clearance=highestAhead+24;
        if (y<clearance) return new Motion(0,.8);
        return new Motion(.8,y>clearance+6?-.5:0);
    }
    public static void main(String[] args) {
        double y=110;
        for(int tick=0;tick<200;tick++){
            Motion movement=next(y,200,true);
            if(y<224&&movement.forward()!=0)throw new AssertionError("Flew into cliff");
            y+=movement.vertical();
        }
        if(y<224||next(y,200,true).forward()==0)throw new AssertionError("Did not clear cliff");
        Motion unknown=next(y,200,false);
        if(unknown.forward()!=0||unknown.vertical()!=0)throw new AssertionError("Flew into unknown terrain");
        if(next(260,200,true).vertical()>=0)throw new AssertionError("No terrain following descent");
        System.out.println("FLIGHT_ROUTE_CONTROLLER_OK: cliff stops forward motion until clear; unknown chunks wait; safe descent");
    }
}
