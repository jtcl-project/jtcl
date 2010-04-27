package tcl.lang.cmd;

import tcl.lang.TclCmdTest;

public class PackageCmdTest extends TclCmdTest {
	public void testCmd() throws Exception {
		String resName = "/tcl/lang/cmd/package.test";
		tclTestResource(resName);
	}
}
