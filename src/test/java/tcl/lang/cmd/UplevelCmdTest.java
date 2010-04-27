package tcl.lang.cmd;

import tcl.lang.TclCmdTest;

public class UplevelCmdTest extends TclCmdTest {
	public void testCmd() throws Exception {
		String resName = "/tcl/lang/cmd/uplevel.test";
		tclTestResource(resName);
	}
}
