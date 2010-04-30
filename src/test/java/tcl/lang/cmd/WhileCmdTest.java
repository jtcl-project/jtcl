package tcl.lang.cmd;

import java.util.Arrays;
import java.util.LinkedList;

import tcl.lang.TclCmdTest;

public class WhileCmdTest extends TclCmdTest {
	public void testCmd() throws Exception {
		LinkedList expectedFailureList = new LinkedList(Arrays.asList( new String[] {
				// differences between "compiling" and "executing" in error message:
				// FIXME - change the error text in the WhileCmd to match 8.4
				"while-1.2", "while-1.8", "while-4.3", "while-4.9"
			}));
			
		String resName = "/tcl/lang/cmd/while.test";
		tclTestResource(resName, expectedFailureList);
	}
}
