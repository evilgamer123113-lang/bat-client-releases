import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.lang.model.type.*;
import javax.lang.model.element.*;
import javax.lang.model.util.Types;
import javax.tools.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Resolves all local-variable `var` declarations in a directory of valid Java 16+
 * sources using javac's own type checker, replacing `var` with explicit Java 8 types.
 */
public final class VarResolver {
    public static void main(String[] args) throws Exception {
        Path srcDir = Paths.get(args[0]);
        String cp = args[1].startsWith("@") ? Files.readString(Paths.get(args[1].substring(1))).trim() : args[1];
        List<Path> files = new ArrayList<>();
        try (var stream = Files.walk(srcDir)) {
            stream.filter(p -> p.toString().endsWith(".java")).forEach(files::add);
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = fm.getJavaFileObjectsFromPaths(files);
            List<String> opts = Arrays.asList("-classpath", cp, "--release", "16", "-proc:none");
            JavacTask task = (JavacTask) compiler.getTask(null, fm, d -> {}, opts, null, units);
            Iterable<? extends CompilationUnitTree> parsed = task.parse();
            try {
                task.analyze();
            } catch (Throwable t) {
                System.err.println("VarResolver analyze warning: " + t);
            }
            Trees trees = Trees.instance(task);
            SourcePositions pos = trees.getSourcePositions();
            Types types = task.getTypes();
            int replaced = 0;

            for (CompilationUnitTree cu : parsed) {
                Path file = Paths.get(cu.getSourceFile().toUri());
                String text = Files.readString(file);
                List<int[]> edits = new ArrayList<>();
                List<String> typesOut = new ArrayList<>();

                new TreePathScanner<Void, Void>() {
                    @Override
                    public Void visitVariable(VariableTree node, Void p) {
                        long ns = pos.getStartPosition(cu, node);
                        long ne = pos.getEndPosition(cu, node);
                        if (ns >= 0 && ne > ns && ne <= text.length()) {
                            String slice = text.substring((int) ns, (int) Math.min(ne, ns + 40));
                            java.util.regex.Matcher vm = java.util.regex.Pattern.compile("\\bvar\\b").matcher(slice);
                            if (vm.find()) {
                                int s = (int) ns + vm.start();
                                int e = (int) ns + vm.end();
                                TypeMirror tm = trees.getTypeMirror(getCurrentPath());
                                if (tm != null && tm.getKind() != TypeKind.ERROR && tm.getKind() != TypeKind.NONE) {
                                    String resolved = renderType(tm, types);
                                    if (resolved != null) {
                                        edits.add(new int[]{s, e});
                                        typesOut.add(resolved);
                                    }
                                }
                            }
                        }
                        return super.visitVariable(node, p);
                    }
                }.scan(cu, null);

                if (!edits.isEmpty()) {
                    StringBuilder sb = new StringBuilder(text);
                    for (int i = edits.size() - 1; i >= 0; i--) {
                        int[] span = edits.get(i);
                        sb.replace(span[0], span[1], typesOut.get(i));
                        replaced++;
                    }
                    Files.writeString(file, sb.toString());
                }
            }
            System.out.println("VAR_RESOLVED_COUNT=" + replaced);
        }
    }

    private static String renderType(TypeMirror tm, Types types) {
        if (tm.getKind() == TypeKind.INTERSECTION) {
            return renderType(((IntersectionType) tm).getBounds().get(0), types);
        }
        if (tm.getKind() == TypeKind.WILDCARD) {
            WildcardType wt = (WildcardType) tm;
            return wt.getExtendsBound() != null ? renderType(wt.getExtendsBound(), types) : "Object";
        }
        if (tm.getKind() == TypeKind.DECLARED) {
            DeclaredType dt = (DeclaredType) tm;
            Element el = dt.asElement();
            if (el instanceof TypeElement && ((TypeElement) el).getQualifiedName().toString().isEmpty()) {
                List<? extends TypeMirror> ifaces = ((TypeElement) el).getInterfaces();
                if (!ifaces.isEmpty()) return renderType(ifaces.get(0), types);
                return renderType(((TypeElement) el).getSuperclass(), types);
            }
        }
        String s = tm.toString();
        if (s.contains("<any>") || s.contains("? extends ") && s.startsWith("capture#")) {
            return "Object";
        }
        // Replace capture#N of ? extends X -> X
        if (s.contains("capture#")) {
            return "Object";
        }
        return s.replace('$', '.');
    }
}
