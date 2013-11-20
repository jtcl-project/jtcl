package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

import java.util.LinkedList;

public class IncrCmdTest extends TclCmdTest {

    @Test
    public void incrCmd() throws Exception {

        LinkedList expectedFailureList = expectedFailures(
                // differences between "compiling" and "executing" in error message:
                // FIXME - change the error text in the IncrCmd to match 8.4
                "incr-1.19", "incr-1.27", "incr-2.19", "incr-2.27"
        );

        String resName = "/tcl/lang/cmd/incr.test";
        tclTestResource(resName, expectedFailureList);
    }

}
