package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

import java.util.LinkedList;

public class WhileCmdTest extends TclCmdTest {

    @Test
    public void whileCommand() throws Exception {
        LinkedList<String> expectedFailureList = expectedFailures(
                // widespread, pesky "invoked from within" instead of "while executing" in error message
                "while-1.2", "while-1.8"
        );

        String resName = "/tcl/lang/cmd/while.test";
        tclTestResource(resName, expectedFailureList);
    }

}
