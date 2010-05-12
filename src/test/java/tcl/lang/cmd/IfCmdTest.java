package tcl.lang.cmd;

import java.util.Arrays;
import java.util.LinkedList;

import tcl.lang.TclCmdTest;

public class IfCmdTest extends TclCmdTest {
	public void testCmd() throws Exception {
		LinkedList expectedFailureList = new LinkedList(Arrays.asList( new String[] {
			// differences between "compiling" and "executing" in error message:
			// FIXME - change the error text in the IfrCmd to match 8.4
			"if-1.3", "if-1.10", "if-2.4", "if-3.4", "if-5.3", "if-5.10", "if-6.4", "if-7.4", "if-10.6"
		}));
			
		String resName = "/tcl/lang/cmd/if.test";
		tclTestResource(resName, expectedFailureList);
	}
}
