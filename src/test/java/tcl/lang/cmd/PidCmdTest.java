package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

import java.util.LinkedList;

public class PidCmdTest extends TclCmdTest {

    @Test
    public void pidCommand() throws Exception {
        String resName = "/tcl/lang/cmd/pid.test";
        LinkedList<String> expectedFailureList = expectedFailures(
                "pid-1.1"
        );
        tclTestResource(TCLTEST_NAMEOFEXECUTABLE, resName, expectedFailureList);
    }

}
