import com.batclient.hud.HealthValues;
public class HealthValuesSmoke {
    public static void main(String[] args) {
        check(HealthValues.hearts(14,20,true).equals("♥ 7.0 / 10.0"));
        check(HealthValues.hearts(1,20,false).equals("♥ 0.5"));
        check(HealthValues.health(-2,20)==0&&HealthValues.health(30,20)==20);
        check(HealthValues.ratio(Float.NaN,0)==0&&HealthValues.max(Float.POSITIVE_INFINITY)==20);
        check(HealthValues.absorption(Float.NaN)==0&&HealthValues.absorption(-2)==0);
        check(HealthValues.row(0,20).equals("♡".repeat(10)));
        check(HealthValues.row(20,20).equals("♥".repeat(10)));
        check(HealthValues.row(300,300).length()==10);
        check(HealthValues.fill(19,20,9)==1&&HealthValues.fill(18,20,9)==0);
        check(HealthValues.fill(20,20,9)==2&&HealthValues.fill(0,20,0)==0);
        check(HealthValues.slots(20)==10&&HealthValues.slots(300)==30);
        check(HealthValues.fill(Float.NaN,20,0)==0&&HealthValues.fill(1,20,0)==1);
        System.out.println("HEALTH_VALUES_OK: 12 checks");
    }
    static void check(boolean value){if(!value)throw new AssertionError();}
}
