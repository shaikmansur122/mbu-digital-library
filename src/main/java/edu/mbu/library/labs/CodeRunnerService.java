package edu.mbu.library.labs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compiles and runs Java, C and C++ programs for the code editor.
 * Python and SQL run inside the student's browser instead and never reach this class.
 *
 * Safeguards: a temporary working folder per run, time limits, a cap on output size, a cap on
 * simultaneous runs, and a stripped-down environment (so secrets such as the database password are not
 * visible to student programs). NOTE: the program still runs with the same operating-system permissions as
 * this application, so on a shared server it should be run inside a container or a locked-down user account.
 */
@Service
public class CodeRunnerService {

    public record Result(String stdout, String stderr, Integer exitCode, boolean timedOut, boolean outputTruncated,
                         String problem) {
        static Result problem(String message) {
            return new Result("", "", null, false, false, message);
        }
    }

    private static final Pattern PUBLIC_CLASS = Pattern.compile("public\\s+(?:final\\s+)?class\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Set<String> KEPT_ENV = Set.of("PATH", "SYSTEMROOT", "WINDIR", "TEMP", "TMP", "COMSPEC", "PATHEXT");
    private static final int MAX_OUTPUT_BYTES = 20_000;

    private final boolean enabled;
    private final long compileSeconds;
    private final long runSeconds;
    private final String gcc;
    private final String gpp;
    private final Semaphore slots;

    public CodeRunnerService(@Value("${app.code-runner.enabled:true}") boolean enabled,
                             @Value("${app.code-runner.compile-timeout-seconds:20}") long compileSeconds,
                             @Value("${app.code-runner.run-timeout-seconds:5}") long runSeconds,
                             @Value("${app.code-runner.max-concurrent:2}") int maxConcurrent,
                             @Value("${app.code-runner.gcc:gcc}") String gcc,
                             @Value("${app.code-runner.gpp:g++}") String gpp) {
        this.enabled = enabled;
        this.compileSeconds = compileSeconds;
        this.runSeconds = runSeconds;
        this.gcc = gcc;
        this.gpp = gpp;
        this.slots = new Semaphore(maxConcurrent);
    }

    public Result run(Language language, String code, String stdin) {
        if (!language.isRunsOnServer()) {
            return Result.problem(language.getLabel() + " runs in your browser, not on the server.");
        }
        if (!enabled) {
            return Result.problem("Running code on the server is switched off.");
        }
        try {
            if (!slots.tryAcquire(5, TimeUnit.SECONDS)) {
                return Result.problem("The server is busy running other programs. Please try again in a moment.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.problem("Interrupted.");
        }
        Path dir = null;
        try {
            dir = Files.createTempDirectory("mbu-run-");
            return switch (language) {
                case JAVA -> runJava(dir, code, stdin);
                case C -> runNative(dir, "main.c", gcc, "C", code, stdin);
                case CPP -> runNative(dir, "main.cpp", gpp, "C++", code, stdin);
                default -> Result.problem("Unsupported language.");
            };
        } catch (IOException e) {
            return Result.problem("Could not run the program: " + e.getMessage());
        } finally {
            slots.release();
            deleteQuietly(dir);
        }
    }

    // ---------- Java ----------

    private Result runJava(Path dir, String code, String stdin) throws IOException {
        Matcher m = PUBLIC_CLASS.matcher(code);
        String className = m.find() ? m.group(1) : "Main";
        Files.writeString(dir.resolve(className + ".java"), code, StandardCharsets.UTF_8);

        Path javaBin = Path.of(System.getProperty("java.home"), "bin");
        Path javac = javaBin.resolve(isWindows() ? "javac.exe" : "javac");
        Path java = javaBin.resolve(isWindows() ? "java.exe" : "java");
        if (!Files.isExecutable(javac)) {
            return Result.problem("The Java compiler (javac) is not available on the server. Install a JDK, not just a JRE.");
        }

        Result compile = exec(dir, List.of(javac.toString(), "-encoding", "UTF-8", className + ".java"), "", compileSeconds);
        if (compile.timedOut()) {
            return Result.problem("Compilation took too long and was stopped.");
        }
        if (compile.exitCode() == null || compile.exitCode() != 0) {
            return new Result("", compile.stderr() + compile.stdout(), compile.exitCode(), false, false, "Compilation failed");
        }
        return exec(dir, List.of(java.toString(), "-Xmx128m", "-Xss512k", "-cp", dir.toString(), className), stdin, runSeconds);
    }

    // ---------- C and C++ ----------

    private Result runNative(Path dir, String sourceName, String compiler, String label,
                             String code, String stdin) throws IOException {
        Files.writeString(dir.resolve(sourceName), code, StandardCharsets.UTF_8);
        String exe = isWindows() ? "program.exe" : "program";

        Result compile;
        try {
            compile = exec(dir, List.of(compiler, sourceName, "-o", exe, "-lm"), "", compileSeconds);
        } catch (IOException e) {
            return Result.problem("No " + label + " compiler ('" + compiler + "') is installed on the server, "
                    + "so " + label + " programs cannot be run yet. You can still write and submit your code.");
        }
        if (compile.timedOut()) {
            return Result.problem("Compilation took too long and was stopped.");
        }
        if (compile.exitCode() == null || compile.exitCode() != 0) {
            return new Result("", compile.stderr() + compile.stdout(), compile.exitCode(), false, false, "Compilation failed");
        }
        return exec(dir, List.of(dir.resolve(exe).toString()), stdin, runSeconds);
    }

    // ---------- Process handling ----------

    /** Runs a command in dir, feeding stdin, and returns what it printed. Throws IOException if it cannot be started. */
    private Result exec(Path dir, List<String> command, String stdin, long timeoutSeconds) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(new ArrayList<>(command)).directory(dir.toFile());
        pb.environment().keySet().removeIf(k -> !KEPT_ENV.contains(k.toUpperCase(Locale.ROOT)));
        Process process = pb.start();

        CappedReader out = new CappedReader(process.getInputStream());
        CappedReader err = new CappedReader(process.getErrorStream());
        Thread outThread = new Thread(out, "runner-out");
        Thread errThread = new Thread(err, "runner-err");
        outThread.setDaemon(true);
        errThread.setDaemon(true);
        outThread.start();
        errThread.start();

        // Feed stdin on its own thread so a program that never reads it cannot block us.
        Thread inThread = new Thread(() -> {
            try (OutputStream os = process.getOutputStream()) {
                os.write(stdin.getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // the program exited or closed its input early
            }
        }, "runner-in");
        inThread.setDaemon(true);
        inThread.start();

        boolean finished;
        try {
            finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            finished = false;
        }
        if (!finished) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
        try {
            outThread.join(1000);
            errThread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Integer exit = finished ? process.exitValue() : null;
        return new Result(out.text(), err.text(), exit, !finished, out.truncated() || err.truncated(), null);
    }

    /** Reads a stream but keeps only the first MAX_OUTPUT_BYTES, so a runaway print loop cannot fill memory. */
    private static final class CappedReader implements Runnable {
        private final InputStream in;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private volatile boolean truncated;

        CappedReader(InputStream in) {
            this.in = in;
        }

        @Override
        public void run() {
            byte[] chunk = new byte[4096];
            try {
                int n;
                while ((n = in.read(chunk)) != -1) {
                    synchronized (buffer) {
                        int room = MAX_OUTPUT_BYTES - buffer.size();
                        if (room > 0) {
                            buffer.write(chunk, 0, Math.min(room, n));
                        }
                        if (n > room) {
                            truncated = true;
                        }
                    }
                }
            } catch (IOException ignored) {
                // stream closed because the process was killed
            }
        }

        String text() {
            synchronized (buffer) {
                return buffer.toString(StandardCharsets.UTF_8);
            }
        }

        boolean truncated() {
            return truncated;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                    Files.deleteIfExists(d);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // temp files are cleaned by the OS eventually
        }
    }
}
