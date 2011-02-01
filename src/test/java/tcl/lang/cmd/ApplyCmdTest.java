package tcl.lang.cmd;

import java.util.Arrays;
import java.util.LinkedList;

import tcl.lang.TclCmdTest;

public class ApplyCmdTest extends TclCmdTest {
	public void testCmd() throws Exception {
		LinkedList<String> expectedFailureList = new LinkedList<String>(Arrays.asList( new String[] {
				// fails because of error message differences:
	            "apply-4.3", "apply-4.4", "apply-4.5", "apply-5.1",
	            // returns the correct values, but non in the same order as C Tcl
	            "apply-8.2", "apply-8.3", "apply-8.4", "apply-8.5", "apply-8.6", "apply-8.7", 
	            "apply-8.8", "apply-8.9", "apply-8.10"
	        }));
		String resName = "/tcl/lang/cmd/apply.test";
		tclTestResource(resName, expectedFailureList);
	}
}
