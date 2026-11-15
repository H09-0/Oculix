package org.sikuli.support.runner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sikuli.basics.Settings;
import org.sikuli.support.recorder.generators.JythonCodeGenerator;
import org.sikuli.support.runnerSupport.JythonSupport;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class Jython3IntegrationTest {

    @Test
    void python3SyntaxAndSikuliApiExecuteViaJythonRunner(@TempDir Path tempDir) throws Exception {
        JythonRunner runner = new JythonRunner();
        assertTrue(runner.isSupported());
        runner.init(null);

        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        PrintStream captureOut = new PrintStream(outBuf, true, StandardCharsets.UTF_8);
        runner.redirect(captureOut, captureOut);

        Path script = tempDir.resolve("test_py3.py");
        Files.writeString(script, """
                import sys
                name = "OculiX"
                nums = [x * 2 for x in range(4)]
                print(f"hello {name} {nums} arg={sys.argv[1]}")

                r = Region(10, 20, 100, 200)
                loc = Location(5, 6)
                pat = Pattern("btn.png").similar(0.85)
                Settings.ActionLogs = False
                with Region(0, 0, 50, 50) as wr:
                    print(f"region={r.x},{r.y} loc={loc.x},{loc.y} sim={pat.getSimilar():.2f} wr={wr.w}")
                """, StandardCharsets.UTF_8);

        boolean prevActionLogs = Settings.ActionLogs;
        try {
            IRunner.Options opts = new IRunner.Options();
            int rc = runner.runScript(script.toAbsolutePath().toString(), new String[]{"cli-ok"}, opts);
            assertEquals(0, rc);
            assertFalse(Settings.ActionLogs);

            String output = outBuf.toString(StandardCharsets.UTF_8);
            assertTrue(output.contains("hello OculiX [0, 2, 4, 6] arg=cli-ok"), output);
            assertTrue(output.contains("region=10,20 loc=5,6 sim=0.85 wr=50"), output);
        } finally {
            Settings.ActionLogs = prevActionLogs;
            runner.redirect(null, null);
        }
    }

    @Test
    void reportsErrorLineAndSystemExitCode(@TempDir Path tempDir) throws Exception {
        JythonRunner runner = new JythonRunner();
        runner.init(null);

        Path errScript = tempDir.resolve("bad.py");
        Files.writeString(errScript, """
                x = 1
                y = 2
                z = 1 / 0
                """, StandardCharsets.UTF_8);

        IRunner.Options opts = new IRunner.Options();
        int rc = runner.runScript(errScript.toAbsolutePath().toString(), new String[0], opts);
        assertEquals(1, rc);
        assertEquals(3, opts.getErrorLine());

        Path exitScript = tempDir.resolve("exit_code.py");
        Files.writeString(exitScript, """
                import sys
                sys.exit(7)
                """, StandardCharsets.UTF_8);
        int exitRc = runner.runScript(exitScript.toAbsolutePath().toString(), new String[0], new IRunner.Options());
        assertEquals(7, exitRc);
    }

    @Test
    void abortCancelsInfiniteLoopCleanly(@TempDir Path tempDir) throws Exception {
        JythonRunner runner = new JythonRunner();
        runner.init(null);

        Path marker = tempDir.resolve("started.txt");
        Path loopScript = tempDir.resolve("loop.py");
        Files.writeString(loopScript,
                "import io\n"
                + "with io.open(r'" + marker.toAbsolutePath().toString().replace("\\", "/") + "', 'w') as f:\n"
                + "    f.write('go')\n"
                + "while True:\n"
                + "    pass\n",
                StandardCharsets.UTF_8);

        var exec = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> fut = exec.submit(() ->
                    runner.runScript(loopScript.toAbsolutePath().toString(), new String[0], new IRunner.Options()));

            long deadline = System.currentTimeMillis() + 5000;
            while (!Files.exists(marker) && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertTrue(Files.exists(marker), "Script should start before abort");
            Thread.sleep(50);
            runner.abort();
            int rc = fut.get(5, TimeUnit.SECONDS);
            assertEquals(0, rc);
        } finally {
            exec.shutdownNow();
        }
    }

    @Test
    void supportsParenthesizedImportsPep414UnicodeAndSikuliBundles(@TempDir Path tempDir) throws Exception {
        JythonRunner runner = new JythonRunner();
        runner.init(null);

        // Create a .sikuli helper bundle next to the main script
        Path helperBundle = tempDir.resolve("myhelper.sikuli");
        Files.createDirectories(helperBundle);
        Files.writeString(helperBundle.resolve("myhelper.py"), "GREETING = 'from-bundle'\n", StandardCharsets.UTF_8);

        JythonCodeGenerator codeGen = new JythonCodeGenerator();
        String generatedType = codeGen.typeText("hi \"py3\"", new String[0]);
        assertFalse(generatedType.startsWith("type(u\""), "Should emit clean Python 3 string: " + generatedType);

        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        PrintStream captureOut = new PrintStream(outBuf, true, StandardCharsets.UTF_8);
        runner.redirect(captureOut, captureOut);

        Path mainPy = tempDir.resolve("bundle_caller.py");
        Files.writeString(mainPy, """
                from sikuli import (
                    Region,
                    Location,
                )
                import myhelper
                uval = u"pep414-ok"
                print(f"{myhelper.GREETING}|{uval}|{Region(1,2,3,4).w}")
                """, StandardCharsets.UTF_8);

        try {
            int rc = runner.runScript(mainPy.toAbsolutePath().toString(), new String[0], new IRunner.Options());
            assertEquals(0, rc);
            String out = outBuf.toString(StandardCharsets.UTF_8);
            assertTrue(out.contains("from-bundle|pep414-ok|3"), out);
        } finally {
            runner.redirect(null, null);
        }
    }
}
