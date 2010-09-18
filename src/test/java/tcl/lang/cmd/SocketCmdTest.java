package tcl.lang.cmd;

import java.util.Collections;

import tcl.lang.TclCmdTest;

public class SocketCmdTest extends TclCmdTest {
	public void testCmd() throws Exception {
		String resName = "/tcl/lang/cmd/socket.test";
		/* need TCLTEST_NAMEOFEXECUTABLE in order to properly initialize stdio testConstraint */
		tclTestResource(TCLTEST_NAMEOFEXECUTABLE, resName, Collections.EMPTY_LIST);
	}
}
