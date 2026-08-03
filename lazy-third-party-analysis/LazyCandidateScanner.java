import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import javax.lang.model.element.Modifier;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;

/**
 * Recall-oriented source scanner used to build the third-party lazy-site inventory.
 *
 * <p>This deliberately over-reports. A human must decide whether a hit is a cache,
 * whether repeated attempts are safe, whether winner selection matters, and whether
 * failure is deterministic. It is not the source of the chapter's classifications.
 */
public final class LazyCandidateScanner {
    private static final int MAX_METHOD_CHARS = 240;

    private record Field(String name, Set<Modifier> modifiers, String initializer, long line) {}

    private final Path root;
    private final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

    private LazyCandidateScanner(Path root) {
        this.root = root;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("usage: LazyCandidateScanner <repository-root>");
        }
        new LazyCandidateScanner(Path.of(args[0]).toAbsolutePath().normalize()).run();
    }

    private void run() throws IOException {
        System.out.println("file\tline\tclass\tmethod\tfield\tmodifiers\tinitializer\tconditions\tmethod_source");
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(this::isProductionJava).sorted().forEach(this::scanFileUnchecked);
        }
    }

    private boolean isProductionJava(Path path) {
        if (!path.toString().endsWith(".java")) {
            return false;
        }
        String p = root.relativize(path).toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        return !p.contains("/src/test/")
                && !p.contains("/src/it/")
                && !p.contains("/src/jmh/")
                && !p.contains("/testfixtures/")
                && !p.contains("/benchmark")
                && !p.contains("/samples/")
                && !p.contains("/examples/")
                && !p.contains("/generated/")
                && !p.startsWith("src/test/")
                && !p.startsWith("src/it/");
    }

    private void scanFileUnchecked(Path file) {
        try {
            scanFile(file);
        } catch (Exception exception) {
            System.err.println("Could not scan " + file + ": " + exception);
        }
    }

    private void scanFile(Path file) throws Exception {
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(null, null, null)) {
            Iterable<? extends JavaFileObject> inputs = manager.getJavaFileObjects(file);
            JavacTask task = (JavacTask) compiler.getTask(null, manager, null,
                    List.of("-proc:none", "-XDshould-stop.at=PARSE"), null, inputs);
            CompilationUnitTree unit = task.parse().iterator().next();
            Trees trees = Trees.instance(task);
            SourcePositions positions = trees.getSourcePositions();
            new TreeScanner<Void, Deque<String>>() {
                @Override
                public Void visitClass(ClassTree node, Deque<String> owners) {
                    Deque<String> nextOwners = new ArrayDeque<>(owners);
                    String simpleName = node.getSimpleName().toString();
                    if (!simpleName.isEmpty()) {
                        nextOwners.addLast(simpleName);
                    }
                    List<Field> fields = new ArrayList<>();
                    for (Tree member : node.getMembers()) {
                        if (member instanceof VariableTree variable) {
                            long start = positions.getStartPosition(unit, variable);
                            long line = start < 0 ? -1 : unit.getLineMap().getLineNumber(start);
                            fields.add(new Field(variable.getName().toString(),
                                    Set.copyOf(variable.getModifiers().getFlags()),
                                    variable.getInitializer() == null ? "<default>" : variable.getInitializer().toString(),
                                    line));
                        }
                    }
                    for (Tree member : node.getMembers()) {
                        if (member instanceof MethodTree method && method.getBody() != null
                                && !method.getName().contentEquals("<init>")) {
                            inspectMethod(unit, positions, nextOwners, fields, method);
                        } else if (member instanceof ClassTree nested) {
                            scan(nested, nextOwners);
                        }
                    }
                    return null;
                }
            }.scan(unit, new ArrayDeque<>());
        }
    }

    private void inspectMethod(CompilationUnitTree unit, SourcePositions positions,
            Deque<String> owners, List<Field> fields, MethodTree method) {
        Set<String> assigned = new HashSet<>();
        List<String> conditions = new ArrayList<>();
        List<String> returns = new ArrayList<>();
        Set<String> identifiers = new HashSet<>();
        new TreeScanner<Void, Void>() {
            @Override public Void visitClass(ClassTree node, Void unused) { return null; }
            @Override public Void visitAssignment(AssignmentTree node, Void unused) {
                assigned.add(lastName(node.getVariable().toString()));
                return super.visitAssignment(node, unused);
            }
            @Override public Void visitCompoundAssignment(CompoundAssignmentTree node, Void unused) {
                assigned.add(lastName(node.getVariable().toString()));
                return super.visitCompoundAssignment(node, unused);
            }
            @Override public Void visitUnary(UnaryTree node, Void unused) {
                switch (node.getKind()) {
                    case PREFIX_INCREMENT, PREFIX_DECREMENT, POSTFIX_INCREMENT, POSTFIX_DECREMENT ->
                            assigned.add(lastName(node.getExpression().toString()));
                    default -> { }
                }
                return super.visitUnary(node, unused);
            }
            @Override public Void visitIf(IfTree node, Void unused) {
                conditions.add(node.getCondition().toString());
                return super.visitIf(node, unused);
            }
            @Override public Void visitReturn(ReturnTree node, Void unused) {
                if (node.getExpression() != null) {
                    returns.add(node.getExpression().toString());
                }
                return super.visitReturn(node, unused);
            }
            @Override public Void visitIdentifier(com.sun.source.tree.IdentifierTree node, Void unused) {
                identifiers.add(node.getName().toString());
                return super.visitIdentifier(node, unused);
            }
        }.scan(method.getBody(), null);

        String source = sourceOf(unit, positions, method).replaceAll("\\s+", " ").trim();
        if (source.length() > MAX_METHOD_CHARS) {
            source = source.substring(0, MAX_METHOD_CHARS) + "...";
        }
        for (Field field : fields) {
            if (!assigned.contains(field.name()) || !identifiers.contains(field.name())) {
                continue;
            }
            boolean guarded = conditions.stream().anyMatch(c -> containsWord(c, field.name()));
            boolean returned = returns.stream().anyMatch(r -> containsWord(r, field.name()));
            boolean defaultish = field.initializer().equals("<default>")
                    || field.initializer().equals("null")
                    || field.initializer().equals("false")
                    || field.initializer().equals("0");
            if (!guarded && !(returned && defaultish)) {
                continue;
            }
            String matchingConditions = conditions.stream()
                    .filter(c -> containsWord(c, field.name())).reduce((a, b) -> a + " && " + b).orElse("");
            System.out.printf("%s\t%d\t%s\t%s\t%s\t%s\t%s\t%s\t%s%n",
                    root.relativize(Path.of(unit.getSourceFile().toUri())), field.line(),
                    String.join(".", owners), method.getName(), field.name(), field.modifiers(),
                    clean(field.initializer()), clean(matchingConditions), clean(source));
        }
    }

    private static String sourceOf(CompilationUnitTree unit, SourcePositions positions, Tree tree) {
        try {
            CharSequence chars = unit.getSourceFile().getCharContent(true);
            int start = (int) positions.getStartPosition(unit, tree);
            int end = (int) positions.getEndPosition(unit, tree);
            return start >= 0 && end >= start ? chars.subSequence(start, end).toString() : tree.toString();
        } catch (IOException exception) {
            return tree.toString();
        }
    }

    private static String lastName(String expression) {
        int dot = expression.lastIndexOf('.');
        return dot < 0 ? expression : expression.substring(dot + 1);
    }

    private static boolean containsWord(String text, String word) {
        return text.matches("(?s).*\\b" + java.util.regex.Pattern.quote(word) + "\\b.*");
    }

    private static String clean(String text) {
        return text.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
