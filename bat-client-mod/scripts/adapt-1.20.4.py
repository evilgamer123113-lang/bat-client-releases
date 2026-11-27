"""Generate explicit 1.20.4 API adapters without changing the tested1.21.1 sources.

Work-in-progress: compile and runtime gates expose remaining 1.20.4 API gaps.
No classes or methods are stubbed; this file does not certify compatibility.
Generated Java remains private under the selected target's build/version-sources.
"""
import argparse
import json
from pathlib import Path
import re
from adapter_1204 import adapt as adapt_1204

parser=argparse.ArgumentParser()
parser.add_argument('--loader',choices=['fabric','forge'],required=True)
parser.add_argument('--project',required=True)
parser.add_argument('--minecraft',choices=['1.20.4','1.20.1','1.19.4','1.19.2','1.18.2','1.17.1','1.16.5','1.8.9','1.8'],default='1.20.4')
args=parser.parse_args()
root=Path(__file__).resolve().parent.parent
project=Path(args.project).resolve()
if project.parent!=root/'ports' or project.name!=args.loader+'-'+args.minecraft:
    raise SystemExit('Only exact internal1.20.4 target paths are supported')
output=project/'build/version-sources'
roots=[root/'src/main/java',project/'src/main/java',root/'ports/mc1.20.6/src',root/'ports/mc1.20.4/src']
if args.minecraft in ('1.20.1','1.19.4','1.19.2','1.18.2','1.17.1','1.16.5','1.8.9','1.8'):
    roots.append(root/'ports/mc1.20.1/src')
if args.minecraft in ('1.19.4','1.19.2','1.18.2','1.17.1','1.16.5','1.8.9','1.8'):
    roots.append(root/'ports/mc1.19.4/src')
if args.minecraft in ('1.19.2','1.18.2','1.17.1','1.16.5','1.8.9','1.8') and (root/'ports/mc1.19.2/src').is_dir():
    roots.append(root/'ports/mc1.19.2/src')
if args.minecraft in ('1.18.2','1.17.1','1.16.5','1.8.9','1.8') and (root/'ports/mc1.18.2/src').is_dir():
    roots.append(root/'ports/mc1.18.2/src')
if args.minecraft in ('1.17.1','1.16.5','1.8.9','1.8') and (root/'ports/mc1.17.1/src').is_dir():
    roots.append(root/'ports/mc1.17.1/src')
if args.minecraft in ('1.16.5','1.8.9','1.8') and (root/'ports/mc1.16.5/src').is_dir():
    roots.append(root/'ports/mc1.16.5/src')
if args.minecraft in ('1.8.9','1.8') and (root/'ports/mc1.8.9/src').is_dir():
    roots.append(root/'ports/mc1.8.9/src')
if args.loader=='forge':
    roots.insert(1,root/'ports/forge/src/main/java')
report=[]
selected={}
for source_root in roots:
    if not source_root.exists():
        continue
    for source in source_root.rglob('*.java'):
        relative=source.relative_to(source_root)
        if args.loader=='forge' and (relative.as_posix().startswith('com/batclient/platform/fabric/') or relative.as_posix()=='com/batclient/performance/PerformanceMixinPlugin.java'):
            continue
        if relative in selected and source_root not in (root/'ports/mc1.19.2/src', root/'ports/mc1.18.2/src', root/'ports/mc1.17.1/src', root/'ports/mc1.16.5/src', root/'ports/mc1.8.9/src', project/'src/main/java'):
            raise RuntimeError('Ambiguous source override: '+str(relative))
        selected[relative]=(source_root,source)
for relative,(source_root,source) in selected.items():
    if args.minecraft in ('1.17.1','1.16.5','1.8.9','1.8') and source_root in (project/'src/main/java', root/'ports/mc1.8.9/src'):
        target=output/relative
        target.parent.mkdir(parents=True,exist_ok=True)
        txt=source.read_text(encoding='utf-8')
        if args.minecraft=='1.8' and args.loader=='fabric':
            from adapter_18 import adapt as adapt_18
            txt=adapt_18(relative.as_posix(),txt)
        target.write_text(txt,encoding='utf-8')
        continue
    if True:
        original=source.read_text(encoding='utf-8')
        value=original.replace('net.minecraft.item.tooltip.TooltipData','net.minecraft.client.item.TooltipData')
        if args.loader=='forge':
            # Forge 50's upstream Mixin 0.8.5 supports Java 17 mixins. Compile
            # real Java 17 bytecode rather than lying about Java 21 class files.
            # Minecraft itself and the Gradle toolchain still require Java 21.
            value=value.replace('Math.clamp(', 'net.minecraft.util.math.MathHelper.clamp(')
            if source.name=='HudRenderer.java':
                value=value.replace('ExtraHud.lines(c,id,mod,editorPreview).getFirst()',
                    'ExtraHud.lines(c,id,mod,editorPreview).get(0)')
            if source.name=='MouseZoomMixin.java':
                value=value.replace('import org.spongepowered.asm.mixin.injection.ModifyArgs;',
                    'import org.spongepowered.asm.mixin.injection.Redirect;')
                value=value.replace('import org.spongepowered.asm.mixin.injection.invoke.arg.Args;', '')
                value=value.replace('@ModifyArgs(method = "updateMouse"', '@Redirect(method = "updateMouse"')
                start=value.index('    private void bat$zoomSensitivity(')
                end=value.index('    @Inject(method = "onMouseScroll"', start)
                value=value[:start]+'''    private void bat$zoomSensitivity(net.minecraft.client.network.ClientPlayerEntity player, double x, double y) {
        double scale=ClientMods.zoomMouseScale();
        if(com.batclient.Freelook.mouse(x*scale,y*scale)) return;
        player.changeLookDirection(x*scale,y*scale);
    }
'''+value[end:]
            if source.name=='FreelookCameraMixin.java':
                value=value.replace('import org.spongepowered.asm.mixin.injection.ModifyArgs;',
                    'import org.spongepowered.asm.mixin.injection.Redirect;\nimport org.spongepowered.asm.mixin.Shadow;')
                value=value.replace('import org.spongepowered.asm.mixin.injection.invoke.arg.Args;', '')
                value=value.replace('@ModifyArgs(', '@Redirect(')
                value=value.replace('    private void bat$orbit(Args args){if(Freelook.active()){args.set(0,Freelook.yaw());args.set(1,Freelook.pitch());}}',
                    '''    @Shadow protected abstract void setRotation(float yaw,float pitch);
    private void bat$orbit(Camera camera,float yaw,float pitch){
        setRotation(Freelook.active()?Freelook.yaw():yaw,Freelook.active()?Freelook.pitch():pitch);
    }''')
                # The shadow is a separate method, not an injector annotation target.
                value=value.replace('    @Redirect(method="update",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/Camera;setRotation(FF)V",ordinal=0))\n    @Shadow protected abstract void setRotation(float yaw,float pitch);',
                    '    @Shadow protected abstract void setRotation(float yaw,float pitch);\n    @Redirect(method="update",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/Camera;setRotation(FF)V",ordinal=0))')
            if source.name=='NativeNametagStyleMixin.java':
                value='''package com.batclient.mixin;
import com.batclient.HudConfig;
import com.batclient.NativeNametags;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
@Mixin(EntityRenderer.class)
public abstract class NativeNametagStyleMixin {
    @Redirect(method="renderLabelIfPresent",at=@At(value="INVOKE",target="Lnet/minecraft/client/util/math/MatrixStack;scale(FFF)V"))
    private void bat$scale(MatrixStack stack,float x,float y,float z,Entity entity,Text label,MatrixStack matrices,VertexConsumerProvider vertices,int light,float delta){
        float size=entity instanceof PlayerEntity&&NativeNametags.enabled()?Math.max(.5f,Math.min(2,HudConfig.setNumber(NativeNametags.settings(),"scale",1))):1;
        stack.scale(x*size,y*size,z*size);
    }
    @Redirect(method="renderLabelIfPresent",at=@At(value="INVOKE",target="Lnet/minecraft/client/font/TextRenderer;draw(Lnet/minecraft/text/Text;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)I"))
    private int bat$textStyle(TextRenderer renderer,Text label,float x,float y,int color,boolean shadow,Matrix4f matrix,VertexConsumerProvider vertices,TextRenderer.TextLayerType layer,int background,int light,Entity entity,Text text,MatrixStack matrices,VertexConsumerProvider originalVertices,int originalLight,float delta){
        if(entity instanceof PlayerEntity&&NativeNametags.enabled()){
            shadow=HudConfig.bool(NativeNametags.settings(),"shadow",true);
            if(!HudConfig.bool(NativeNametags.settings(),"background",true))background=0;
            NativeNametags.drawLogo(label,x,y,color,matrix,vertices,layer,light);
        }
        return renderer.draw(label,x,y,color,shadow,matrix,vertices,layer,background,light);
    }
}
'''
            if source.name in ('MultiplayerExtrasMixin.java','ServerEntryExtrasMixin.java','RainCustomizerMixin.java'):
                value=value.replace('import org.spongepowered.asm.mixin.injection.ModifyArgs;',
                    'import org.spongepowered.asm.mixin.injection.ModifyArg;')
                value=value.replace('import org.spongepowered.asm.mixin.injection.invoke.arg.Args;', '')
                if source.name=='MultiplayerExtrasMixin.java':
                    value=value.replace('@ModifyArgs(', '@ModifyArg(').replace('Text;III)V"))','Text;III)V"), index=3)')
                    value=value.replace('private void bat$titleAboveSearch(Args args) { args.set(3, 8); }',
                        'private int bat$titleAboveSearch(int y) { return 8; }')
                elif source.name=='ServerEntryExtrasMixin.java':
                    value=value.replace('@ModifyArgs(', '@ModifyArg(').replace('ordinal = 0))\n    private void bat$nameSpace', 'ordinal = 0), index=2)\n    private int bat$nameSpace')
                    value=value.replace('(Args args) { args.set(2, (int)args.get(2) + 18); }', '(int x) { return x+18; }')
                else:
                    value=value.replace('@ModifyArgs(', '@ModifyArg(').replace('VertexConsumer;"))', 'VertexConsumer;"),index=3)')
                    value=value.replace('private void bat$opacity(Args args){args.set(3,(Float)args.get(3)*(bat$snow?bat$snowAlpha:bat$rainAlpha));}',
                        'private float bat$opacity(float alpha){return alpha*(bat$snow?bat$snowAlpha:bat$rainAlpha);}')
        if source.name=='ServerEntryExtrasMixin.java':
            value=value.replace('value = "INVOKE", target = "Lnet/minecraft/client/network/ServerInfo;getStatus()Lnet/minecraft/client/network/ServerInfo$Status;", ordinal = 0',
                'value = "FIELD", target = "Lnet/minecraft/client/network/ServerInfo;online:Z", ordinal = 0')
        value=value.replace('net/minecraft/item/tooltip/TooltipData','net/minecraft/client/item/TooltipData')
        value=value.replace('net.minecraft.entity.player.PlayerModelPart','net.minecraft.client.render.entity.PlayerModelPart')
        value=value.replace('Identifier.ofVanilla(','Identifier.of("minecraft",')
        value=value.replace('.getEnchantmentEntries()','.getEnchantmentsMap()')
        value=value.replace('Enchantment.getName(entry.getKey(),entry.getIntValue())','entry.getKey().value().getName(entry.getIntValue())')
        value=value.replace('dye.getEntityColor()','com.batclient.platform.mc1206.DyeColors.rgb(dye)')
        if source.name=='ServerEntryExtrasMixin.java' and args.loader=='forge':
            # Forge50 ships an older Mixin API without injector order(). Runtime
            # favorite/collapse click ordering still requires its own regression.
            value=value.replace(', order = 1100','')
        if source.name=='WebWindow.java':
            value=value.replace('Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);',
                'Tessellator.getInstance().getBuffer();\n        builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);')
        if source.name=='HitColorMixin.java':
            value=value.replace('setupOverlayColor(bat$hitTexture.getGlId(),16)','setupOverlayColor(()->bat$hitTexture.getGlId(),16)')
        if source.name=='FabricClientHooks.java':
            value=value.replace('source.tickCounter().getTickDelta(false)','source.tickDelta()')
        if source.name=='ForgeHudMixin.java':
            value=value.replace('RenderTickCounter tickCounter','float tickCounter')
        if source.name=='MotionBlurMixin.java':
            value=value.replace('RenderTickCounter counter, boolean tick','float counter, long frameTime, boolean tick')
            value=value.replace('RenderTickCounter counter, CallbackInfo','float counter, long frameTime, CallbackInfo')
        if source.name=='ServerHudMixin.java':
            value=value.replace('Lnet/minecraft/client/render/RenderTickCounter;)V','F)V').replace('RenderTickCounter ticks','float ticks')
        # 1.20.4 VertexConsumer does not implicitly finish a vertex. Every actual
        # custom geometry chain must call next(), including text/hearts and lines.
        vertices=0
        def finish(match):
            global vertices
            vertices+=1
            return match.group(1)+'.next();'
        value=re.sub(r'(\b\w+\.vertex\([^;]+?\))\s*;',finish,value)
        # 1.20.4 predates item data components. Read real legacy item/NBT
        # APIs, preserving names, enchantments, container contents and counts.
        value=value.replace('Math.clamp(', 'net.minecraft.util.math.MathHelper.clamp(')
        value=value.replace('ExtraHud.lines(c,id,mod,editorPreview).getFirst()', 'ExtraHud.lines(c,id,mod,editorPreview).get(0)')
        if source.name=='AdventureHud.java':
            value=value.replace('import net.minecraft.component.DataComponentTypes;', '')
            value=value.replace('main.contains(DataComponentTypes.FOOD)', 'main.isFood()').replace('off.contains(DataComponentTypes.FOOD)', 'off.isFood()')
            value=value.replace('.get(DataComponentTypes.FOOD)', '.getItem().getFoodComponent()')
            value=value.replace('food.nutrition()', 'food.getHunger()').replace('component.nutrition()', 'component.getHunger()')
            value=value.replace('food.saturation()', '(food.getHunger()*food.getSaturationModifier()*2)')
        if source.name=='ExtraHud.java':
            value=value.replace('import net.minecraft.component.DataComponentTypes;', '').replace('import net.minecraft.component.type.ItemEnchantmentsComponent;', '')
            value=value.replace('stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT).getEnchantmentsMap()', 'net.minecraft.enchantment.EnchantmentHelper.get(stack).entrySet()')
            value=value.replace('entry.getKey().value().getName(entry.getIntValue())', 'entry.getKey().getName(entry.getValue())')
        if source.name=='ItemPreviews.java':
            value=value.replace('import net.minecraft.component.DataComponentTypes;', '')
            value=value.replace('stack.get(DataComponentTypes.CONTAINER)', 'stack.getSubNbt("BlockEntityTag")')
            value=value.replace('container.copyTo(items)', 'net.minecraft.inventory.Inventories.readNbt(container,items)')
        if source.name=='ItemPickupTracker.java':
            value=value.replace('import net.minecraft.component.ComponentMap;', 'import net.minecraft.nbt.NbtCompound;')
            value=value.replace('ComponentMap components', 'NbtCompound components')
            value=value.replace('normalized.getComponents()', 'normalized.getNbt()==null?null:normalized.getNbt().copy()')
        value=adapt_1204(relative.as_posix(),value)
        if args.minecraft in ('1.20.1','1.19.4','1.19.2','1.18.2','1.17.1','1.16.5','1.8.9','1.8'):
            from adapter_1201 import adapt as adapt_1201
            value=adapt_1201(relative.as_posix(),value)
        if args.minecraft in ('1.19.4','1.19.2','1.18.2','1.17.1','1.16.5','1.8.9','1.8'):
            from adapter_1194 import adapt as adapt_1194
            value=adapt_1194(relative.as_posix(),value)
        if source_root in (root/'ports/mc1.20.4/src',root/'ports/mc1.19.4/src'):value=original;vertices=0
        if args.minecraft in ('1.19.2','1.18.2','1.17.1','1.16.5','1.8.9','1.8'):
            from adapter_1192 import adapt as adapt_1192
            value=adapt_1192(relative.as_posix(),value)
            if args.loader=='forge' and args.minecraft=='1.19.2' and relative.as_posix().endswith('InventoryNametagMixin.java'):
                value=value.replace('method_29977','lambda$renderEntityInInventoryRaw$1')
        if source_root==root/'ports/mc1.19.2/src':value=original;vertices=0
        if args.minecraft in ('1.18.2','1.17.1','1.16.5','1.8.9','1.8'):
            from adapter_1182 import adapt as adapt_1182
            value=adapt_1182(relative.as_posix(),value)
        if source_root==root/'ports/mc1.18.2/src':value=original;vertices=0
        if args.minecraft in ('1.17.1','1.16.5','1.8.9','1.8'):
            from adapter_1171 import adapt as adapt_1171
            value=adapt_1171(relative.as_posix(),value)
        if source_root==root/'ports/mc1.17.1/src':value=original;vertices=0
        if args.minecraft in ('1.16.5','1.8.9','1.8'):
            from adapter_1165 import adapt as adapt_1165
            value=adapt_1165(relative.as_posix(),value)
        if source_root==root/'ports/mc1.16.5/src':value=original;vertices=0
        if args.minecraft in ('1.8.9','1.8'):
            from adapter_189 import adapt as adapt_189
            value=adapt_189(relative.as_posix(),value)
        if args.minecraft == '1.8' and args.loader == 'fabric':
            from adapter_18 import adapt as adapt_18
            value=adapt_18(relative.as_posix(),value)
        if source_root==root/'ports/mc1.8.9/src':value=original;vertices=0
        target=output/relative
        target.parent.mkdir(parents=True,exist_ok=True)
        target.write_text(value,encoding='utf-8')
        if value!=original:
            report.append({'file':relative.as_posix(),'finishedVertices':vertices})
visual = (root/'scripts/VisualParitySmoke.java').read_text(encoding='utf-8')
visual = adapt_1204('VisualParitySmoke.java', visual)
if args.minecraft in ('1.20.1','1.19.4','1.19.2','1.18.2','1.17.1','1.16.5','1.8.9','1.8'):
    from adapter_1201 import adapt as adapt_1201
    visual = adapt_1201('VisualParitySmoke.java', visual)
if args.minecraft in ('1.19.4','1.19.2','1.18.2','1.17.1','1.16.5','1.8.9','1.8'):
    from adapter_1194 import adapt as adapt_1194
    visual = adapt_1194('VisualParitySmoke.java', visual)
if args.minecraft in ('1.19.2','1.18.2','1.17.1','1.16.5','1.8.9','1.8'):
    from adapter_1192 import adapt as adapt_1192
    visual = adapt_1192('VisualParitySmoke.java', visual)
if args.minecraft in ('1.18.2','1.17.1','1.16.5','1.8.9','1.8'):
    from adapter_1182 import adapt as adapt_1182
    visual = adapt_1182('VisualParitySmoke.java', visual)
if args.minecraft in ('1.17.1','1.16.5','1.8.9','1.8'):
    from adapter_1171 import adapt as adapt_1171
    visual = adapt_1171('VisualParitySmoke.java', visual)
if args.minecraft in ('1.16.5','1.8.9','1.8'):
    from adapter_1165 import adapt as adapt_1165
    visual = adapt_1165('VisualParitySmoke.java', visual)
if args.minecraft in ('1.8.9','1.8'):
    from adapter_189 import adapt as adapt_189
    visual = adapt_189('VisualParitySmoke.java', visual)
if args.minecraft == '1.8' and args.loader == 'fabric':
    from adapter_18 import adapt as adapt_18
    visual = adapt_18('VisualParitySmoke.java', visual)
if args.loader == 'forge' or args.minecraft in ('1.17.1','1.16.5','1.8.9','1.8'):
    visual = visual.replace('import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;', '')
    visual = visual.replace('ClientTickEvents.END_CLIENT_TICK.register(this::tick);', 'com.batclient.platform.ClientHookServices.get().endTick(this::tick);')
if args.loader == 'forge':
    visual = visual.replace('package com.batclient.platform.fabric;', 'package com.batclient.platform.forge;')
    visual = visual.replace('import net.fabricmc.api.ClientModInitializer;', '')
    visual = visual.replace(' implements ClientModInitializer', '').replace('@Override public void onInitializeClient()', 'public void onInitializeClient()')
fixture = output/'com/batclient/platform'/args.loader
(fixture/'VisualParitySmoke.java').write_text(visual, encoding='utf-8')
smoke_name = ('Fabric'+args.minecraft.replace('.','')+'Smoke.java') if args.loader == 'fabric' else 'ForgeFullSmoke.java'
entry = fixture/smoke_name
code = entry.read_text(encoding='utf-8')
method = 'public void onInitializeClient(){' if args.loader == 'fabric' else 'static void install(){'
assert code.count(method) == 1
entry.write_text(code.replace(method, method+'\n        if (Boolean.getBoolean("batclient.visualParity")) { new VisualParitySmoke().onInitializeClient(); return; }'), encoding='utf-8')
(project/'build/api-adaptation-report.json').write_text(json.dumps(report,indent=2)+'\n')
print('MC'+args.minecraft.replace('.','')+'_REAL_API_ADAPTERS_GENERATED:',len(selected),'sources,',len(report),'adapted; runtime verification still required')
