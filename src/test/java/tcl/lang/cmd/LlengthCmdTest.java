package tcl.lang.cmd;

import java.util.Collections;

import tcl.lang.TclCmdTest;

public class LlengthCmdTest extends TclCmdTest {
	public void testCmd() throws Exception {
		String resName = "/tcl/lang/cmd/llength.test";
		tclTestResource(resName);
	}
}
