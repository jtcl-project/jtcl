package tcl.lang.cmd;

import java.util.Arrays;
import java.util.LinkedList;

import tcl.lang.TclCmdTest;

public class ForCmdTest extends TclCmdTest {
	public void testCmd() throws Exception {
		LinkedList<String> expectedFailureList = new LinkedList<String>(Arrays.asList( new String[] {
			// FIXME - can we fix the error callback messages?
			// widespread, pesky "invoked from within" instead of "while executing" in error message
			"for-1.8", "for-1.12"
		}));
		
		String resName = "/tcl/lang/cmd/for.test";
		tclTestResource(resName, expectedFailureList);
	}
}
