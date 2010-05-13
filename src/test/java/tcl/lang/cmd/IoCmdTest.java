package tcl.lang.cmd;

import tcl.lang.TclCmdTest;

public class IoCmdTest extends TclCmdTest {
	public void testCmd() throws Exception {
		String resName = "/tcl/lang/cmd/ioCmd.test";
		tclTestResource(resName);
	}
}