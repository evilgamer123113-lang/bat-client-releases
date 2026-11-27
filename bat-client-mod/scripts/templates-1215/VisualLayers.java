package com.batclient;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.*;
import net.minecraft.client.render.*;
import net.minecraft.util.Identifier;
import java.util.*;

/** Version-specific native pipelines; bounded line variants and explicit depth semantics. */
public final class VisualLayers extends RenderLayer {
 private static final Map<Integer,RenderLayer> LINES_CACHE=new HashMap<>();
 private static final RenderLayer PLATE=plate(true),PLATE_DEPTH=plate(false);
 private final RenderPipeline pipeline;private final float width;
 private VisualLayers(String name,RenderPipeline pipeline,float width){super(name,4096,false,true,()->{},()->{});this.pipeline=pipeline;this.width=width;}
 private static RenderPipeline.Builder copy(String name,RenderPipeline source){
  var b=RenderPipeline.builder().withLocation(Identifier.of("batclient",name)).withVertexShader(source.getVertexShader()).withFragmentShader(source.getFragmentShader())
   .withVertexFormat(source.getVertexFormat(),source.getVertexFormatMode()).withDepthTestFunction(source.getDepthTestFunction())
   .withDepthWrite(false).withColorWrite(true,true).withCull(false).withBlend(BlendFunction.TRANSLUCENT);
  source.getUniforms().forEach(u->b.withUniform(u.name(),u.type()));source.getSamplers().forEach(b::withSampler);return b;
 }
 private static RenderLayer plate(boolean through){var name="waypoint_plate_"+through;var b=copy(name,RenderPipelines.GUI);
  b.withDepthTestFunction(through?com.mojang.blaze3d.platform.DepthTestFunction.NO_DEPTH_TEST:RenderPipelines.LINES.getDepthTestFunction());return new VisualLayers(name,b.build(),1);}
 public static RenderLayer waypointPlate(boolean through){return through?PLATE:PLATE_DEPTH;}
 public static RenderLayer lines(float width){int key=Math.clamp(Math.round(width*2),2,8);return LINES_CACHE.computeIfAbsent(key,k->new VisualLayers("bat_lines_"+k,copy("lines_"+k,RenderPipelines.LINES).build(),k/2f));}
 @Override public Framebuffer getTarget(){return MinecraftClient.getInstance().getFramebuffer();}
 @Override public RenderPipeline getPipeline(){return pipeline;}
 @Override public VertexFormat getVertexFormat(){return pipeline.getVertexFormat();}
 @Override public VertexFormat.DrawMode getDrawMode(){return pipeline.getVertexFormatMode();}
 @Override public void draw(BuiltBuffer built){
  float oldWidth=RenderSystem.getShaderLineWidth();
  try(built){
   var params=built.getDrawParameters();var format=params.format();var vertices=format.uploadImmediateVertexBuffer(built.getBuffer());
   var sequential=RenderSystem.getSequentialBuffer(params.mode());
   var indices=built.getSortedBuffer()!=null?format.uploadImmediateIndexBuffer(built.getSortedBuffer()):sequential.getIndexBuffer(params.indexCount());
   var indexType=built.getSortedBuffer()!=null?params.indexType():sequential.getIndexType();
   var client=MinecraftClient.getInstance();var device=RenderSystem.getDevice();
   if(!(device.precompilePipeline(pipeline) instanceof CompiledShaderPipeline compiled)||!compiled.isValid())throw new IllegalStateException("Bat world pipeline failed: "+pipeline);
   RenderSystem.lineWidth(width);compiled.program().initializeUniforms(params.mode(),RenderSystem.getModelViewMatrix(),RenderSystem.getProjectionMatrix(),client.getWindow().getFramebufferWidth(),client.getWindow().getFramebufferHeight());
   var fb=client.getFramebuffer();
   try(var pass=device.createCommandEncoder().createRenderPass(fb.getColorAttachment(),OptionalInt.empty(),fb.getDepthAttachment(),OptionalDouble.empty())){
    pass.setPipeline(pipeline);if(RenderSystem.SCISSOR_STATE.isEnabled())pass.enableScissor(RenderSystem.SCISSOR_STATE);
    pass.setVertexBuffer(0,vertices);pass.setIndexBuffer(indices,indexType);pass.drawIndexed(0,params.indexCount());
   }
  }finally{RenderSystem.lineWidth(oldWidth);}
 }
}
