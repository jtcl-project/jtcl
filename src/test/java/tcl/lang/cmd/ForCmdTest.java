package tcl.lang.cmd;

import java.util.Arrays;
import java.util.LinkedList;

import tcl.lang.TclCmdTest;

public class ForCmdTest extends TclCmdTest {
	public void testCmd() throws Exception {
		LinkedList expectedFailureList = new LinkedList(Arrays.asList( new String[] {
			// differences between "compiling" and "executing" in error message:
			// FIXME - change the error text in the ForCmd to match 8.4
			"for-1.2", "for-1.4", "for-1.8", "for-1.12", "for-6.6", "for-6.7", "for-6.9", "for-6.13"
		}));
		
		String resName = "/tcl/lang/cmd/for.test";
		tclTestResource(resName, expectedFailureList);
	}
}
