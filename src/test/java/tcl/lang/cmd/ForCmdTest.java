package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

import java.util.LinkedList;

public class ForCmdTest extends TclCmdTest {

    @Test
    public void forCommand() throws Exception {
        LinkedList<String> expectedFailureList = expectedFailures(
                // FIXME - can we fix the error callback messages?
                // widespread, pesky "invoked from within" instead of "while executing" in error message
                "for-1.8", "for-1.12"
        );

        String resName = "/tcl/lang/cmd/for.test";
        tclTestResource(resName, expectedFailureList);
    }

}
