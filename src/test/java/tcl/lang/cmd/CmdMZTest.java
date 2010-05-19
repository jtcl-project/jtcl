package tcl.lang.cmd;

import java.util.Arrays;
import java.util.LinkedList;

import tcl.lang.TclCmdTest;

public class CmdMZTest extends TclCmdTest {
	public void testCmd() throws Exception {
		LinkedList expectedFailureList = new LinkedList(Arrays.asList(new String[] {
				// differences in error message, not enough to worry about
				"cmdMZ-3.5" 
			}));

		String resName = "/tcl/lang/cmd/cmdMZ.test";
		tclTestResource(resName, expectedFailureList);
	}
}
