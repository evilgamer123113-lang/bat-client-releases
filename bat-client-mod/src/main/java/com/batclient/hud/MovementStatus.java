package com.batclient.hud;

import java.util.Locale;

/** Read-only movement label, ordered by the player's actual movement mode. */
public record MovementStatus(boolean riding,boolean gliding,boolean flying,boolean swimming,
                             boolean sneaking,boolean sprinting,boolean moving,boolean toggledSprint,
                             boolean heldSprint,float flightMultiplier) {
    public String label(boolean showFlightSpeed) {
        if (riding) return "Riding";
        if (gliding) return "Gliding";
        if (flying) {
            if (!showFlightSpeed) return "Flying";
            float multiplier=Float.isFinite(flightMultiplier)&&flightMultiplier>0?flightMultiplier:1;
            multiplier=Math.round(Math.min(multiplier,100)*10)/10f;
            String value=multiplier==(int)multiplier?Integer.toString((int)multiplier):String.format(Locale.ROOT,"%.1f",multiplier);
            return "Flying "+value+"x";
        }
        if (swimming) return "Swimming";
        if (sneaking) return "Sneaking";
        if (sprinting) return toggledSprint?"Sprinting (Toggled)":heldSprint?"Sprinting (Held)":"Sprinting";
        return moving?"Walking":"Standing";
    }
}
