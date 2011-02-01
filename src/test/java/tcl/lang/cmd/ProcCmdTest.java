package tcl.lang.cmd;

import java.util.Arrays;
import java.util.LinkedList;

import tcl.lang.TclCmdTest;

public class ProcCmdTest extends TclCmdTest {
	public void testCmd() throws Exception {
		LinkedList<String> expectedFailureList = new LinkedList<String>(Arrays.asList( new String[] {
				// fails because of error message differences:
	            "proc-1.7", "proc-1.8"
	        }));
		String resName = "/tcl/lang/cmd/proc.test";
		tclTestResource(resName, expectedFailureList);
	}
}
