package tcl.lang.cmd;

import java.util.Arrays;
import java.util.LinkedList;

import tcl.lang.TclCmdTest;

public class ErrorCmdTest  extends TclCmdTest {
	public void testCmd() throws Exception {	LinkedList<String> expectedFailureList = new LinkedList<String>(Arrays.asList( new String[] {
			// difference in 'invoked from within' and 'while executing'
            "error-1.3",
            "error-2.6" 
        }));
		String resName = "/tcl/lang/cmd/error.test";
		tclTestResource(resName, expectedFailureList);
	}
}
