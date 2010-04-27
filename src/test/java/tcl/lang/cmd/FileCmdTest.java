package tcl.lang.cmd;

import tcl.lang.TclCmdTest;

public class FileCmdTest extends TclCmdTest {
	public void testCmd() throws Exception {
		String resName = "/tcl/lang/cmd/fCmd.test";
		tclTestResource(resName);
	}
}
