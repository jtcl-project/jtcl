package tcl.lang.cmd;

import tcl.lang.TclCmdTest;

public class AppendCmdTest extends TclCmdTest {
	public void testCmd() throws Exception {
		String resName = "/tcl/lang/cmd/append.test";
		tclTestResource(resName);
	}
	
	public void testAppendComp() throws Exception {
		String resName = "/tcl/lang/cmd/appendComp.test";
		tclTestResource(resName);
	}
}