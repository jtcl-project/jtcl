package tcl.lang.cmd;

import java.util.Arrays;
import java.util.LinkedList;

import tcl.lang.TclCmdTest;

public class IncrCmdTest extends TclCmdTest {
	public void testCmd() throws Exception {
		LinkedList expectedFailureList = new LinkedList(Arrays.asList( new String[] {
			// differences between "compiling" and "executing" in error message:
			// FIXME - change the error text in the IncrCmd to match 8.4
			"incr-1.19", "incr-1.27", "incr-2.19", "incr-2.27"
		}));
			
		String resName = "/tcl/lang/cmd/incr.test";
		tclTestResource(resName, expectedFailureList);
	}
}
