package tcl.lang.cmd;

import java.util.Arrays;
import java.util.LinkedList;

import tcl.lang.TclCmdTest;

public class ForCmdTest extends TclCmdTest {
	public void testCmd() throws Exception {
		LinkedList expectedFailureList = new LinkedList(Arrays.asList( new String[] {
			// differences in error messages:
			// FIXME - can we fix the error callback messages?
			"for-1.8", "for-1.12", "for-6.7"
		}));
		
		String resName = "/tcl/lang/cmd/for.test";
		tclTestResource(resName, expectedFailureList);
	}
}
