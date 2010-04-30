package tcl.lang.cmd;

import java.util.Collections;

import tcl.lang.TclCmdTest;

public class BinaryCmdTest extends TclCmdTest {
	public void testCmd() throws Exception {
		String resName = "/tcl/lang/cmd/binary.test";
		tclTestResource(TCLTEST_VERBOSE, resName, Collections.EMPTY_LIST);
	}
}
