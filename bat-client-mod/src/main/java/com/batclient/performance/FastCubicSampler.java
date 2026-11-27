package com.batclient.performance;

import net.minecraft.util.CubicSampler;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Vanilla's exact 6x6x6 kernel without 432 intermediate multiply/add Vec3d objects. */
public final class FastCubicSampler {
    private static final double[] CURVE={0,1,4,6,4,1,0};
    private FastCubicSampler() { }
    public static Vec3d sample(Vec3d position,CubicSampler.RgbFetcher fetcher){
        int ix=MathHelper.floor(position.x),iy=MathHelper.floor(position.y),iz=MathHelper.floor(position.z);
        double fx=position.x-ix,fy=position.y-iy,fz=position.z-iz;
        double total=0,red=0,green=0,blue=0;
        for(int x=0;x<6;x++){
            double wx=MathHelper.lerp(fx,CURVE[x+1],CURVE[x]);
            for(int y=0;y<6;y++){
                double wy=MathHelper.lerp(fy,CURVE[y+1],CURVE[y]);
                for(int z=0;z<6;z++){
                    double wz=MathHelper.lerp(fz,CURVE[z+1],CURVE[z]);
                    double weight=wx*wy*wz;
                    total+=weight;
                    Vec3d color=fetcher.fetch(ix-2+x,iy-2+y,iz-2+z);
                    red+=color.x*weight;green+=color.y*weight;blue+=color.z*weight;
                }
            }
        }
        double reciprocal=1/total;
        return new Vec3d(red*reciprocal,green*reciprocal,blue*reciprocal);
    }
}
