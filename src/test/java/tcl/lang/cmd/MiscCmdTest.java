package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

import java.util.LinkedList;

public class MiscCmdTest extends TclCmdTest {

    @Test
    public void basic() throws Exception {
        LinkedList<String> expectedFailureList = expectedFailures(
                "basic-24.3" // 'info commands' result in a different order; this is not a bug
        );

        String resName = "/tcl/lang/cmd/basic.test";
        tclTestResource(TCLTEST_NAMEOFEXECUTABLE, resName, expectedFailureList);
    }

    @Test
    public void io() throws Exception {
        LinkedList<String> expectedFailureList = expectedFailures(
                "io-29.27"
        );
        String resName = "/tcl/lang/cmd/io.test";
        tclTestResource(TCLTEST_NAMEOFEXECUTABLE, resName, expectedFailureList);
    }

    @Test
    public void util() throws Exception {
        String resName = "/tcl/lang/cmd/util.test";
        tclTestResource(resName);
    }

    @Test
    public void var() throws Exception {
        String resName = "/tcl/lang/cmd/var.test";
        tclTestResource(resName);
    }

}