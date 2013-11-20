package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

import java.util.LinkedList;

public class CmdAHTest extends TclCmdTest {

    @Test
    public void cmdAHCommand() throws Exception {
        LinkedList<String> expectedFailureList = expectedFailures(
                // these fail because 'file atime', 'file mtime', 'file stat' and 'file link' are incomplete
                // because of JVM restrictions.  Could fix with Java 1.7 or native code
                "cmdAH-20.2", "cmdAH-24.3", "cmdAH-28.3", "cmdAH-28.4", "cmdAH-28.8", "cmdAH-28.12", "cmdAH-29.4.1"
        );

        String resName = "/tcl/lang/cmd/cmdAH.test";
        tclTestResource(resName, expectedFailureList);
    }

}
