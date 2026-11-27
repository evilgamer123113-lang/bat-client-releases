/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.objectweb.asm.ClassReader
 *  org.objectweb.asm.ClassVisitor
 *  org.objectweb.asm.ClassWriter
 *  org.objectweb.asm.tree.AbstractInsnNode
 *  org.objectweb.asm.tree.ClassNode
 *  org.objectweb.asm.tree.InsnList
 *  org.objectweb.asm.tree.InsnNode
 *  org.objectweb.asm.tree.MethodInsnNode
 *  org.objectweb.asm.tree.MethodNode
 *  org.objectweb.asm.tree.TypeInsnNode
 *  org.objectweb.asm.tree.VarInsnNode
 */
package com.batclient.cosmetics;

import com.batclient.cosmetics.Mappings;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

final class CapeTransformer
implements ClassFileTransformer {
    private static final String HOOK = "com/batclient/cosmetics/CapeAnimator";
    private static final String PERF = "com/batclient/cosmetics/PerformanceTuner";
    private static final String PLAYER_HOOK = "com/batclient/cosmetics/PlayerListHook";
    private final String capeOwnerCls;
    private final String altCapeOwnerCls;
    private final String capeGetter;
    private final String capeReturnCls;
    private final String capeGetterDesc;
    private final String tickCls;
    private final String tick;
    private final String playerListEntryCls;
    private final boolean skinMode;

    CapeTransformer(Mappings m) {
        this.capeOwnerCls = m.cls("CapeGetterOwner");
        this.altCapeOwnerCls = m.cls("AbstractClientPlayer");
        this.capeGetter = m.mtd("capeGetter");
        this.skinMode = "PlayerSkin".equals(m.meta("capeGetterReturns"));
        this.capeReturnCls = this.skinMode ? m.cls("PlayerSkin") : m.cls("ResourceLocation");
        this.capeGetterDesc = "()L" + this.capeReturnCls + ";";
        this.tickCls = m.cls("Minecraft");
        this.tick = m.mtd("tick");
        this.playerListEntryCls = m.cls("PlayerListEntry");
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null || classfileBuffer == null) {
            return null;
        }
        try {
            if (className.equals(this.capeOwnerCls) || this.altCapeOwnerCls != null && className.equals(this.altCapeOwnerCls)) {
                return this.patchCapeGetter(classfileBuffer);
            }
            if (className.equals(this.tickCls)) {
                return this.patchTick(classfileBuffer);
            }
            if (this.playerListEntryCls != null && className.equals(this.playerListEntryCls)) {
                return this.patchPlayerListEntry(classfileBuffer);
            }
        }
        catch (Throwable t) {
            System.err.println("[BatCape] transform failed for " + className + ": " + t);
        }
        return null;
    }

    private byte[] patchCapeGetter(byte[] classfileBuffer) {
        String origName;
        ClassNode cn = new ClassNode();
        new ClassReader(classfileBuffer).accept((ClassVisitor)cn, 0);
        MethodNode target = null;
        for (MethodNode mn : cn.methods) {
            if (!mn.name.equals(this.capeGetter) || !mn.desc.equals(this.capeGetterDesc)) continue;
            target = mn;
            break;
        }
        if (target == null) {
            return null;
        }
        if ((target.access & 0x508) != 0) {
            return null;
        }
        String hookMethod = this.skinMode ? "getSkin" : "getCape";
        String hookDesc = "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";
        target.name = origName = this.capeGetter + "$bat$orig";
        MethodNode wrapper = new MethodNode(target.access & 0xFFFFFBFF & 0xFFFFFEFF, this.capeGetter, this.capeGetterDesc, null, null);
        InsnList code = new InsnList();
        code.add((AbstractInsnNode)new VarInsnNode(25, 0));
        code.add((AbstractInsnNode)new VarInsnNode(25, 0));
        code.add((AbstractInsnNode)new MethodInsnNode(182, cn.name, origName, this.capeGetterDesc, false));
        code.add((AbstractInsnNode)new MethodInsnNode(184, HOOK, hookMethod, hookDesc, false));
        code.add((AbstractInsnNode)new TypeInsnNode(192, this.capeReturnCls));
        code.add((AbstractInsnNode)new InsnNode(176));
        wrapper.instructions.add(code);
        wrapper.maxStack = 3;
        wrapper.maxLocals = 1;
        cn.methods.add(wrapper);
        ClassWriter cw = new ClassWriter(0);
        cn.accept((ClassVisitor)cw);
        return cw.toByteArray();
    }

    private byte[] patchPlayerListEntry(byte[] classfileBuffer) {
        ClassNode cn = new ClassNode();
        new ClassReader(classfileBuffer).accept((ClassVisitor)cn, 0);
        MethodNode target = null;
        for (MethodNode mn : cn.methods) {
            // Namespace-invariant ctor: authlib GameProfile is never remapped.
            // Modern: (GameProfile, boolean)V  |  Legacy 1.8–1.13: (GameProfile)V
            if (mn.name.equals("<init>") && mn.desc.startsWith("(Lcom/mojang/authlib/GameProfile;")) {
                target = mn;
                break;
            }
        }
        if (target == null) {
            return null;
        }
        InsnList patch = new InsnList();
        // Replace the constructor argument for immutable modern Authlib profiles.
        patch.add((AbstractInsnNode)new VarInsnNode(25, 1));
        patch.add((AbstractInsnNode)new MethodInsnNode(184, PLAYER_HOOK, "prepareProfile", "(Ljava/lang/Object;)Ljava/lang/Object;", false));
        patch.add(new TypeInsnNode(192, "com/mojang/authlib/GameProfile"));
        patch.add(new VarInsnNode(58, 1));
        target.instructions.insert(patch);
        ClassWriter cw = new ClassWriter(0);
        cn.accept((ClassVisitor)cw);
        return cw.toByteArray();
    }

    private byte[] patchTick(byte[] classfileBuffer) {
        ClassNode cn = new ClassNode();
        new ClassReader(classfileBuffer).accept((ClassVisitor)cn, 0);
        MethodNode target = null;
        for (MethodNode mn : cn.methods) {
            if (!mn.name.equals(this.tick) || !mn.desc.equals("()V")) continue;
            target = mn;
            break;
        }
        if (target == null) {
            return null;
        }
        InsnList patch = new InsnList();
        patch.add((AbstractInsnNode)new MethodInsnNode(184, HOOK, "tick", "()V", false));
        patch.add((AbstractInsnNode)new MethodInsnNode(184, PERF, "onTick", "()V", false));
        if (target.instructions.size() == 0) {
            target.instructions.add(patch);
        } else {
            target.instructions.insert(patch);
        }
        ClassWriter cw = new ClassWriter(0);
        cn.accept((ClassVisitor)cw);
        return cw.toByteArray();
    }
}

