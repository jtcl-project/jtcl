package tcl.lang.cmd;

import tcl.lang.TclCmdTest;

public class FileNameCmdTest extends TclCmdTest {
	public void testCmd() throws Exception {
		// mostly tests the 'glob' command and some 'file ...' tests
		String resName = "/tcl/lang/cmd/fileName.test";
		tclTestResource(resName);
	}
}