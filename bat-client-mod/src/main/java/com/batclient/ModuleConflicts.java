package com.batclient;
import com.google.gson.JsonObject;
import com.batclient.platform.Platforms;
public final class ModuleConflicts {
    private ModuleConflicts(){}
    public static String json(){var out=new JsonObject();var loader=Platforms.get();
        String[][] rules={{"freelook","freelook","Another Freelook mod is installed. Choose one camera implementation if their keys or views conflict."},{"lootbeams","lootbeams","Another Loot Beams mod is installed. Disable one if effects duplicate."},{"skinlayers3d","skinlayers3d","Standalone 3D Skin Layers detected. Bat's extra skin layer yields to it."},{"shulkerpreview","shulkerboxtooltip","Shulker Box Tooltip detected. Both can add container previews; disable one if previews duplicate."},{"foodpreview","appleskin","AppleSkin detected. Food information may overlap."},{"minimap","xaerominimap","Xaero's Minimap detected. Both map widgets may overlap."},{"minimap","voxelmap","VoxelMap detected. Both map widgets may overlap."}};
        for(var r:rules)if(loader.isModLoaded(r[1]))out.addProperty(r[0],out.has(r[0])?out.get(r[0]).getAsString()+" "+r[2]:r[2]);return out.toString();}
}
