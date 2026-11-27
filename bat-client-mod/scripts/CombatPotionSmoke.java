import com.batclient.hud.CombatTracker;
import com.batclient.hud.PotionExpiry;
public class CombatPotionSmoke {
    static int checks;
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    public static void main(String[] args){
        var tracker=new CombatTracker();
        check(tracker.combo(1000,3000)==0&&Double.isNaN(tracker.reach(1000,5000)),"empty trackers");
        tracker.attack(1,2.75,1000);check(tracker.combo(1050,3000)==0,"attack attempt does not count");
        tracker.confirm(2,1100,3000,true,true);check(tracker.combo(1100,3000)==0,"other entity damage ignored");
        tracker.confirm(1,1100,3000,true,true);check(tracker.combo(1100,3000)==1&&tracker.reach(1100,5000)==2.75,"matching confirmation records client reach and combo");
        tracker.confirm(1,1150,3000,true,true);check(tracker.combo(1150,3000)==1,"duplicate packet consumed once");
        tracker.attack(1,2.5,1200);tracker.confirm(1,1250,3000,true,true);check(tracker.combo(1250,3000)==2,"same target combo");
        tracker.attack(2,2,1300);tracker.confirm(2,1350,3000,true,true);check(tracker.combo(1350,3000)==1,"new target resets combo");
        check(tracker.combo(5000,3000)==0&&Double.isNaN(tracker.reach(7000,5000)),"independent timeouts");
        tracker.attack(2,1.5,7000);tracker.confirm(2,9000,3000,true,true);check(tracker.combo(9000,3000)==0,"late confirmations rejected");
        tracker.attack(2,1.5,10000);tracker.confirm(2,10100,3000,false,true);check(tracker.combo(10100,3000)==0&&tracker.reach(10100,5000)==1.5,"module filters independent");
        tracker.attack(2,1.5,11000);tracker.confirm(2,11100,3000,true,false);tracker.damaged();check(tracker.combo(11100,3000)==0&&tracker.reach(11100,5000)==1.5,"taking damage breaks combo but keeps last reach");
        tracker.reset();check(Double.isNaN(tracker.reach(11200,5000)),"world/player reset clears reach");
        tracker.attack(2,Double.NaN,12000);tracker.confirm(2,12010,3000,true,true);check(tracker.combo(12010,3000)==0,"invalid geometry rejected");
        check(PotionExpiry.warning(200,false,true,10),"threshold inclusive");
        check(!PotionExpiry.warning(201,false,true,10),"above threshold normal");
        check(!PotionExpiry.warning(-1,true,true,10),"infinite effect never warns");
        check(!PotionExpiry.warning(0,false,true,10)&&!PotionExpiry.warning(100,false,false,10),"expired/disabled normal");
        check(PotionExpiry.warning(100,false,true,Float.NaN),"malformed threshold safe default");
        System.out.println("COMBAT_POTION_OK: "+checks+" confirmation, reach, combo reset and potion-expiry checks");
    }
}
