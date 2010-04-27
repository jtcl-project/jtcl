package tcl.lang.cmd;

import tcl.lang.TclCmdTest;

public class NamespaceCmdTest extends TclCmdTest {
	public void testCmd() throws Exception {
		String resName = "/tcl/lang/cmd/namespace.test";
		tclTestResource(resName);
	}
}
