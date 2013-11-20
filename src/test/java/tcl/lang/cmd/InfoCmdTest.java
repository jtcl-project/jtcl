package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

import java.util.LinkedList;

public class InfoCmdTest extends TclCmdTest {

    @Test
    public void infoCommand() throws Exception {

        LinkedList<String> expectedFailureList = expectedFailures(
                // fails because it counts the number of compiled, not interpreted, commands in C Tcl; JTCL doesn't compile
                "info-3.1",
                // returns the correct values, but non in the same order as C Tcl
                "info-15.4"
        );
        String resName = "/tcl/lang/cmd/info.test";
        tclTestResource(resName, expectedFailureList);
    }

}
