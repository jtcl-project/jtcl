package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

import java.util.LinkedList;

public class InterpCmdTest extends TclCmdTest {

    @Test
    public void interpCommand() throws Exception {
        LinkedList<String> expectedFailureList = expectedFailures(
                // different list from [interp hidden] because the 'load' command is not implemented in JTCL
                "interp-21.5",
                "interp-21.8",
                // recursion limit is actually inherited by the slave interpreter as expected, but the nested proc
                // call count is one fewer in JTCL than in TCL.  My best guess is that this is due to TCL compiling
                // some of the contents of the recursive proc in these tests, so that there's no eval internal
                // to the proc (DJB)
                "interp-29.4.1",
                "interp-29.4.2"
        );
        String resName = "/tcl/lang/cmd/interp.test";
        tclTestResource(resName, expectedFailureList);
    }

}
