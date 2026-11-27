package com.batclient.api.waypoints;

import org.bukkit.Bukkit;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
import org.bukkit.command.*;

public final class BatWaypointsPlugin extends JavaPlugin implements Listener {
    private WaypointService waypoints;
    @Override public void onEnable(){waypoints=new WaypointService(this);getServer().getMessenger().registerOutgoingPluginChannel(this,WaypointService.CHANNEL);getServer().getServicesManager().register(WaypointService.class,waypoints,this,ServicePriority.Normal);getServer().getPluginManager().registerEvents(this,this);getServer().getScheduler().runTaskTimer(this,waypoints::expire,100,100);}
    @Override public void onDisable(){if(waypoints!=null)for(var p:Bukkit.getOnlinePlayers())waypoints.destroyAllWaypoints(p);getServer().getServicesManager().unregisterAll(this);getServer().getMessenger().unregisterOutgoingPluginChannel(this);}
    public WaypointService getWaypointService(){return waypoints;}
    private void sync(Player player){Bukkit.getScheduler().runTaskLater(this,()->waypoints.synchronize(player),20);}
    @EventHandler public void join(PlayerJoinEvent event){sync(event.getPlayer());}
    @EventHandler public void world(PlayerChangedWorldEvent event){sync(event.getPlayer());}
    @EventHandler public void respawn(PlayerRespawnEvent event){sync(event.getPlayer());}
    @EventHandler public void channel(PlayerRegisterChannelEvent event){if(event.getChannel().equals(WaypointService.CHANNEL))sync(event.getPlayer());}
    @EventHandler public void quit(PlayerQuitEvent event){waypoints.quit(event.getPlayer());}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){if(!(sender instanceof Player player)){sender.sendMessage("Run this command in-game.");return true;}if(args.length==1&&args[0].equalsIgnoreCase("clear")){waypoints.destroyAllWaypoints(player);return true;}if(args.length>=2&&args[0].equalsIgnoreCase("create")){var pos=player.getLocation();String name=String.join(" ",java.util.Arrays.copyOfRange(args,1,args.length));try{waypoints.createWaypoint(player,waypoints.createWaypointBuilder(pos.getBlockX(),pos.getBlockY(),pos.getBlockZ()).withName(name).withColor(117,207,255));sender.sendMessage("Bat Client waypoint created.");}catch(IllegalArgumentException|IllegalStateException e){sender.sendMessage(e.getMessage());}return true;}return false;}
}
