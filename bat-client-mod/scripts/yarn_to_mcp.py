"""Translate Legacy Fabric Yarn 1.8.9 Java/Mixin sources to MCP 1.8.9 using Tiny v2 mappings."""
from __future__ import annotations

import argparse
from collections import defaultdict
from pathlib import Path
import re
import shutil


def parse_tiny_v2(path: Path):
    classes = {}  # official -> named
    fields = {}   # (official_cls, official_name) -> named
    methods = {}  # (official_cls, official_name, official_desc) -> named
    cur_off = None
    with path.open('r', encoding='utf-8') as f:
        header = f.readline().rstrip('\r\n').split('\t')
        if len(header) < 5 or header[0] != 'tiny':
            raise ValueError(f'Unexpected tiny header in {path}: {header}')
        for raw in f:
            line = raw.rstrip('\r\n')
            if not line:
                continue
            if line.startswith('c\t'):
                parts = line.split('\t')
                cur_off = parts[1]
                named = parts[3] if len(parts) > 3 and parts[3] else parts[2]
                classes[cur_off] = named
            elif line.startswith('\tf\t') and cur_off is not None:
                parts = line.split('\t')
                # \tf\tdesc\toff\tint\tnamed
                off_name = parts[3]
                named = parts[5] if len(parts) > 5 and parts[5] else parts[4]
                fields[(cur_off, off_name)] = named
            elif line.startswith('\tm\t') and cur_off is not None:
                parts = line.split('\t')
                # \tm\tdesc\toff\tint\tnamed
                off_desc = parts[2]
                off_name = parts[3]
                named = parts[5] if len(parts) > 5 and parts[5] else parts[4]
                methods[(cur_off, off_name, off_desc)] = named
    return classes, fields, methods


def build_maps(yarn_tiny: Path, mcp_tiny: Path):
    y_cls, y_fld, y_mtd = parse_tiny_v2(yarn_tiny)
    m_cls, m_fld, m_mtd = parse_tiny_v2(mcp_tiny)

    cls_map = {}  # yarn_slash -> mcp_slash
    for off, y_name in y_cls.items():
        m_name = m_cls.get(off)
        if m_name:
            cls_map[y_name] = m_name

    # Per-class and global unambiguous member maps
    # yarn_cls_slash -> {yarn_member: set(mcp_member)}
    cls_methods = defaultdict(lambda: defaultdict(set))
    cls_fields = defaultdict(lambda: defaultdict(set))
    global_methods = defaultdict(set)
    global_fields = defaultdict(set)

    for (off_cls, off_name), y_name in y_fld.items():
        m_name = m_fld.get((off_cls, off_name))
        y_c = y_cls.get(off_cls)
        if m_name and y_c and y_name != m_name:
            cls_fields[y_c][y_name].add(m_name)
            global_fields[y_name].add(m_name)

    for (off_cls, off_name, off_desc), y_name in y_mtd.items():
        m_name = m_mtd.get((off_cls, off_name, off_desc))
        y_c = y_cls.get(off_cls)
        if m_name and y_c and y_name != m_name:
            cls_methods[y_c][y_name].add(m_name)
            global_methods[y_name].add(m_name)

    STDLIB_SKIP = {
        'copy', 'copyOf', 'next', 'accept', 'apply', 'test', 'get', 'set', 'add', 'remove',
        'clear', 'size', 'isEmpty', 'contains', 'values', 'keys', 'entries', 'put', 'run',
        'call', 'close', 'flush', 'read', 'write', 'open', 'create', 'build', 'parse',
        'format', 'encode', 'decode', 'hashCode', 'equals', 'toString', 'compareTo',
        'iterator', 'stream', 'forEach', 'empty', 'valueOf', 'name', 'type', 'path',
        'load', 'save', 'init', 'tick', 'render', 'draw', 'bind', 'unbind', 'release',
        'start', 'stop', 'reset', 'update', 'scale', 'rotate', 'translate', 'color',
        'nanoTime', 'round', 'replace', 'getOrDefault', 'number', 'removed', 'nextInt',
        'min', 'max', 'abs', 'floor', 'ceil', 'sqrt', 'clamp', 'length', 'split', 'trim',
    }
    unambig_members = {}
    for name, targets in global_methods.items():
        if len(targets) == 1 and len(name) >= 5 and name not in STDLIB_SKIP:
            unambig_members[name] = next(iter(targets))
    for name, targets in global_fields.items():
        if len(targets) == 1 and len(name) >= 5 and name not in STDLIB_SKIP:
            unambig_members[name] = next(iter(targets))

    return cls_map, cls_methods, cls_fields, unambig_members


# Explicit overrides for common 1.8.9 Minecraft names where Yarn and MCP differ or overlap
EXPLICIT_MEMBERS = {
    'getKeyCode': 'getKeyCode',
    'textRenderer': 'fontRendererObj',
    'getTextRenderer': 'getFontRendererObj',
    'textureManager': 'renderEngine',
    'getTextureManager': 'getTextureManager',
    'options': 'gameSettings',
    'inGameHud': 'ingameGUI',
    'currentScreen': 'currentScreen',
    'getWindow': 'getWindow',
    'width': 'width',
    'height': 'height',
    'displayWidth': 'displayWidth',
    'displayHeight': 'displayHeight',
    'setScreen': 'displayGuiScreen',
    'openScreen': 'displayGuiScreen',
    'isPaused': 'isGamePaused',
    'getNetworkHandler': 'getNetHandler',
    'getClientConnection': 'getNetworkManager',
    'isInSingleplayer': 'isSingleplayer',
    'getSession': 'getSession',
    'getAccessToken': 'getToken',
    'getCameraEntity': 'getRenderViewEntity',
    'getCurrentServerEntry': 'getCurrentServerData',
    'isHudEnabled': 'isGuiEnabled',
    'runDirectory': 'mcDataDir',
    'getUsedItem': 'getItemInUse',
    'getItemUseTicks': 'getItemInUseCount',
    'getHungerManager': 'getFoodStats',
    'getHungerPoints': 'getHealAmount',
    'getSaturation': 'getSaturationModifier',
    'renderGuiItemOverlay': 'renderItemOverlays',
    'renderInGuiWithOverrides': 'renderItemAndEffectIntoGUI',
    'hasAltDown': 'isAltKeyDown',
    'hasControlDown': 'isCtrlKeyDown',
    'hasShiftDown': 'isShiftKeyDown',
    'getTranslatedName': 'getLocalizedName',
    'getLevelName': 'getWorldName',
    'activeTexture': 'setActiveTexture',
    'byRawId': 'getEnchantmentById',
    'GUI_ICONS_TEXTURE': 'icons',
    'POSITION_TEXTURE': 'POSITION_TEX',
    'NIGHTVISION': 'nightVision',
    'GENERIC_MOVEMENT_SPEED': 'movementSpeed',
    'REGISTRY': 'blockRegistry',
    'AIR': 'air',
    'DIRT': 'dirt',
    'COOKED_BEEF': 'cooked_beef',
    'BOW': 'bow',
    'COMPASS': 'compass',
    'DIAMOND_SWORD': 'diamond_sword',
    'SADDLE': 'saddle',
    'allKeys': 'keyBindings',
    'attackKey': 'keyBindAttack',
    'backKey': 'keyBindBack',
    'forwardKey': 'keyBindForward',
    'hudHidden': 'hideGUI',
    'jumpKey': 'keyBindJump',
    'leftKey': 'keyBindLeft',
    'perspective': 'thirdPersonView',
    'rightKey': 'keyBindRight',
    'sneakKey': 'keyBindSneak',
    'sprintKey': 'keyBindSprint',
    'useKey': 'keyBindUseItem',
    'framesToLive': 'statusBarTime',
    'interactionManager': 'playerController',
    'trimToWidth': 'trimStringToWidth',
    'fontHeight': 'FONT_HEIGHT',
    'getStatHandler': 'getStatFileWriter',
    'getEffectId': 'getPotionID',
    'getBoundingBox': 'getEntityBoundingBox',
    'getTargetingMargin': 'getCollisionBorderSize',
    'isAlive': 'isEntityAlive',
    'getHorizontalDirection': 'getHorizontalFacing',
    'getRotationVector': 'getLook',
    'addStatusEffect': 'addPotionEffect',
    'getEffectInstance': 'getActivePotionEffect',
    'getStatusEffectInstances': 'getActivePotionEffects',
    'removeEffect': 'removePotionEffectClient',
    'initializeAttribute': 'getEntityAttribute',
    'getJumpStrength': 'getHorseJumpStrength',
    'getEntitiesIn': 'getEntitiesWithinAABBExcludingEntity',
    'getTimeOfDay': 'getWorldTime',
    'getBiome': 'getBiomeGenForCoords',
    'hasVehicle': 'isRiding',
    'isTouchingWater': 'isInWater',
    'abilities': 'capabilities',
    'forwardSpeed': 'moveForward',
    'sidewaysSpeed': 'moveStrafing',
    'asUnformattedString': 'getUnformattedText',
    'asFormattedString': 'getFormattedText',
    'addFeature': 'addLayer',
    'handleMouse': 'handleMouseInput',
    'getSelectionBox': 'getSelectedBoundingBox',
    'setBoundingBox': 'setBlockBoundsBasedOnState',
    'getPlayerTeam': 'getPlayersTeam',
    'getResourcePackLoader': 'getResourcePackRepository',
    'setOverlayMessage': 'setRecordPlaying',
    'jacket': 'bipedBodyWear',
    'leftPants': 'bipedLeftLegwear',
    'leftSleeve': 'bipedLeftArmwear',
    'rightPants': 'bipedRightLegwear',
    'rightSleeve': 'bipedRightArmwear',
    'preRender': 'postRender',
    'getGlId': 'getGlTextureId',
    'POSITION_TEXTURE_NORMAL': 'POSITION_TEX_NORMAL',
    'fuseTimer': 'fuse',
    'getMaterialColor': 'getMapColor',
    'getScoreboardTeam': 'getTeam',
    'getWorld': 'getEntityWorld',
    'isInvisibleTo': 'isInvisibleToPlayer',
    'getWaterColor': 'getWaterColorAtPos',
    'COBBLESTONE': 'cobblestone',
    'DIAMOND_BLOCK': 'diamond_block',
    'GRASS': 'grass',
    'STONE': 'stone',
    'ARROW': 'arrow',
    'DIAMOND': 'diamond',
    'DIAMOND_BOOTS': 'diamond_boots',
    'DIAMOND_CHESTPLATE': 'diamond_chestplate',
    'DIAMOND_HELMET': 'diamond_helmet',
    'DIAMOND_LEGGINGS': 'diamond_leggings',
    'EMERALD': 'emerald',
    'ENDER_PEARL': 'ender_pearl',
    'GOLDEN_APPLE': 'golden_apple',
    'IRON_SWORD': 'iron_sword',
    'SPEED': 'moveSpeed',
    'STATUS_EFFECTS': 'potionTypes',
    'STRENGTH': 'damageBoost',
    'GENERIC_ATTACK_DAMAGE': 'attackDamage',
    'POSITION_TEXTURE_COLOR': 'POSITION_TEX_COLOR',
    'getSessionProperties': 'getProfileProperties',
    'startIntegratedServer': 'launchIntegratedServer',
    'getPlayerList': 'getPlayerInfoMap',
    'getPlayerListEntry': 'getPlayerInfo',
    'getLatency': 'getResponseTime',
    'getSkinTexture': 'getLocationSkin',
    'getProfile': 'getGameProfile',
    'getScore': 'getScorePoints',
    'getAllPlayerScores': 'getSortedScores',
    'getObjectiveForSlot': 'getObjectiveInDisplaySlot',
    'getCreationTick': 'getUpdatedCounter',
    'getChannel': 'getChannelName',
    'getPayload': 'getBufferData',
    'getPixels': 'getTextureData',
    'registerDynamicTexture': 'getDynamicTextureLocation',
    'isBreakingBlock': 'getIsHittingBlock',
    'textureHeight': 'framebufferTextureHeight',
    'textureWidth': 'framebufferTextureWidth',
    'getFormatting': 'getChatFormat',
    'setFormatting': 'setColor',
    'getStyle': 'getChatStyle',
    'setStyle': 'setChatStyle',
    'getChunk': 'getChunkFromChunkCoords',
    'getLoadedEntities': 'getLoadedEntityList',
    'getHighestBlockY': 'getHeightValue',
    'isPermanent': 'getIsPotionDurationMax',
    'getIconLevel': 'getStatusIconIndex',
    'hasIcon': 'hasStatusIcon',
    'getAttributes': 'getAttributeModifiers',
    'hasEnchantments': 'isItemEnchanted',
    'viewDistance': 'renderDistanceChunks',
    'vsync': 'enableVsync',
    'decorateName': 'formatPlayerName',
    'getSelectedResourcePacks': 'getRepositoryEntries',
    'prevTickX': 'lastTickPosX',
    'prevTickY': 'lastTickPosY',
    'prevTickZ': 'lastTickPosZ',
    'getResourcePackManager': 'getResourcePackRepository',
    'getResourceManager': 'getResourceManager',
    'reloadResources': 'refreshResources',
    'getSoundManager': 'getSoundHandler',
    'getEntityRenderDispatcher': 'getRenderManager',
    'getEntityRenderManager': 'getRenderManager',
    'getIcon': 'getPackImage',
    'getLocalizedName': 'getTranslatedName',
    'getMaterialColor': 'getMapColor',
    'getColor': 'getMaterialMapColor',
    'getItemRenderer': 'getRenderItem',
    'getFramebuffer': 'getFramebuffer',
    'scheduleStop': 'shutdown',
    'getStringWidth': 'getStringWidth',
    'drawWithShadow': 'drawStringWithShadow',
    'addMessage': 'printChatMessage',
    'getChatHud': 'getChatGUI',
    'bindTexture': 'bindTexture',
    'translate': 'translate',
    'scale': 'scale',
    'rotate': 'rotate',
    'color': 'color',
    'enableBlend': 'enableBlend',
    'disableBlend': 'disableBlend',
    'enableTexture': 'enableTexture2D',
    'disableTexture': 'disableTexture2D',
    'enableDepthTest': 'enableDepth',
    'disableDepthTest': 'disableDepth',
    'depthMask': 'depthMask',
    'disableAlphaTest': 'disableAlpha',
    'enableAlphaTest': 'enableAlpha',
    'disableLighting': 'disableLighting',
    'enableLighting': 'enableLighting',
    'disableCull': 'disableCull',
    'enableCull': 'enableCull',
    'blendFunc': 'blendFunc',
    'blendFuncSeparate': 'tryBlendFuncSeparate',
    'getTessellator': 'getInstance',
    'getBuffer': 'getWorldRenderer',
    'begin': 'begin',
    'vertex': 'pos',
    'hasLabel': 'canRenderName',
    'renderLabelIfPresent': 'renderLivingLabel',
    'bobViewWhenHurt': 'hurtCameraEffect',
    'renderWeather': 'renderRainSnow',
    'getFov': 'getFOVModifier',
    'getEntityName': 'getName',
    'getUuid': 'getUniqueID',
    'getBlockPos': 'getPosition',
    'getPos': 'getPositionVector',
    'getMainHandStack': 'getHeldItem',
    'getArmorProtectionValue': 'getTotalArmorValue',
    'getAbsorption': 'getAbsorptionAmount',
    'getHealth': 'getHealth',
    'getMaxHealth': 'getMaxHealth',
    'isInvisible': 'isInvisible',
    'isSneaking': 'isSneaking',
    'isSprinting': 'isSprinting',
    'isOnGround': 'onGround',
    'getItemStack': 'getEntityItem',
    'getAge': 'getAge',
    'getItem': 'getItem',
    'getCount': 'stackSize',
    'getMaxCount': 'getMaxStackSize',
    'getDamage': 'getItemDamage',
    'getMaxDamage': 'getMaxDamage',
    'isDamageable': 'isItemStackDamageable',
    'getHoverName': 'getDisplayName',
    'getKeyCode': 'getKeyCode',
    'setKeyCode': 'setKeyCode',
    'setCode': 'setKeyCode',
    'wasPressed': 'isPressed',
    'isPressed': 'isKeyDown',
    'updateKeysByCode': 'resetKeyBindingArrayAndHash',
    'maxFramerate': 'limitFramerate',
    'fpsDebugString': 'debug',
    'renderWorld': 'renderWorld',
    'renderGameOverlay': 'renderGameOverlay',
    'runGameLoop': 'runGameLoop',
    'setServers': 'updateOnlineServers',
    'getRowWidth': 'getListWidth',
    'mouseClicked': 'mouseClicked',
    'mouseDragged': 'mouseClickMove',
    'mouseReleased': 'mouseReleased',
    'keyPressed': 'keyTyped',
    'shouldPause': 'doesGuiPauseGame',
}

# Packages that are our own or non-MC — never touch identifiers inside them unless explicit
SKIP_FILES = {'J8.java'}
MEM_DOT_PAT = re.compile(r'(\.|::)([A-Za-z_][A-Za-z0-9_]*)\b')


def translate_descriptor(desc: str, cls_map: dict[str, str]) -> str:
    def repl(m):
        c = m.group(1)
        return 'L' + cls_map.get(c, c) + ';'
    return re.sub(r'L(net/minecraft/[^;]+|com/mojang/blaze3d/[^;]+);', repl, desc)


def translate_file(text: str, cls_map: dict[str, str], cls_methods, cls_fields, unambig_members: dict[str, str]) -> str:
    # 1. Collect imported net.minecraft / com.mojang.blaze3d simple names in this file
    simple_map = {}
    simple_to_yarn_slash = {}
    for m in re.finditer(r'^\s*import\s+(?:static\s+)?((?:net\.minecraft|com\.mojang\.blaze3d)\.[A-Za-z0-9_.$]+)\s*;', text, re.MULTILINE):
        fq = m.group(1)
        slash = fq.replace('.', '/')
        y_simple = slash.rsplit('/', 1)[-1].replace('$', '.')
        simple_to_yarn_slash[y_simple] = slash
        if slash in cls_map:
            mcp_slash = cls_map[slash]
            m_simple = mcp_slash.rsplit('/', 1)[-1].replace('$', '.')
            if y_simple != m_simple:
                simple_map[y_simple] = m_simple

    # Detect @Mixin target class(es) in Yarn before class-name replacement
    mixin_targets = []
    mm = re.search(r'@Mixin\s*\(([^)]+)\)', text)
    if mm:
        for t in re.findall(r'([A-Za-z0-9_.]+)\.class', mm.group(1)):
            slash = t.replace('.', '/') if '.' in t else simple_to_yarn_slash.get(t, '')
            if slash:
                mixin_targets.append(slash)

    MIXIN_OVERRIDES = {
        ('net/minecraft/client/gui/screen/multiplayer/MultiplayerScreen', 'tick'): 'updateScreen',
        ('net/minecraft/client/gui/screen/Screen', 'init'): 'setWorldAndResolution',
        ('net/minecraft/client/particle/ParticleManager', 'addParticle'): 'addEffect',
        ('net/minecraft/client/gui/hud/ChatHud', 'addMessage'): 'setChatLine',
        ('net/minecraft/client/gui/widget/ServerEntry', 'render'): 'drawEntry',
        ('net/minecraft/client/gui/widget/ServerEntry', 'mouseClicked'): 'mousePressed',
        ('net/minecraft/client/gui/widget/ServerEntry', 'serverInfo'): 'server',
        ('net/minecraft/client/gui/widget/ServerEntry', 'client'): 'mc',
        ('net/minecraft/client/gui/widget/ServerEntry', 'parent'): 'owner',
        ('net/minecraft/client/gui/widget/MultiplayerServerListWidget', 'servers'): 'serverListInternet',
        ('net/minecraft/client/render/chunk/ChunkBuilder', 'upload'): 'runChunkUploads',
        ('net/minecraft/client/gui/screen/multiplayer/MultiplayerScreen', 'serverListWidget'): 'serverListSelector',
        ('net/minecraft/client/render/entity/LivingEntityRenderer', 'buffer'): 'brightnessBuffer',
        ('net/minecraft/client/render/entity/LivingEntityRenderer', 'method_10252'): 'setBrightness',
        ('net/minecraft/client/MinecraftClient', 'currentFps'): 'debugFPS',
        ('net/minecraft/client/MinecraftClient', 'options'): 'gameSettings',
        ('net/minecraft/client/MinecraftClient', 'world'): 'theWorld',
        ('net/minecraft/client/gui/screen/multiplayer/MultiplayerScreen', 'textRenderer'): 'fontRendererObj',
        ('net/minecraft/client/network/ClientPlayNetworkHandler', 'client'): 'gameController',
        ('net/minecraft/client/network/ClientPlayNetworkHandler', 'onEntityStatus'): 'handleEntityStatus',
        ('net/minecraft/client/network/ClientPlayNetworkHandler', 'onCustomPayload'): 'handleCustomPayload',
        ('net/minecraft/client/render/WorldRenderer', 'client'): 'mc',
        ('net/minecraft/client/render/WorldRenderer', 'world'): 'theWorld',
        ('net/minecraft/client/render/entity/EntityRenderer', 'hasLabel'): 'canRenderName',
        ('net/minecraft/client/render/entity/LivingEntityRenderer', 'hasLabel'): 'canRenderName',
    }

    def lookup_mixin_member(name: str, is_field: bool = False) -> str | None:
        for tc in mixin_targets:
            if (tc, name) in MIXIN_OVERRIDES:
                return MIXIN_OVERRIDES[(tc, name)]
        table = cls_fields if is_field else cls_methods
        for tc in mixin_targets:
            cands = table.get(tc, {}).get(name)
            if cands:
                return next(iter(cands))
        if not is_field:
            if name == 'init':
                return 'initGui'
            if name == 'render':
                return 'drawScreen'
            if name == 'tick':
                return 'updateScreen'
        return None

    # 2. Replace full dotted class names (net.minecraft.* and com.mojang.blaze3d.*)
    dotted_cls = {k.replace('/', '.'): v.replace('/', '.') for k, v in cls_map.items()}
    def repl_dotted(m):
        full = m.group(0)
        parts = full.split('.')
        for i in range(len(parts), 2, -1):
            prefix = '.'.join(parts[:i])
            if prefix in dotted_cls:
                rest = '.'.join(parts[i:])
                return dotted_cls[prefix] + ('.' + rest if rest else '')
        return full
    text = re.sub(r'\b(?:net\.minecraft|com\.mojang\.blaze3d)\.[A-Za-z0-9_.]+', repl_dotted, text)

    # 3. Translate class-aware Mixin @Accessor, method="...", and target="Lowner;method(" BEFORE generic member renames
    if mixin_targets:
        text = re.sub(r'(@Accessor\s*\(\s*")([A-Za-z0-9_]+)("\s*\))',
                      lambda m: m.group(1) + (lookup_mixin_member(m.group(2), True) or EXPLICIT_MEMBERS.get(m.group(2), m.group(2))) + m.group(3), text)
        text = re.sub(r'(method\s*=\s*")([A-Za-z0-9_]+)([("])',
                      lambda m: m.group(1) + (lookup_mixin_member(m.group(2), False) or EXPLICIT_MEMBERS.get(m.group(2), m.group(2))) + m.group(3), text)
        # @Shadow field / method names
        def repl_shadow(m):
            prefix, name, suffix = m.group(1), m.group(2), m.group(3)
            is_fld = suffix.strip() == ';'
            mapped = lookup_mixin_member(name, is_fld)
            return prefix + (mapped if mapped else name) + suffix
        text = re.sub(r'(@Shadow[^\n;{}]*?\s)([A-Za-z_][A-Za-z0-9_]*)(\s*[;(])', repl_shadow, text)

    def repl_at_target(m):
        owner, sep, member, paren = m.group(1), m.group(2), m.group(3), m.group(4)
        mapped = cls_methods.get(owner, {}).get(member)
        if mapped:
            member_out = next(iter(mapped))
        else:
            member_out = EXPLICIT_MEMBERS.get(member, member)
        return cls_map.get(owner, owner) + sep + member_out + paren

    text = re.sub(r'((?:net/minecraft|com/mojang/blaze3d)/[A-Za-z0-9_/$]+)(;)([A-Za-z0-9_]+)(\()', repl_at_target, text)

    # 4. Replace remaining internal slash class names & descriptors
    text = re.sub(r'(?:net/minecraft|com/mojang/blaze3d)/[A-Za-z0-9_/$]+',
                  lambda m: cls_map.get(m.group(0), m.group(0)), text)

    # 5. Replace imported simple class names & dot-accesses (skip package/import lines!)
    simple_pat = re.compile(r'\b(' + '|'.join(re.escape(k) for k in sorted(simple_map, key=len, reverse=True)) + r')\b') if simple_map else None
    merged_members = {k: v for k, v in EXPLICIT_MEMBERS.items() if k != v}

    out_lines = []
    for line in text.splitlines(keepends=True):
        stripped = line.lstrip()
        if stripped.startswith('import ') or stripped.startswith('package '):
            out_lines.append(line)
            continue
        if simple_pat is not None:
            line = simple_pat.sub(lambda m: simple_map[m.group(1)], line)
        line = re.sub(r'\bMinecraft\.getInstance\(', 'Minecraft.getMinecraft(', line)
        line = MEM_DOT_PAT.sub(lambda m: m.group(1) + merged_members.get(m.group(2), m.group(2)), line)
        line = re.sub(r'\bMinecraft\.getMinecraft\(\)\.submit\(', 'Minecraft.getMinecraft().addScheduledTask(', line)
        line = re.sub(r'\bGuiUtilRenderComponents\.wrapLines\(', 'GuiUtilRenderComponents.splitText(', line)
        line = re.sub(r'\bfontRendererObj\.wrapLines\(', 'fontRendererObj.listFormattedStringToWidth(', line)
        line = re.sub(r'\b(s|session)\.getGameProfile\(', r'\1.getProfile(', line)
        line = re.sub(r'\bgetNetworkManager\(\)\.getAddress\(', 'getNetworkManager().getRemoteAddress(', line)
        line = re.sub(r'\bServerAddress\.getAddress\(', 'ServerAddress.getIP(', line)
        line = re.sub(r'\b(server|serverInfo)\.name\b', r'\1.serverName', line)
        line = re.sub(r'\bcapabilities\.flying\b', 'capabilities.isFlying', line)
        line = re.sub(r'\bplayer\.isPartVisible\(', 'player.isWearing(', line)
        line = re.sub(r'\bmodel\.child\b', 'model.isChild', line)
        line = re.sub(r'\bpack\.getDescription\(', 'pack.getTexturePackDescription(', line)
        line = re.sub(r'\bpack\.getName\(', 'pack.getResourcePackName(', line)
        line = re.sub(r'\bgetRunDirectory\(', 'getDataDirectory(', line)
        line = re.sub(r'\bgetBiomeGenForCoords\(([^)]+)\)\.name\b', r'getBiomeGenForCoords(\1).biomeName', line)
        line = re.sub(r'\.getMaterialMapColor\(\)\.color\b', '.getMaterialMapColor().colorValue', line)
        line = re.sub(r'\b(?:net\.minecraft\.util\.)?MathHelper\.clamp\(', 'com.batclient.platform.forge.ForgeMath.clamp(', line)
        line = re.sub(r'\b(?:net\.minecraft\.util\.)?MathHelper\.wrapDegrees\(', 'com.batclient.platform.forge.ForgeMath.wrapDegrees(', line)
        line = re.sub(r'\.getName\(\)\.(?:getFormattedText|getUnformattedText)\(\)', '.getName()', line)
        line = re.sub(r'\bGlStateManager\.(push|pop)\(', r'GlStateManager.\1Matrix(', line)
        line = re.sub(r'\bDefaultPlayerSkin\.getTexture\(', 'DefaultPlayerSkin.getDefaultSkin(', line)
        line = re.sub(r'\bEnchantmentHelper\.get\(', 'EnchantmentHelper.getEnchantments(', line)
        line = re.sub(r'\bMovingObjectPosition\.Type\b', 'MovingObjectPosition.MovingObjectType', line)
        line = re.sub(r'\bWorldSettings\.GameMode\b', 'WorldSettings.GameType', line)
        line = re.sub(r'\bEnumChatFormatting\.strip\(', 'EnumChatFormatting.getTextWithoutFormattingCodes(', line)
        line = re.sub(r'\bServerAddress\.parse\(', 'ServerAddress.fromString(', line)
        line = re.sub(r'\bItem\.fromBlock\(', 'Item.getItemFromBlock(', line)
        line = re.sub(r'\bRenderGlobal\.drawBox\(', 'RenderGlobal.drawOutlinedBoundingBox(', line)
        line = re.sub(r'\bMaterial\.WATER\b', 'Material.water', line)
        line = re.sub(r'\bBossStatus\.name\b', 'BossStatus.bossName', line)
        line = re.sub(r'\bBossStatus\.percent\b', 'BossStatus.healthScale', line)
        line = re.sub(r'\bMinecraft\.getTime\(', 'Minecraft.getSystemTime(', line)
        line = re.sub(r'\bMathHelper\.ceil\(', 'MathHelper.ceiling_float_int(', line)
        line = re.sub(r'\bMathHelper\.floor\(', 'MathHelper.floor_double(', line)
        line = re.sub(r'\bGui\.fill\(', 'Gui.drawRect(', line)
        line = re.sub(r'\bGui\.drawTexture\((?=[^)]*regionWidth)', 'Gui.drawScaledCustomSizeModalRect(', line)
        line = re.sub(r'\bGui\.drawTexture\(', 'Gui.drawModalRectWithCustomSizedTexture(', line)
        line = re.sub(r'\bHELPER\.drawTexture\(x,\s*y,\s*u,\s*v,', 'HELPER.drawTexturedModalRect(x, y, (int) u, (int) v,', line)
        line = re.sub(r'\bHELPER\.drawSprite\(x,\s*y,\s*sprite,\s*width,\s*height\)',
                      'net.minecraft.client.renderer.GlStateManager.bindTexture(0); Gui.drawScaledCustomSizeModalRect(x, y, 0, 0, 16, 16, width, height, 16, 16)', line)
        line = re.sub(r'renderItemOverlays\(tr,\s*stack,\s*x,\s*y,\s*countOverride\)', 'renderItemOverlayIntoGUI(tr, stack, x, y, countOverride)', line)
        line = re.sub(r'\.getBlockColor\(([^)]+,[^)]+,[^)]+)\)', r'.colorMultiplier(\1)', line)
        line = re.sub(r'\.getDistanceSq\((c\.(?:player|thePlayer))\)', r'.getDistanceSqToEntity(\1)', line)
        line = re.sub(r'\.texture\(', '.tex(', line)
        line = re.sub(r'(?<!\.)\b(mc|client|c|minecraft|Minecraft\.getMinecraft\(\))\.(world|player)\b',
                      lambda m: m.group(1) + ('.theWorld' if m.group(2) == 'world' else '.thePlayer'), line)
        line = re.sub(r'(?<!\.)\b(mc|client|c|Minecraft\.getMinecraft\(\))\.(width|height|result)\b',
                      lambda m: m.group(1) + {'width': '.displayWidth', 'height': '.displayHeight', 'result': '.objectMouseOver'}[m.group(2)], line)
        line = re.sub(r'(?<!\.)\b(mc|client|c)\.connect\(', r'\1.loadWorld(', line)
        line = re.sub(r'(?<!\.)\b(mc|client|c)\.submit\(', r'\1.addScheduledTask(', line)
        line = re.sub(r'(?<!\.)\b(mc|client|c)\.getServer\(', r'\1.getIntegratedServer(', line)
        line = re.sub(r'\b(fontRendererObj|textRenderer|tr|fr|renderer|font)\.draw\(', r'\1.drawString(', line)
        line = re.sub(r'\b(theWorld|world)\.rayTrace\(', r'\1.rayTraceBlocks(', line)
        line = re.sub(r'\bwin\.getHeight\(', 'win.getScaledHeight(', line)
        line = re.sub(r'\b(server|serverInfo)\.address\b', r'\1.serverIP', line)
        line = re.sub(r'\bpacket\.getStatus\(', 'packet.getOpCode(', line)
        line = re.sub(r'\b(stack|normalized|a|b|st)\.(getCustomName|getTranslationKey|getData|setDamage)\(',
                      lambda m: m.group(1) + '.' + {'getCustomName':'getDisplayName(','getTranslationKey':'getUnlocalizedName(','getData':'getMetadata(','setDamage':'setItemDamage('}[m.group(2)], line)
        line = re.sub(r'\b(stack|normalized|a|b|st)\.count\b', r'\1.stackSize', line)
        line = re.sub(r'\b(thePlayer|player|horse|attacker|item|entity)\.(x|y|z|prevX|prevY|prevZ|vehicle|pitch|yaw|removed)\b',
                      lambda m: m.group(1) + '.' + {'x':'posX','y':'posY','z':'posZ','prevX':'prevPosX','prevY':'prevPosY','prevZ':'prevPosZ','vehicle':'ridingEntity','pitch':'rotationPitch','yaw':'rotationYaw','removed':'isDead'}[m.group(2)], line)
        line = re.sub(r'\b(thePlayer|player|entity|item|e)\.squaredDistanceTo\(', r'\1.getDistanceSqToEntity(', line)
        line = re.sub(r'\.getDistanceSqToEntity\(([^,]+,[^,]+,[^)]+)\)', r'.getDistanceSq(\1)', line)
        line = re.sub(r'\.getDistanceSq\((c\.(?:player|thePlayer))\)', r'.getDistanceSqToEntity(\1)', line)
        line = re.sub(r'\b(thePlayer|player|entity)\.distanceTo\(', r'\1.getDistanceToEntity(', line)
        line = re.sub(r'\b(delta|dir|direction|end|eye|look|p|start|world|pos|vec|a|b|back|side)\.(x|y|z)\b(?!\()',
                      lambda m: m.group(1) + '.' + {'x':'xCoord','y':'yCoord','z':'zCoord'}[m.group(2)], line)
        line = re.sub(r'\bgetRenderManager\(\)\.(pitch|yaw)\b', lambda m: 'getRenderManager().' + ('playerViewX' if m.group(1) == 'pitch' else 'playerViewY'), line)
        line = re.sub(r'\bdelta\.length\(', 'delta.lengthVector(', line)
        line = re.sub(r'\beye\.squaredDistanceTo\(', 'eye.squareDistanceTo(', line)
        line = re.sub(r'\bcamera\.add\(', 'camera.addVector(', line)
        line = re.sub(r'\btheWorld\.dimension\.getType\(\)', 'theWorld.provider.getDimensionId()', line)
        line = re.sub(r'\btheWorld\.disconnect\(', 'theWorld.sendQuittingDisconnectingPacket(', line)
        line = re.sub(r'\bworld\.disconnect\(', 'world.sendQuittingDisconnectingPacket(', line)
        line = re.sub(r'\b(hit|block|objectMouseOver)\.type\b', r'\1.typeOfHit', line)
        line = re.sub(r'\b(hit|block|objectMouseOver)\.getPosition\(\)', r'\1.hitVec', line)
        line = re.sub(r'\b(hit|block|objectMouseOver)\.pos\b', r'\1.hitVec', line)
        line = re.sub(r'\b(BlockPos\s+[A-Za-z0-9_]+\s*=\s*(?:hit|block|objectMouseOver))\.hitVec\b', r'\1.getBlockPos()', line)
        line = re.sub(r'\bobjectMouseOver\.entity\b', 'objectMouseOver.entityHit', line)
        line = re.sub(r'\b(liveTexture|previewTexture|texture)\.upload\(', r'\1.updateDynamicTexture(', line)
        line = re.sub(r'\b(liveTexture|previewTexture|texture)\.clearGlId\(', r'\1.deleteGlTexture(', line)
        line = re.sub(r'\b(armor|hovered)\.(protection|slot)\b', lambda m: m.group(1) + ('.damageReduceAmount' if m.group(2) == 'protection' else '.armorType'), line)
        line = re.sub(r'\binventory\.(armor|main)\b', lambda m: 'inventory.' + ('armorInventory' if m.group(1) == 'armor' else 'mainInventory'), line)
        line = re.sub(r'\b(line|message)\.getText\(', r'\1.getChatComponent(', line)
        line = re.sub(r'\bmessage\.getId\(', 'message.getChatLineID(', line)
        line = re.sub(r'\bbuf\.readString\(', 'buf.readStringFromBuffer(', line)
        line = re.sub(r'\binitializeAttribute\(([^)]+)\)\.getValue\(', r'getEntityAttribute(\1).getAttributeValue(', line)
        line = re.sub(r'\bgetEntityAttribute\(([^)]+)\)\.getValue\(', r'getEntityAttribute(\1).getAttributeValue(', line)
        line = re.sub(r'\btext\.copy\(', 'text.createCopy(', line)
        line = re.sub(r'\b(prefix)\.(append)\(', r'\1.appendSibling(', line)
        line = re.sub(r'\bbox\.contains\(', 'box.isVecInside(', line)
        line = re.sub(r'\.stretch\(', '.addCoord(', line)
        line = re.sub(r'\bbinding\.getTranslationKey\(', 'binding.getKeyDescription(', line)
        line = re.sub(r'\bbinding\.getCode\(', 'binding.getKeyCode(', line)
        line = re.sub(r'\beff\.getTranslationKey\(', 'eff.getEffectName(', line)
        line = re.sub(r'\beffect\.getTranslationKey\(', 'effect.getEffectName(', line)
        line = re.sub(r'\bblockRegistry\.getIdentifier\(', 'blockRegistry.getNameForObject(', line)
        line = re.sub(r'\bid\.(getPath|getNamespace)\(', lambda m: 'id.' + ('getResourcePath(' if m.group(1) == 'getPath' else 'getResourceDomain('), line)
        line = re.sub(r'\bplayer\.getSkinId\(', 'player.getLocationSkin(', line)
        line = re.sub(r'\bplayer\.getModel\(', 'player.getSkinType(', line)
        line = re.sub(r'\brenderer\.getModel\(', 'renderer.getMainModel(', line)
        line = re.sub(r'\bmodel\.hat\b', 'model.bipedHeadwear', line)
        line = re.sub(r'\bpart\.visible\b', 'part.showModel', line)
        line = re.sub(r'\bentry\.getServer\(', 'entry.getServerData(', line)
        line = re.sub(r'\bserverListWidget\.updateBounds\(', 'serverListWidget.setDimensions(', line)
        line = re.sub(r'\bbat\$search\.(keyTyped|render|tick)\(', lambda m: 'bat$search.' + {'keyTyped':'textboxKeyTyped(','render':'drawTextBox(','tick':'updateCursorCounter('}[m.group(1)], line)
        line = re.sub(r'\bdelegate\.(render|shouldPauseGame|tick)\(', lambda m: 'delegate.' + {'render':'drawScreen(','shouldPauseGame':'doesGuiPauseGame(','tick':'updateScreen('}[m.group(1)], line)
        line = re.sub(r'\.append\((new\s+ChatComponentText|text|name)\b', r'.appendSibling(\1', line)
        line = re.sub(r'\.append\((BADGE_SPACE|"[^"]*")\)', r'.appendText(\1)', line)
        line = re.sub(r'\bServerAddress\.fromString\(([^)]+)\)\.getAddress\(', r'ServerAddress.fromString(\1).getIP(', line)
        line = re.sub(r'\bgetSession\(\)\.getGameProfile\(', 'getSession().getProfile(', line)
        line = re.sub(r'\bsession\(\)\.getGameProfile\(', 'session().getProfile(', line)
        line = re.sub(r'\bgetCurrentServerData\(\)\.address\b', 'getCurrentServerData().serverIP', line)
        line = re.sub(r'\bgetServerData\(\)\.address\b', 'getServerData().serverIP', line)
        line = re.sub(r'\bgetServerData\(\)\.name\b', 'getServerData().serverName', line)
        line = re.sub(r'\bm\.color\b', 'm.colorValue', line)
        line = re.sub(r'\bgetMapColor\(\)\.color\b', 'getMapColor().colorValue', line)
        line = re.sub(r'\bgetMaterialMapColor\(\)\.color\b', 'getMaterialMapColor().colorValue', line)
        line = re.sub(r'(getBiomeGenForCoords\([^;]+)\.name\b', r'\1.biomeName', line)
        line = re.sub(r'\bd\.address\b', 'd.serverIP', line)
        line = re.sub(r'\bd\.name\b', 'd.serverName', line)
        line = re.sub(r'\bpublic\s+void\s+render\((AbstractClientPlayer\s)', r'public void doRenderLayer(\1', line)
        line = re.sub(r'\bpublic\s+boolean\s+combineTextures\(', 'public boolean shouldCombineTextures(', line)
        line = re.sub(r'\bnew\s+BufferBuilder\(', 'new com.batclient.platform.mc189.BufferBuilder(', line)
        line = re.sub(r'\bSharedMonsterAttributes\.attackDamage\.getId\(', 'SharedMonsterAttributes.attackDamage.getAttributeUnlocalizedName(', line)
        line = re.sub(r'\b((?:ItemArmor\)\s*\(Object\)\s*\([^)]+\)|ItemArmor\)\s*[^)]+)\))\.protection\b', r'\1.damageReduceAmount', line)
        line = re.sub(r'\.protection\b', '.damageReduceAmount', line)
        line = re.sub(r'\b(row\.icon\(\)|item\.getEntityItem\(\))\.getTranslationKey\(', r'\1.getUnlocalizedName(', line)
        line = re.sub(r'\b(row\.icon\(\)|item\.getEntityItem\(\))\.count\b', r'\1.stackSize', line)
        line = re.sub(r'\bentity\.world\b', 'entity.worldObj', line)
        line = re.sub(r'\bdelegate\.init\(', 'delegate.setWorldAndResolution(', line)
        line = re.sub(r'\be\.getLocalizedName\(', 'e.getTranslatedName(', line)
        line = re.sub(r'\bhasLabel\(', 'canRenderName(', line)
        line = re.sub(r'(Vertices\.color\(.*)\.next\(\)', r'\1.endVertex()', line)
        line = re.sub(r'\btheWorld\.dimension\b', 'theWorld.provider', line)
        line = re.sub(r'\bprovider\.getName\(\)', 'provider.getDimensionName()', line)
        line = re.sub(r'^\s*\.next\(\);', '                .endVertex();', line)
        line = re.sub(r'\b(getWorldRenderer\(\)|bb|wr|buf|buffer)\b([^;\n]*)\.next\(\)', r'\1\2.endVertex()', line)
        line = re.sub(r'\.(pos|tex|color|lightmap|normal)\(([^)]*)\)\.next\(\)', r'.\1(\2).endVertex()', line)
        out_lines.append(line)
    res = ''.join(out_lines)
    if mixin_targets:
        for (tc, mname), mtarget in MIXIN_OVERRIDES.items():
            if tc in mixin_targets and mname != mtarget:
                res = re.sub(r'(?<![A-Za-z0-9_./])(?:this\.)?' + re.escape(mname) + r'\b(?!\s*\()',
                             lambda m, mt=mtarget: ('this.' + mt if m.group(0).startswith('this.') else mt), res)
    if re.search(r'\bVec3\s+camera\b', res):
        res = re.sub(r'\bcamera\.(x|y|z)\b(?!\()', lambda m: 'camera.' + {'x':'xCoord','y':'yCoord','z':'zCoord'}[m.group(1)], res)
    else:
        res = re.sub(r'\bcamera\.(x|y|z|prevX|prevY|prevZ)\b(?!\()',
                     lambda m: 'camera.' + {'x':'posX','y':'posY','z':'posZ','prevX':'prevPosX','prevY':'prevPosY','prevZ':'prevPosZ'}[m.group(1)], res)
    if re.search(r'\bextends\s+(?:GuiScreen|GuiIngameMenu)\b', res):
        res = re.sub(r'\bsuper\.init\(', 'super.initGui(', res)
        res = re.sub(r'\bpublic\s+void\s+init\(', 'public void initGui(', res)
        res = re.sub(r'\bsuper\.render\(', 'super.drawScreen(', res)
        res = re.sub(r'\bpublic\s+void\s+render\(', 'public void drawScreen(', res)
        res = re.sub(r'\bpublic\s+void\s+tick\(', 'public void updateScreen(', res)
        res = re.sub(r'\bpublic\s+boolean\s+shouldPauseGame\(', 'public boolean doesGuiPauseGame(', res)
        res = re.sub(r'\bprotected\s+void\s+buttonClicked\(', 'protected void actionPerformed(', res)
        res = re.sub(r'"buttonClicked"', '"actionPerformed"', res)
        res = re.sub(r'\bprotected\s+void\s+keyPressed\(([^)]*)\)(?!\s*throws)', r'protected void keyTyped(\1) throws java.io.IOException', res)
        res = re.sub(r'\bprotected\s+void\s+mouseClicked\(([^)]*)\)(?!\s*throws)', r'protected void mouseClicked(\1) throws java.io.IOException', res)
        res = re.sub(r'\bprotected\s+void\s+mouseDragged\(', 'protected void mouseClickMove(', res)
        res = re.sub(r'\bpublic\s+void\s+handleMouse\(\)', 'public void handleMouseInput() throws java.io.IOException', res)
        res = re.sub(r'\bpublic\s+void\s+handleMouse\(', 'public void handleMouseInput(', res)
        res = re.sub(r'"keyPressed"', '"keyTyped"', res)
        res = re.sub(r'\bsuper\.removed\(', 'super.onGuiClosed(', res)
        res = re.sub(r'\bpublic\s+void\s+removed\(', 'public void onGuiClosed(', res)
        res = re.sub(r'\bhasShiftDown\(', 'isShiftKeyDown(', res)
        res = re.sub(r'\bhasControlDown\(', 'isCtrlKeyDown(', res)
        res = re.sub(r'\bhasAltDown\(', 'isAltKeyDown(', res)
        res = re.sub(r'\bbuttons\b', 'buttonList', res)
        res = re.sub(r'\bMinecraft\s+client\b', 'Minecraft mc', res)
        res = re.sub(r'\bthis\.client\b', 'this.mc', res)
        res = re.sub(r'\bmc\.mcDataDir\b', 'Minecraft.getMinecraft().mcDataDir', res)
        res = re.sub(r'(?m)^(?!import\s)(.*)$', lambda m: re.sub(r'(?<![A-Za-z0-9_./])client\b(?!/)', 'mc', m.group(1)), res)
    if 'NofapiGameOptionsAccessor' in res:
        res = re.sub(r'\r?\n\s*@Mutable\b', '', res)
        res = re.sub(r'import org\.spongepowered\.asm\.mixin\.Mutable;\s*\r?\n', '', res)
    if 'class InventoryOpeningMixin' in res:
        res = re.sub(r'\s*@ModifyVariable\(method\s*=\s*\{\s*"mouseClicked\(III\)V",\s*"mouseReleased\(III\)V"\s*\}[^}]+\}', '', res)
    if 'class ServerListExtrasMixin' in res:
        res = re.sub(r'\bservers\b', 'serverListInternet', res)
    if 'class PlayerNametagMixin' in res:
        res = res.replace(
            'import org.spongepowered.asm.mixin.injection.ModifyVariable;',
            'import org.spongepowered.asm.mixin.Unique;\nimport org.spongepowered.asm.mixin.injection.Inject;\nimport org.spongepowered.asm.mixin.injection.ModifyVariable;\nimport org.spongepowered.asm.mixin.injection.callback.CallbackInfo;'
        )
        res = re.sub(
            r'@ModifyVariable\(method = "renderLivingLabel", at = @At\("HEAD"\), argsOnly = true\)\s*private String bat\$badge\(String name, Entity entity, String original, double x, double y, double z, int maxDistance\)',
            '@Unique private Entity bat$entity;\n    @Inject(method = "renderLivingLabel", at = @At("HEAD"))\n    private void bat$captureEntity(Entity entity, String str, double x, double y, double z, int maxDistance, CallbackInfo ci) { this.bat$entity = entity; }\n    @ModifyVariable(method = "renderLivingLabel", at = @At("HEAD"), argsOnly = true)\n    private String bat$badge(String name)',
            res
        )
        res = res.replace('if (entity instanceof AbstractClientPlayer', 'Entity entity = this.bat$entity;\n        if (entity instanceof AbstractClientPlayer')
    if 'class MotionBlurMixin' in res:
        res = res.replace('private void bat$blurWorld(float tickDelta, long nanoTime, CallbackInfo ci)', 'private void bat$blurWorld(int pass, float tickDelta, long nanoTime, CallbackInfo ci)')
    if 'class MultiplayerExtrasMixin' in res:
        res = res.replace('import org.spongepowered.asm.mixin.injection.ModifyArgs;', 'import org.spongepowered.asm.mixin.injection.ModifyArg;')
        res = res.replace('import org.spongepowered.asm.mixin.injection.invoke.arg.Args;\n', '')
        res = re.sub(
            r'@ModifyArgs\(\s*method = "drawScreen\(IIF\)V",\s*at = @At\(value = "INVOKE", target = "([^"]+)"\)\s*\)\s*private void bat\$titleAboveSearch\(Args args\) \{\s*args\.set\(3, 8\);\s*\}',
            r'@ModifyArg(method = "drawScreen(IIF)V", at = @At(value = "INVOKE", target = "\1"), index = 3)\n    private int bat$titleAboveSearch(int y) {\n        return 8;\n    }',
            res
        )
    if 'class RainCustomizerMixin' in res:
        res = res.replace('import org.spongepowered.asm.mixin.injection.ModifyArgs;', 'import org.spongepowered.asm.mixin.injection.ModifyArg;')
        res = res.replace('import org.spongepowered.asm.mixin.injection.invoke.arg.Args;\n', '')
        res = re.sub(
            r'@ModifyArgs\(method = "renderRainSnow\(F\)V", at = @At\(value = "INVOKE", target = "([^"]+)"\)\)\s*private void bat\$opacity\(Args args\) \{\s*float a = \(Float\) args\.get\(3\);\s*args\.set\(3, a \* ([^;]+)\);\s*\}',
            r'@ModifyArg(method = "renderRainSnow(F)V", at = @At(value = "INVOKE", target = "\1"), index = 3)\n    private float bat$opacity(float a) {\n        return a * \2;\n    }',
            res
        )
    return res


def adapt_mcp_18(res: str) -> str:
    # ScaledResolution(mc, mc.displayWidth, mc.displayHeight)
    res = re.sub(
        r'new\s+((?:net\.minecraft\.client\.gui\.)?ScaledResolution)\(((?:[^,()]|\(\))+)\)',
        r'new \1(\2, (\2).displayWidth, (\2).displayHeight)',
        res,
    )
    # Non-generic RendererLivingEntity / LayerRenderer
    res = res.replace('RendererLivingEntity<AbstractClientPlayer>', 'RendererLivingEntity')
    res = res.replace('LayerRenderer<AbstractClientPlayer>', 'LayerRenderer')
    # ResourcePackRepository.Entry is package-private in 1.8
    if 'class ResourcePackHud' in res:
        res = res.replace('ResourcePackRepository.Entry', 'Object')
        res = res.replace('pack.getResourcePackName()', '((String) bat$packCall(pack, "getResourcePackName", "func_110515_d"))')
        res = res.replace('pack.getTexturePackDescription()', '((String) bat$packCall(pack, "getTexturePackDescription", "func_110519_e"))')
        res = res.replace('pack.getResourcePack()', '((net.minecraft.client.resources.IResourcePack) bat$packCall(pack, "getResourcePack", "func_110514_c"))')
        res = re.sub(
            r'(\n\}\s*)$',
            '\n    private static Object bat$packCall(Object pack, String mcp, String srg) {\n'
            '        try {\n'
            '            java.lang.reflect.Method m;\n'
            '            try { m = pack.getClass().getDeclaredMethod(mcp); }\n'
            '            catch (NoSuchMethodException e) { m = pack.getClass().getDeclaredMethod(srg); }\n'
            '            m.setAccessible(true);\n'
            '            return m.invoke(pack);\n'
            '        } catch (Exception e) { return null; }\n'
            '    }\n}\n',
            res,
            count=1,
        )
    # Entity.getName() -> getCommandSenderName()
    res = re.sub(r'\b(horse|living|entity)\.getName\(\)', r'\1.getCommandSenderName()', res)
    # BlockIndicator breaking check
    if 'class BlockIndicator' in res:
        res = res.replace('client.playerController.getIsHittingBlock()', '((com.batclient.mixin.BlockBreakingAccessor) client.playerController).bat$getBreakingProgress() > 0f')
    # Raw List / Collection casts
    if 'class ClientPresence' in res:
        res = res.replace('client.theWorld.playerEntities.stream()', '((java.util.List<net.minecraft.entity.player.EntityPlayer>) client.theWorld.playerEntities).stream()')
    if 'class HudRenderer' in res:
        res = res.replace('board.getSortedScores(objective).stream()', '((java.util.Collection<net.minecraft.scoreboard.Score>) board.getSortedScores(objective)).stream()')
    if 'class LootBeams' in res:
        res = res.replace('for (Entity entity : c.theWorld.getLoadedEntityList()) {', 'for (Object _entity : c.theWorld.getLoadedEntityList()) { Entity entity = (Entity) _entity;')
    if 'class WorldVisualModules' in res:
        res = res.replace('for (Entity entity : context.world().getLoadedEntityList()) {', 'for (Object _entity : context.world().getLoadedEntityList()) { Entity entity = (Entity) _entity;')
        res = re.sub(
            r'RenderGlobal\.drawOutlinedBoundingBox\(([^,]+),\s*([^,]+),\s*([^,]+),\s*([^,]+),\s*([^)]+)\)',
            r'RenderGlobal.drawOutlinedBoundingBox(\1, (((\5) & 255) << 24) | (((\2) & 255) << 16) | (((\3) & 255) << 8) | ((\4) & 255))',
            res,
        )
    if 'class NativeChat' in res:
        res = res.replace('for (NetworkPlayerInfo player : c.getNetHandler().getPlayerInfoMap()) {', 'for (Object _player : c.getNetHandler().getPlayerInfoMap()) { NetworkPlayerInfo player = (NetworkPlayerInfo) _player;')
        res = res.replace('GuiUtilRenderComponents.splitText(', 'GuiUtilRenderComponents.func_178908_a(')
    # Minecraft.getProfileProperties() -> getTwitchDetails()
    res = res.replace('.getProfileProperties()', '.getTwitchDetails()')
    # ServerAddress.fromString -> func_78860_a
    res = res.replace('ServerAddress.fromString(', 'ServerAddress.func_78860_a(')
    # WorldSettings.GameType.CREATIVE -> getGameTypeById(1)
    res = res.replace('net.minecraft.world.WorldSettings.GameType.CREATIVE', 'net.minecraft.world.WorldSettings.getGameTypeById(1)')
    # SkinLayers3D LayerRenderer
    if 'class SkinLayers3D' in res:
        res = re.sub(
            r'public void doRenderLayer\(AbstractClientPlayer (\w+),([^)]+)\)\s*\{',
            r'public void doRenderLayer(net.minecraft.entity.EntityLivingBase _entity,\2) {\n            if (!(_entity instanceof AbstractClientPlayer)) return;\n            AbstractClientPlayer \1 = (AbstractClientPlayer) _entity;',
            res,
        )
    # Mixin 1.8.0 targets
    if 'class NativeNametagStyleMixin' in res:
        res = res.replace(
            'target = "Lnet/minecraft/client/renderer/WorldRenderer;color(FFFF)Lnet/minecraft/client/renderer/WorldRenderer;"',
            'target = "Lnet/minecraft/client/renderer/WorldRenderer;setColorRGBA_F(FFFF)V"',
        )
        res = res.replace(
            'private WorldRenderer bat$bgColor(WorldRenderer buf, float r, float g, float b, float a) {',
            'private void bat$bgColor(WorldRenderer buf, float r, float g, float b, float a) {',
        )
        res = re.sub(r'return buf\.color\(([^;]+)\);', r'buf.setColorRGBA_F(\1);', res)
    if 'class RainCustomizerMixin' in res:
        res = res.replace(
            'Lnet/minecraft/client/renderer/WorldRenderer;color(FFFF)Lnet/minecraft/client/renderer/WorldRenderer;',
            'Lnet/minecraft/client/renderer/WorldRenderer;setColorRGBA_F(FFFF)V',
        )
    if 'class MotionBlurMixin' in res:
        res = res.replace('@Inject(method = "drawScreen", at = @At("HEAD"))', '@Inject(method = "updateCameraAndRender", at = @At("HEAD"))')
        res = res.replace('private void bat$prepareBlur(float tickDelta, long nanoTime, CallbackInfo ci)', 'private void bat$prepareBlur(float tickDelta, CallbackInfo ci)')
    if 'class ServerEntryExtrasMixin' in res:
        res = res.replace('private ServerData server;', 'private ServerData field_148301_e;')
        res = re.sub(r'\bthis\.server\b|\bserver\b', 'field_148301_e', res)
        res = res.replace('private GuiMultiplayer owner;', 'private GuiMultiplayer field_148303_c;')
        res = re.sub(r'\bthis\.owner\b|\bowner\b', 'field_148303_c', res)
    if 'class ServerListExtrasMixin' in res:
        res = res.replace('serverListInternet', 'field_148198_l')
    # Vertices.java helpers
    if 'class Vertices' in res:
        res = res.replace('return v.pos(x, y, z);', 'v.addVertex(x, y, z); return v;')
        res = res.replace('return v.normal(x, y, z);', 'v.setNormal(x, y, z); return v;')
        res = res.replace(
            'return v.color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF);',
            'v.setColorRGBA((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF); return v;',
        )
    # WorldRenderer non-fluent 1.8.0 methods
    res = re.sub(r'(\b\w+)\.begin\(([^,]+),\s*DefaultVertexFormats\.[A-Z_]+\)', r'\1.startDrawing(\2)', res)
    res = res.replace('import net.minecraft.client.renderer.vertex.DefaultVertexFormats;\n', '')
    res = re.sub(r'(\b\w+)\.pos\(([^;]+?)\)\.tex\(([^;]+?)\)\.color\(([^;]+?)\)\.endVertex\(\)', r'\1.setColorRGBA(\4); \1.setTextureUV(\3); \1.addVertex(\2)', res)
    res = re.sub(r'(\b\w+)\.pos\(([^;]+?)\)\.color\(([^;]+?)\)\.endVertex\(\)', r'\1.setColorRGBA(\3); \1.addVertex(\2)', res)
    res = re.sub(r'(\b\w+)\.pos\(([^;]+?)\)\.tex\(([^;]+?)\)\.endVertex\(\)', r'\1.setTextureUV(\3); \1.addVertex(\2)', res)
    res = re.sub(r'Vertices\.color\((\w+)\.pos\(([^;]+?)\),\s*([^)]+)\)\.endVertex\(\)', r'Vertices.color(\1, \3); \1.addVertex(\2)', res)
    res = re.sub(r'Vertices\.normal\((\w+)\.pos\(([^;]+?)\)\.tex\(([^;]+?)\),\s*([^)]+)\)\.endVertex\(\)', r'Vertices.normal(\1, \4); \1.setTextureUV(\3); \1.addVertex(\2)', res)
    res = re.sub(r'(\w+)\.pos\(([^;]+?)\)\s*\.tex\(([^;]+?)\)\s*\.normal\(([^;]+?)\)\s*\.endVertex\(\)', r'\1.setNormal(\4); \1.setTextureUV(\3); \1.addVertex(\2)', res)
    return res


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--yarn-tiny', required=True)
    ap.add_argument('--mcp-tiny', required=True)
    ap.add_argument('--src', required=True)
    ap.add_argument('--dst', required=True)
    ap.add_argument('--forge-pkg', action='store_true')
    args = ap.parse_args()

    cls_map, cls_methods, cls_fields, unambig = build_maps(Path(args.yarn_tiny), Path(args.mcp_tiny))
    src_root = Path(args.src)
    dst_root = Path(args.dst)
    if dst_root.exists():
        shutil.rmtree(dst_root)
    dst_root.mkdir(parents=True)

    is_mc18 = ('1.8-' in str(args.mcp_tiny) and '1.8.9' not in str(args.mcp_tiny))
    count = 0
    for p in src_root.rglob('*'):
        rel = p.relative_to(src_root)
        out = dst_root / rel
        if p.is_dir():
            out.mkdir(parents=True, exist_ok=True)
            continue
        out.parent.mkdir(parents=True, exist_ok=True)
        if p.suffix == '.java' and p.name not in SKIP_FILES:
            txt = p.read_text(encoding='utf-8')
            if args.forge_pkg:
                txt = txt.replace('com.batclient.platform.fabric', 'com.batclient.platform.forge')
            translated = translate_file(txt, cls_map, cls_methods, cls_fields, unambig)
            if is_mc18:
                translated = adapt_mcp_18(translated)
            out.write_text(translated, encoding='utf-8')
            count += 1
        else:
            shutil.copy2(p, out)
    print(f'YARN_TO_MCP_DONE: {count} java files translated ({len(cls_map)} classes, {len(unambig)} unambig members)')


if __name__ == '__main__':
    main()
