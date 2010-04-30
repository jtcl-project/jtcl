package tcl.lang.cmd;

import java.util.Arrays;
import java.util.LinkedList;

import tcl.lang.TclCmdTest;

public class CmdMZTest  extends TclCmdTest {
	public void testCmd() throws Exception {
		LinkedList expectedFailureList = new LinkedList(Arrays.asList( new String[] {
				// differences between "compiling" and "executing" in error message:
				// FIXME - change the error text in the Cmd files to match 8.4
				"cmdMZ-3.5", "cmdMZ-5.1", "cmdMZ-5.2"
			}));

		String resName = "/tcl/lang/cmd/cmdMZ.test";
		tclTestResource(resName, expectedFailureList);
	}
}
