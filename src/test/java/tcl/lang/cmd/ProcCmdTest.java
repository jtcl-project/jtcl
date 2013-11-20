package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

import java.util.LinkedList;

public class ProcCmdTest extends TclCmdTest {

    @Test
    public void procCommand() throws Exception {
        LinkedList<String> expectedFailureList = expectedFailures(
                // fails because of error message differences:
                "proc-1.7", "proc-1.8"
        );
        String resName = "/tcl/lang/cmd/proc.test";
        tclTestResource(resName, expectedFailureList);
    }

}
