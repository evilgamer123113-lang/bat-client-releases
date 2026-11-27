import com.batclient.accounts.AccountHeads;
import net.minecraft.client.session.Session;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;

public class SkinHeadsSmoke {
    static int checks;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
    static byte[] png(BufferedImage image)throws Exception{var out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);return out.toByteArray();}
    static BufferedImage decode(String uri)throws Exception{return ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(uri.substring(uri.indexOf(',')+1))));}
    public static void main(String[] args)throws Exception {
        var skin=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);
        for(int y=8;y<16;y++)for(int x=8;x<16;x++)skin.setRGB(x,y,0xffaa4422);
        skin.setRGB(40,8,0xff00ff00);skin.setRGB(41,8,0x800000ff);
        var face=decode(AccountHeads.face(png(skin)));
        check(face.getWidth()==8&&face.getHeight()==8,"head is an 8x8 PNG");
        check(face.getRGB(0,0)==0xff00ff00,"opaque hat overlays face");
        check(face.getRGB(2,0)==0xffaa4422,"transparent hat preserves face");
        check(face.getRGB(1,0)!=0xffaa4422&&face.getRGB(1,0)!=0xff0000ff,"partial hat alpha blends correctly");
        var legacy=new BufferedImage(64,32,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<32;y++)for(int x=0;x<64;x++)legacy.setRGB(x,y,0xff000000);
        for(int y=8;y<16;y++)for(int x=8;x<16;x++)legacy.setRGB(x,y,0xffaa4422);
        check(decode(AccountHeads.face(png(legacy))).getRGB(0,0)==0xffaa4422,"legacy unused opaque hat does not hide face");
        try{AccountHeads.face(png(new BufferedImage(32,32,BufferedImage.TYPE_INT_ARGB)));throw new AssertionError("invalid dimensions accepted");}catch(IOException expected){checks++;}
        check(AccountHeads.officialTexture(URI.create("http://textures.minecraft.net/texture/"+"a".repeat(64))).getScheme().equals("https"),"official texture fetch upgrades HTTPS");
        try{AccountHeads.officialTexture(URI.create("https://evil.test/texture/"+"a".repeat(64)));throw new AssertionError("untrusted texture host accepted");}catch(IOException expected){checks++;}
        Path root=Path.of(args[0]);Files.createDirectories(root.resolve("skins"));
        Files.write(root.resolve("skins/selected.png"),png(skin));Files.write(root.resolve("skins/slot.png"),png(legacy));
        Files.writeString(root.resolve("profiles.config.json"),"{\"savedAccounts\":[{\"username\":\"NightBat\",\"skinFile\":\"selected.png\",\"customSkins\":[{\"file\":\"slot.png\"}]}]}");
        check(Arrays.equals(AccountHeads.launcherSkin(root,"NightBat"),png(skin)),"offline uses launcher's selected skin");
        check(AccountHeads.launcherSkin(root,"OtherPlayer")==null,"local skins do not leak across account names");
        Files.delete(root.resolve("skins/selected.png"));
        check(Arrays.equals(AccountHeads.launcherSkin(root,"NightBat"),png(legacy)),"missing selected skin uses populated launcher slot");
        Files.write(root.resolve("outside.png"),png(skin));
        Files.writeString(root.resolve("profiles.config.json"),"{\"savedAccounts\":[{\"username\":\"NightBat\",\"skinFile\":\"../outside.png\"}]}");
        check(AccountHeads.launcherSkin(root,"NightBat")==null,"skin paths stay inside launcher skin directory");
        var cracked=new Session("NightBat",AccountHeads.offlineUuid("NightBat"),"bat_session_pass",Optional.empty(),Optional.empty(),Session.AccountType.MSA);
        check(!AccountHeads.premium(cracked),"launcher offline accounts marked MSA are still classified as offline");
        var premium=new Session("PremiumBat",UUID.randomUUID(),"real-token-fixture",Optional.empty(),Optional.empty(),Session.AccountType.MSA);
        check(AccountHeads.premium(premium),"premium identity uses official source");
        if(Boolean.getBoolean("bat.skin.live")) {
            var fetch=AccountHeads.class.getDeclaredMethod("mojang",UUID.class);fetch.setAccessible(true);
            byte[] official=(byte[])fetch.invoke(null,UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"));
            var officialFace=decode(AccountHeads.face(official));
            check(officialFace.getWidth()==8,"live official Minecraft profile/texture produces head");
            ImageIO.write(officialFace,"png",root.resolve("live-premium-head.png").toFile());
            Path launcher=Path.of(System.getenv("APPDATA"),"bat-client");
            var config=com.google.gson.JsonParser.parseString(Files.readString(launcher.resolve("profiles.config.json"))).getAsJsonObject();
            boolean localFound=false;
            for(var item:config.getAsJsonArray("savedAccounts")) {
                var account=item.getAsJsonObject();if(!account.has("username"))continue;
                byte[] local=AccountHeads.launcherSkin(launcher,account.get("username").getAsString());
                if(local!=null){ImageIO.write(decode(AccountHeads.face(local)),"png",root.resolve("live-offline-head.png").toFile());localFound=true;break;}
            }
            check(localFound,"real launcher skin produces head without changing launcher data");
            System.out.println("LIVE_OFFICIAL_AND_LAUNCHER_SKIN_HEADS_OK");
        }
        System.out.println("SKIN_HEADS_OK: "+checks+" checks");
    }
}
