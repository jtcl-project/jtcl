package tcl.lang.cmd;

import tcl.lang.TclCmdTest;

import java.util.Arrays;
import java.util.LinkedList;

public class PidCmdTest extends TclCmdTest {
	public void testCmd() throws Exception {
		String resName = "/tcl/lang/cmd/pid.test";
        LinkedList<String> expectedFailureList = new LinkedList<String>(Arrays.asList(new String[]{
                "pid-1.1"
        }));
        tclTestResource(TCLTEST_NAMEOFEXECUTABLE,resName, expectedFailureList);
	}
}
