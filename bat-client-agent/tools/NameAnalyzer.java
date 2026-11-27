import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Structural name resolver for obfuscated Minecraft client jars (1.8 - 1.14.3).
 *
 * Given a jar (and optional hints from neighboring ground-truth versions), it
 * resolves the obfuscated names of the symbols RavenCape needs and emits JSON:
 *
 *   { mc, tick, acp, capeGetter, skinGetter, rloc, dynTex, tm, nativeImage,
 *     band }   // band: "int[]" (1.8-1.12.2) | "nativeImage" (1.13+)
 *
 * Hints are verified against the jar structure; a symbol that fails verification
 * is resolved structurally. Run with:
 *   java -cp <asm.jar> NameAnalyzer <client.jar> [--hints <json>]
 */
public class NameAnalyzer {
    static final class Mth {
        String name, desc;
        int access;
        List<AbstractInsnNode> insns;
        int size() {
            int n = 0;
            for (AbstractInsnNode i = insns == null ? null : insns.size() > 0 ? insns.get(0) : null; i != null; i = i.getNext()) n++;
            return n;
        }
        boolean isStatic() { return (access & Opcodes.ACC_STATIC) != 0; }
        boolean isAbstract() { return (access & Opcodes.ACC_ABSTRACT) != 0; }
    }

    static final class Cls {
        String name, superName;
        int access;
        List<Mth> methods = new ArrayList<>();
        List<FieldNode> fields = new ArrayList<>();
        List<Mth> methodsNamed(String n) {
            List<Mth> r = new ArrayList<>();
            for (Mth m : methods) if (m.name.equals(n)) r.add(m);
            return r;
        }
        boolean hasMethod(String n, String desc) {
            for (Mth m : methods) if (m.name.equals(n) && m.desc.equals(desc)) return true;
            return false;
        }
    }

    final Map<String, Cls> classes = new HashMap<>();
    final Map<String, Integer> referencers = new HashMap<>(); // methodKey "owner/name+desc" -> count of other classes calling it

    public static void main(String[] args) throws Exception {
        String jar = args[0];
        String hintsJson = null;
        for (int i = 1; i < args.length; i++) {
            if ("--hints".equals(args[i]) && i + 1 < args.length) hintsJson = args[i + 1];
        }
        NameAnalyzer a = new NameAnalyzer();
        a.load(jar);
        a.indexCalls();
        Map<String, String> hints = hintsJson == null ? new HashMap<>() : parseHints(hintsJson);
        Map<String, String> out = new TreeMap<>();
        a.resolve(hints, out);
        StringBuilder sb = new StringBuilder("{");
        int n = 0;
        for (Map.Entry<String, String> e : out.entrySet()) {
            if (n++ > 0) sb.append(",");
            sb.append("\"").append(e.getKey()).append("\":\"").append(e.getValue() == null ? "" : e.getValue()).append("\"");
        }
        sb.append("}");
        System.out.println(sb.toString());
    }

    static Map<String, String> parseHints(String json) {
        Map<String, String> m = new HashMap<>();
        String s = json.trim();
        if (s.startsWith("{")) s = s.substring(1);
        if (s.endsWith("}")) s = s.substring(0, s.length() - 1);
        for (String part : s.split(",")) {
            String[] kv = part.split(":", 2);
            if (kv.length != 2) continue;
            String k = kv[0].trim().replace("\"", "").trim();
            String v = kv[1].trim().replace("\"", "").trim();
            if (!k.isEmpty() && !v.isEmpty() && !v.equalsIgnoreCase("null")) m.put(k, v);
        }
        return m;
    }

    void load(String jar) throws Exception {
        ZipInputStream zin = new ZipInputStream(new FileInputStream(jar));
        ZipEntry e;
        while ((e = zin.getNextEntry()) != null) {
            if (!e.getName().endsWith(".class") || e.isDirectory()) continue;
            byte[] data = readAll(zin);
            try {
                ClassNode cn = new ClassNode();
                new ClassReader(data).accept(cn, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                Cls c = new Cls();
                c.name = cn.name;
                c.superName = cn.superName;
                c.access = cn.access;
                for (MethodNode mn : cn.methods) {
                    Mth m = new Mth();
                    m.name = mn.name;
                    m.desc = mn.desc;
                    m.access = mn.access;
                    m.insns = new ArrayList<>();
                    if (mn.instructions != null) for (AbstractInsnNode i = mn.instructions.getFirst(); i != null; i = i.getNext()) m.insns.add(i);
                    c.methods.add(m);
                }
                for (FieldNode fn : cn.fields) c.fields.add(fn);
                classes.put(c.name, c);
            } catch (Throwable t) {
                System.err.println("WARN parse " + e.getName() + ": " + t);
            }
        }
        zin.close();
    }

    static byte[] readAll(ZipInputStream zin) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int r;
        while ((r = zin.read(buf)) > 0) bos.write(buf, 0, r);
        return bos.toByteArray();
    }

    void indexCalls() {
        for (Cls c : classes.values()) {
            Set<String> seen = new HashSet<>();
            for (Mth m : c.methods) {
                for (AbstractInsnNode i : m.insns) {
                    if (i instanceof MethodInsnNode) {
                        MethodInsnNode mi = (MethodInsnNode) i;
                        if (mi.getOpcode() == Opcodes.INVOKESTATIC) {
                            String key = mi.owner + "/" + mi.name + mi.desc;
                            if (seen.add(key)) referencers.merge(key, 1, Integer::sum);
                        }
                    }
                }
            }
        }
    }

    Cls cls(String n) { return n == null ? null : classes.get(n); }

    // ---------- hint verify / structural ----------

    void resolve(Map<String, String> h, Map<String, String> out) {
        resolveMinecraft(h, out);
        resolveAbstractClientPlayer(h, out);
        resolveRloc(h, out);
        resolveDynamicTexture(h, out);
        resolveTextureManager(h, out);
        resolveNativeImage(h, out);
    }

    void resolveMinecraft(Map<String, String> h, Map<String, String> out) {
        Cls mc = null;
        String hint = h.get("mc");
        if (hint != null) {
            Cls c = cls(hint);
            if (c != null && findSelfStatic(c) != null) mc = c;
        }
        if (mc == null) mc = findMinecraftStructural();
        out.put("mc", mc == null ? "" : mc.name);
        // tick: hint verify then largest-0-arg-void
        String th = h.get("tick");
        if (mc != null) {
            if (th != null && mc.hasMethod(th, "()V")) out.put("tick", th);
            else {
                Mth best = largestZeroArgVoid(mc);
                out.put("tick", best == null ? "" : best.name);
            }
        } else out.put("tick", "");
    }

    Mth findSelfStatic(Cls c) {
        for (Mth m : c.methods) {
            if (m.isStatic() && m.insns.size() == 0) continue;
            if (m.isStatic() && m.insns.size() > 0 && m.insns.get(0) instanceof org.objectweb.asm.tree.MethodInsnNode) {
                // allow plain getters that return the class
            }
            if (m.isStatic() && m.desc.equals("()L" + c.name + ";")) return m;
        }
        return null;
    }

    Cls findMinecraftStructural() {
        // classes with a static 0-arg self-returning method; pick the most referenced
        List<Cls> cands = new ArrayList<>();
        for (Cls c : classes.values()) {
            for (Mth m : c.methods) {
                if (m.isStatic() && m.desc.equals("()L" + c.name + ";")) { cands.add(c); break; }
            }
        }
        if (cands.isEmpty()) return null;
        Collections.sort(cands, new Comparator<Cls>() {
            public int compare(Cls a, Cls b) {
                int ra = selfRefCount(a), rb = selfRefCount(b);
                return rb - ra;
            }
        });
        return cands.get(0);
    }

    int selfRefCount(Cls c) {
        int n = 0;
        for (Mth m : c.methods) {
            if (m.isStatic() && m.desc.equals("()L" + c.name + ";")) {
                Integer r = referencers.get(c.name + "/" + m.name + m.desc);
                if (r != null) n += r;
            }
        }
        return n;
    }

    Mth largestZeroArgVoid(Cls c) {
        Mth best = null;
        for (Mth m : c.methods) {
            if (m.desc.equals("()V") && !m.isStatic() && !m.isAbstract()) {
                if (best == null || m.size() > best.size()) best = m;
            }
        }
        return best;
    }

    void resolveRloc(Map<String, String> h, Map<String, String> out) {
        String set = out.get("rloc");
        if (set != null && !set.isEmpty()) return; // derived from ACP getter pair
        Cls r = null;
        String hint = h.get("rloc");
        if (hint != null) {
            Cls c = cls(hint);
            if (c != null && looksLikeRloc(c)) r = c;
        }
        if (r == null) {
            for (Cls c : classes.values()) {
                if (looksLikeRloc(c)) { r = c; break; }
            }
        }
        out.put("rloc", r == null ? "" : r.name);
    }

    boolean looksLikeRloc(Cls c) {
        return rtHasStringCtors(c);
    }

    void resolveAbstractClientPlayer(Map<String, String> h, Map<String, String> out) {
        String hint = h.get("acp");
        String rlocHint = h.get("rloc");
        Cls acp = null;
        if (hint != null) {
            Cls c = cls(hint);
            if (c != null && rlocGetters(c, rlocHint).size() >= 1) acp = c;
        }
        if (acp == null) acp = findAcpStructural();
        out.put("acp", acp == null ? "" : acp.name);
        if (acp != null) {
            // if rloc not resolved yet, derive it as the shared return type of ACP's getters
            String rloc = out.get("rloc");
            if (rloc == null || rloc.isEmpty()) {
                rloc = dominantGetterType(acp);
                out.put("rloc", rloc == null ? "" : rloc);
            }
            List<Mth> rlocs = rlocGetters(acp, rloc);
            String capeHint = h.get("capeGetter");
            String skinHint = h.get("skinGetter");
            Mth cape = null, skin = null;
            if (capeHint != null) {
                for (Mth m : rlocs) if (m.name.equals(capeHint)) cape = m;
            }
            if (skinHint != null) {
                for (Mth m : rlocs) if (m.name.equals(skinHint)) skin = m;
            }
            if (cape == null) cape = findCapeGetter(acp, rloc);
            if (skin == null) {
                for (Mth m : rlocs) if (m != cape) skin = m;
            }
            out.put("capeGetter", cape == null ? "" : cape.name);
            out.put("skinGetter", skin == null ? "" : skin.name);
        } else {
            out.put("capeGetter", "");
            out.put("skinGetter", "");
        }
    }

    List<Mth> rlocGetters(Cls c, String rloc) {
        List<Mth> r = new ArrayList<>();
        if (c == null || rloc == null) return r;
        String desc = "()L" + rloc + ";";
        for (Mth m : c.methods) {
            if (m.desc.equals(desc) && !m.isStatic()) r.add(m);
        }
        return r;
    }

    /** The shared return type of the cape/skin getter pair on an abstract player class. */
    String dominantGetterType(Cls c) {
        Map<String, Integer> byType = new HashMap<>();
        for (Mth m : c.methods) {
            if (m.isStatic() || m.name.equals("<init>")) continue;
            if (!m.desc.startsWith("()L") || !m.desc.endsWith(";")) continue;
            String rt = m.desc.substring(3, m.desc.length() - 1);
            byType.merge(rt, 1, Integer::sum);
        }
        String best = null;
        int bestN = 0;
        for (Map.Entry<String, Integer> e : byType.entrySet()) {
            Cls rt = cls(e.getKey());
            if (rt == null || !rtHasStringCtors(rt)) continue;
            if (e.getValue() > bestN) { bestN = e.getValue(); best = e.getKey(); }
        }
        if (System.getenv("NA_DEBUG") != null && c.name.equals("cil")) {
            Cls rr = cls("oa");
            StringBuilder cs = new StringBuilder();
            if (rr != null) for (Mth m : rr.methods) if (m.name.equals("<init>")) cs.append(m.desc).append(" ");
            System.err.println("DGT cil oa=" + (rr != null) + " ctors=[" + cs + "] rtHas=" + (rr != null && rtHasStringCtors(rr)) + " best=" + best + " bestN=" + bestN);
        }
        return bestN >= 2 ? best : null;
    }

    boolean rtHasStringCtors(Cls c) {
        boolean s1 = false, s2 = false;
        for (Mth m : c.methods) {
            if (m.name.equals("<init>")) {
                if (m.desc.equals("(Ljava/lang/String;)V")) s1 = true;
                if (m.desc.equals("(Ljava/lang/String;Ljava/lang/String;)V")) s2 = true;
            }
        }
        return s1 && s2;
    }

    Cls findAcpStructural() {
        List<Cls> cands = new ArrayList<>();
        for (Cls c : classes.values()) {
            if ((c.access & Opcodes.ACC_ABSTRACT) == 0) continue;
            String rt = dominantGetterType(c);
            if (rt == null) continue;
            cands.add(c);
        }
        Collections.sort(cands, new Comparator<Cls>() {
            public int compare(Cls a, Cls b) {
                String ra = dominantGetterType(a), rb = dominantGetterType(b);
                boolean au = hasUuidFallback(a, ra), bu = hasUuidFallback(b, rb);
                if (au != bu) return au ? -1 : 1;
                return countRloc(b, rb) - countRloc(a, ra);
            }
        });
        return cands.isEmpty() ? null : cands.get(0);
    }

    int countRloc(Cls c, String rloc) {
        return rlocGetters(c, rloc).size();
    }

    boolean hasUuidFallback(Cls c, String rloc) {
        for (Mth m : c.methods) {
            if (m.desc.equals("()L" + rloc + ";") && !m.isStatic() && isCapeGetterBody(m)) return true;
        }
        return false;
    }

    boolean isCapeGetterBody(Mth m) {
        // null-branch invokes a static method (UUID) -> rloc
        for (AbstractInsnNode i : m.insns) {
            if (i instanceof MethodInsnNode) {
                MethodInsnNode mi = (MethodInsnNode) i;
                if (mi.getOpcode() == Opcodes.INVOKESTATIC
                        && mi.desc.startsWith("(Ljava/util/UUID;)")
                        && mi.desc.endsWith(";")) return true;
            }
        }
        return false;
    }

    Mth findCapeGetter(Cls c, String rloc) {
        for (Mth m : c.methods) {
            if (m.desc.equals("()L" + rloc + ";") && !m.isStatic() && isCapeGetterBody(m)) return m;
        }
        return null;
    }

    void resolveDynamicTexture(Map<String, String> h, Map<String, String> out) {
        Cls dt = null;
        String hint = h.get("dynTex");
        if (hint != null) {
            Cls c = cls(hint);
            if (c != null && (hasBufferedImageCtor(c) || hasNativeImageCtor(c, out.get("nativeImage")))) dt = c;
        }
        if (dt == null) dt = findDynamicTextureStructural(out.get("nativeImage"));
        out.put("dynTex", dt == null ? "" : dt.name);
        if (dt != null) {
            String band = hasNativeImageCtor(dt, out.get("nativeImage")) ? "nativeImage" : "int[]";
            out.put("band", band);
            // upload = the single 0-arg void instance method (int[] band) or func-matching (nativeImage band)
            String uh = h.get("upload");
            if (band.equals("int[]")) {
                Mth up = null;
                if (uh != null && dt.hasMethod(uh, "()V")) up = dt.methodsNamed(uh).get(0);
                if (up == null) up = soleZeroArgVoid(dt);
                out.put("upload", up == null ? "" : up.name);
            } else {
                out.put("upload", uh != null && dt.hasMethod(uh, "()V") ? uh : "");
                String gp = h.get("getPixels");
                out.put("getPixels", gp != null && dt.hasMethod(gp, "()L" + safe(out.get("nativeImage")) + ";") ? gp : "");
            }
        } else {
            out.put("band", "");
            out.put("upload", "");
            out.put("getPixels", "");
        }
    }

    String safe(String s) { return s == null ? "" : s; }

    boolean hasBufferedImageCtor(Cls c) {
        for (Mth m : c.methods)
            if (m.name.equals("<init>") && m.desc.equals("(Ljava/awt/image/BufferedImage;)V")) return true;
        return false;
    }

    boolean hasNativeImageCtor(Cls c, String ni) {
        if (ni == null || ni.isEmpty()) return false;
        for (Mth m : c.methods)
            if (m.name.equals("<init>") && m.desc.equals("(L" + ni + ";)V")) return true;
        return false;
    }

    Mth soleZeroArgVoid(Cls c) {
        Mth found = null;
        for (Mth m : c.methods) {
            if (m.desc.equals("()V") && !m.isStatic()) {
                if (found != null) return null;
                found = m;
            }
        }
        return found;
    }

    Cls findDynamicTextureStructural(String ni) {
        // a class with a (BufferedImage) ctor or (NativeImage) ctor and a 0-arg ()[I or ()Lni; getter
        for (Cls c : classes.values()) {
            if (hasBufferedImageCtor(c)) {
                for (Mth m : c.methods) if (m.desc.equals("()[I") && !m.isStatic()) return c;
            }
            if (ni != null && hasNativeImageCtor(c, ni)) {
                for (Mth m : c.methods) if (m.desc.equals("()L" + ni + ";") && !m.isStatic()) return c;
            }
        }
        return null;
    }

    void resolveTextureManager(Map<String, String> h, Map<String, String> out) {
        Cls tm = null;
        String hint = h.get("tm");
        if (hint != null) {
            Cls c = cls(hint);
            if (c != null && hasRlocMapField(c, out.get("rloc"))) tm = c;
        }
        if (tm == null) {
            for (Cls c : classes.values()) {
                if (hasRlocMapField(c, out.get("rloc"))) { tm = c; break; }
            }
        }
        out.put("tm", tm == null ? "" : tm.name);
    }

    boolean hasRlocMapField(Cls c, String rloc) {
        if (rloc == null) return false;
        for (FieldNode f : c.fields) {
            String d = f.desc;
            if (!d.startsWith("Ljava/util/Map;")) continue;
            // The map's generic key is rloc at runtime; we can't see generics via
            // desc, but TextureManager is the only class with a Map field of this
            // shape that also has a bind(ResourceLocation) method. Require a
            // 1-arg (L<rloc>;)V instance method ("bind").
            if (hasBindMethod(c, rloc)) return true;
        }
        return false;
    }

    boolean hasBindMethod(Cls c, String rloc) {
        for (Mth m : c.methods) {
            if (!m.isStatic() && m.desc.equals("(L" + rloc + ";)V")) return true;
        }
        return false;
    }

    void resolveNativeImage(Map<String, String> h, Map<String, String> out) {
        String hint = h.get("nativeImage");
        Cls ni = null;
        if (hint != null) {
            Cls c = cls(hint);
            if (c != null && hasStaticRead(c)) ni = c;
        }
        if (ni == null) {
            String dynTex = out.get("dynTex");
            if (dynTex != null) {
                Cls dt = cls(dynTex);
                if (dt != null) {
                    for (FieldNode f : dt.fields) {
                        if (f.desc.startsWith("L") && f.desc.endsWith(";")) {
                            String t = f.desc.substring(1, f.desc.length() - 1);
                            Cls c = cls(t);
                            if (c != null && hasStaticRead(c)) { ni = c; break; }
                        }
                    }
                }
            }
            if (ni == null) {
                for (Cls c : classes.values()) {
                    if (hasStaticRead(c)) { ni = c; break; }
                }
            }
        }
        out.put("nativeImage", ni == null ? "" : ni.name);
    }

    boolean hasStaticRead(Cls c) {
        for (Mth m : c.methods) {
            if (m.isStatic() && m.desc.equals("(Ljava/io/InputStream;)L" + c.name + ";")) return true;
        }
        return false;
    }
}
