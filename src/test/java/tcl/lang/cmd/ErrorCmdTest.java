package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

import java.util.LinkedList;

public class ErrorCmdTest extends TclCmdTest {

    @Test
    public void errorCommand() throws Exception {
        LinkedList<String> expectedFailureList = expectedFailures(
                // difference in 'invoked from within' and 'while executing'
                "error-1.3",
                "error-2.6"
        );
        String resName = "/tcl/lang/cmd/error.test";
        tclTestResource(resName, expectedFailureList);
    }

}
