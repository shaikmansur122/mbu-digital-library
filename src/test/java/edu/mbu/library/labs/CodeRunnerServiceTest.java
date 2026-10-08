package edu.mbu.library.labs;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Java is always available (the tests run on a JDK), so these exercise the real runner. */
class CodeRunnerServiceTest {

    private final CodeRunnerService runner = new CodeRunnerService(true, 20, 3, 2, "gcc", "g++");

    @Test
    void runsAJavaProgramWithInput() {
        String code = "import java.util.*; public class Main { public static void main(String[] a) {"
                + " Scanner s = new Scanner(System.in); System.out.println(s.nextInt() + s.nextInt()); } }";
        var result = runner.run(Language.JAVA, code, "20\n22\n");
        assertThat(result.stdout().trim()).isEqualTo("42");
        assertThat(result.exitCode()).isZero();
        assertThat(result.timedOut()).isFalse();
    }

    @Test
    void reportsCompileErrors() {
        var result = runner.run(Language.JAVA, "public class Main { void x() { int a = \"no\"; } }", "");
        assertThat(result.problem()).isEqualTo("Compilation failed");
        assertThat(result.stderr()).contains("incompatible types");
    }

    @Test
    void stopsAnInfiniteLoop() {
        var result = runner.run(Language.JAVA,
                "public class Main { public static void main(String[] a) { while (true) {} } }", "");
        assertThat(result.timedOut()).isTrue();
    }

    @Test
    void doesNotRunBrowserLanguagesOnTheServer() {
        assertThat(runner.run(Language.PYTHON, "print(1)", "").problem()).contains("browser");
        assertThat(runner.run(Language.SQL, "select 1", "").problem()).contains("browser");
    }

    @Test
    void canBeSwitchedOff() {
        var off = new CodeRunnerService(false, 20, 3, 2, "gcc", "g++");
        assertThat(off.run(Language.JAVA, "class Main {}", "").problem()).contains("switched off");
    }
}
