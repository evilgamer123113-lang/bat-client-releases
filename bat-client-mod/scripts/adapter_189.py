"""1.8.9 / 1.8 Legacy Fabric & Forge API adapter."""
import re


def adapt(name: str, value: str) -> str:
    value = value.replace('import com.mojang.blaze3d.systems.RenderSystem;', 'import com.mojang.blaze3d.platform.GlStateManager;')
    value = value.replace('com.mojang.blaze3d.systems.RenderSystem.', 'com.mojang.blaze3d.platform.GlStateManager.')
    value = value.replace('RenderSystem.', 'GlStateManager.')
    value = value.replace('import org.lwjgl.glfw.GLFW;', 'import com.batclient.platform.mc189.GLFW;')
    value = value.replace('org.lwjgl.glfw.GLFW.', 'com.batclient.platform.mc189.GLFW.')
    value = value.replace('net.minecraft.client.util.math.MatrixStack', 'com.batclient.platform.mc189.MatrixStack')
    value = value.replace('net.minecraft.screen.slot.Slot', 'net.minecraft.inventory.slot.Slot')
    value = value.replace('net.minecraft.client.model.ModelPart', 'net.minecraft.client.render.model.ModelPart')
    value = value.replace('PlayerEntityModel<AbstractClientPlayerEntity>', 'PlayerEntityModel')
    value = value.replace('PlayerEntityModel<net.minecraft.client.network.AbstractClientPlayerEntity>', 'PlayerEntityModel')
    value = value.replace('FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel>', 'FeatureRenderer<AbstractClientPlayerEntity>')
    value = value.replace('FeatureRenderer<net.minecraft.client.network.AbstractClientPlayerEntity, net.minecraft.client.render.entity.model.PlayerEntityModel>', 'FeatureRenderer<net.minecraft.client.network.AbstractClientPlayerEntity>')
    value = value.replace('ChatHudLine<OrderedText>', 'ChatHudLine')
    value = value.replace('ChatHudLine<Text>', 'ChatHudLine')
    value = value.replace('Text.literal(', 'new net.minecraft.text.LiteralText(')
    value = value.replace('Text.translatable(', 'new net.minecraft.text.TranslatableText(')
    value = value.replace('Text.empty()', 'new net.minecraft.text.LiteralText("")')
    value = value.replace('.openScreen(', '.setScreen(')
    value = value.replace('.getConnection()', '.getClientConnection()')
    value = value.replace('options.maxFps', 'options.maxFramerate')
    value = value.replace('options.getMaxFps().getValue()', 'options.maxFramerate')
    value = value.replace('.getName().getString()', '.getName().asUnformattedString()')
    value = value.replace('.getWindow().getFramebufferWidth()', '.width')
    value = value.replace('.getWindow().getFramebufferHeight()', '.height')
    value = value.replace('.isDead()', '.getHealth() <= 0f')
    value = value.replace('.getArmor()', '.getArmorProtectionValue()')
    value = value.replace('.getAbsorptionAmount()', '.getAbsorption()')
    value = value.replace('.getSaveProperties().getLevelName()', '.getLevelName()')
    value = value.replace('InputUtil.Type.KEYSYM,', '')
    value = value.replace('net.minecraft.client.util.InputUtil.Type.KEYSYM,', '')
    value = value.replace('client.getWindow().getHandle()', '0L')
    value = value.replace('MinecraftClient.getInstance().getWindow().getHandle()', '0L')
    value = value.replace('textRenderer.getWidth(', 'textRenderer.getStringWidth(')
    value = value.replace('tr.getWidth(', 'tr.getStringWidth(')
    value = value.replace('font.getWidth(', 'font.getStringWidth(')
    value = value.replace('client.execute(', 'client.submit(')
    value = value.replace('mc.execute(', 'mc.submit(')
    value = value.replace('client.isWindowFocused()', 'org.lwjgl.opengl.Display.isActive()')
    value = value.replace('client.options.keysAll', 'client.options.allKeys')
    value = value.replace('client.world.getPlayers()', 'client.world.playerEntities')
    value = value.replace('peers.add(player.getUuid().toString())', 'peers.add(new com.google.gson.JsonPrimitive(player.getUuid().toString()))')
    value = value.replace('client.options.keyForward', 'client.options.forwardKey')
    value = value.replace('client.options.keySneak', 'client.options.sneakKey')
    value = value.replace('client.options.keySprint', 'client.options.sprintKey')
    value = value.replace('client.options.keyAttack', 'client.options.attackKey')
    value = value.replace('client.options.keyUse', 'client.options.useKey')

    if name.endswith('VisualParitySmoke.java'):
        value = value.replace('net.minecraft.client.gui.screen.option.OptionsScreen', 'net.minecraft.client.gui.screen.SettingsScreen')
        value = value.replace('new GameMenuScreen(true)', 'new GameMenuScreen()')
        value = value.replace('client.getOverlay() != null || ', '')
        value = value.replace('if (client.getOverlay() != null) return;', '')
        value = value.replace('client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);', '')
        value = value.replace('client.currentScreen.getTitle().getString()', 'client.currentScreen.getClass().getSimpleName()')
        value = re.sub(
            r'var info = new net\.minecraft\.world\.level\.LevelInfo\(.*?\);\s*var drm = .*?;\s*client\.createWorld\(.*?\);',
            'net.minecraft.world.level.LevelInfo info = new net.minecraft.world.level.LevelInfo(0L, net.minecraft.world.level.LevelInfo.GameMode.CREATIVE, true, false, net.minecraft.world.level.LevelGeneratorType.FLAT);\n        client.startIntegratedServer("VisualParity-" + System.currentTimeMillis(), "Visual parity", info);',
            value,
            flags=re.S,
        )
        value = re.sub(
            r'save\(client,\s*file,\s*Screenshot(?:Utils|Recorder)\.takeScreenshot\(.*?\)\);',
            'try { com.batclient.platform.mc189.FramebufferCapture.save(client, file); } finally { pending--; }',
            value,
            flags=re.S,
        )
        value = value.replace('image.writeFile(file);', '')
        value = value.replace('image.writeTo(file);', '')
        value = value.replace('import net.minecraft.client.util.ScreenshotUtils;', '')
        value = value.replace('try (image) {', 'try {')
        value = value.replace('net.minecraft.client.texture.NativeImage', 'Object')
        value = value.replace('try (Object _img = image) {\n            image.writeFile(file);\n        } catch (Exception failure) {\n            finish(client, "FAILED: " + failure);\n        } finally {\n            pending--;\n        }',
                              'pending--;')
        value = value.replace('try (image) {\n            image.writeTo(file);\n        } catch (Exception failure) {\n            finish(client, "FAILED: " + failure);\n        } finally {\n            pending--;\n        }',
                              'pending--;')
        value = value.replace('try (Object _ignored = null) { image.writeTo(file); }', '')

    if name.endswith('ClientAccounts.java'):
        value = value.replace('import com.mojang.authlib.minecraft.SocialInteractionsService;\n', '')
        value = value.replace('new AccountSession(session,new com.mojang.authlib.minecraft.OfflineSocialInteractions(),new GameProfile(uuid,name))',
                              'new AccountSession(session,new GameProfile(uuid,name))')
        value = value.replace('SocialInteractionsService api=new YggdrasilAuthenticationService(Proxy.NO_PROXY).createSocialInteractionsService(identity.token());', '')
        value = value.replace('new AccountSession(session,api,profile)', 'new AccountSession(session,profile)')
        value = value.replace('new AccountSession(session,api,result)', 'new AccountSession(session,result)')
        value = value.replace('names.add(e.name)', 'names.add(new com.google.gson.JsonPrimitive(e.name))')
        value = value.replace('Util.getOperatingSystem().open(', 'java.awt.Desktop.getDesktop().browse(')

    if name.endswith('MicrosoftLogin.java'):
        value = value.replace('.getAsJsonArray("UserTokens").add(required(xbl,"Token"))',
                              '.getAsJsonArray("UserTokens").add(new com.google.gson.JsonPrimitive(required(xbl,"Token")))')

    if name.endswith('BatKeybinds.java'):
        value = value.replace('.getTranslationKey()).getString()', '.getTranslationKey()).asUnformattedString()')

    if name.endswith('ClientMods.java'):
        value = value.replace('import net.minecraft.client.option.Perspective;\n', '')
        value = value.replace('import net.minecraft.entity.effect.StatusEffects;\n', 'import net.minecraft.entity.effect.StatusEffect;\n')
        value = value.replace('client.player.getStatusEffect(StatusEffects.NIGHT_VISION)', 'client.player.getEffectInstance(StatusEffect.NIGHTVISION)')
        value = value.replace('new StatusEffectInstance(\n\t\t\t\t\t\tStatusEffects.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false, false)',
                              'new StatusEffectInstance(StatusEffect.NIGHTVISION.id, Integer.MAX_VALUE, 0, false, false)')
        value = value.replace('client.player.removeStatusEffect(StatusEffects.NIGHT_VISION)', 'client.player.removeEffect(StatusEffect.NIGHTVISION.id)')
        value = value.replace('player.isFallFlying()', 'false')
        value = value.replace('player.isSwimming()', 'player.isTouchingWater()')
        value = value.replace('client.options.setPerspective(client.options.getPerspective().next());', 'client.options.perspective = (client.options.perspective + 1) % 3;')

    if name.endswith('DeathCoordinates.java'):
        value = value.replace('c.getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT).toAbsolutePath().normalize()', 'c.getServer().getLevelName()')
        value = value.replace('c.player.isDead()', '(c.player.removed || c.player.getHealth() <= 0)')
        value = value.replace('c.world.getRegistryKey().getValue().toString()', 'String.valueOf(c.world.dimension.getType())')

    if name.endswith('BatClientMod.java'):
        value = value.replace('import net.minecraft.client.gui.screen.SplashScreen;\n', '')
        value = value.replace('ClientResourceServices.get().shaders(BatClientShaders::registerAll);\n', '')
        value = value.replace('if (Boolean.getBoolean("batclient.test.multiplayer")', 'if (false && Boolean.getBoolean("batclient.test.multiplayer")')
        s_idx = value.find('if (false && Boolean.getBoolean("batclient.test.multiplayer")')
        if s_idx != -1:
            e_idx = value.find('\t}\n\n\tprivate static void tick', s_idx)
            if e_idx != -1:
                value = value[:s_idx] + '// dev multiplayer smoke omitted on 1.8.9\n' + value[e_idx:]

    if name.endswith('HudConfig.java'):
        value = value.replace('map.size()', 'map.entrySet().size()')
        value = value.replace('profiles.size()', 'profiles.entrySet().size()')

    if name.endswith('TargetHud.java'):
        value = value.replace('import net.minecraft.util.hit.EntityHitResult;\n', '')
        value = value.replace('client.crosshairTarget instanceof EntityHitResult hit&&hit.getEntity() instanceof LivingEntity living?living:null', 'client.result != null && client.result.entity instanceof LivingEntity ? (LivingEntity) client.result.entity : null')
        value = value.replace('client.crosshairTarget instanceof EntityHitResult', 'client.result != null && client.result.entity != null')
        value = value.replace('((EntityHitResult) (Object) (client.crosshairTarget)).getEntity()', 'client.result.entity')

    if name.endswith('TravelHud.java'):
        value = value.replace('import net.minecraft.entity.EquipmentSlot;\n', '')

    if name.endswith('ServerLocation.java'):
        value = value.replace('import net.minecraft.client.network.ServerAddress;\n', '')
        value = value.replace('import net.minecraft.client.texture.NativeImage;\n', '')

    return value
