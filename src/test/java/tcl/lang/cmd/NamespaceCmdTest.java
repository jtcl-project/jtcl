package tcl.lang.cmd;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;

import tcl.lang.TclCmdTest;

public class NamespaceCmdTest extends TclCmdTest {
	public void testCmd() throws Exception {
		LinkedList<String> expectedFailureList = new LinkedList<String>(Arrays.asList( new String[] {
				// sorting issues
				"namespace-26.6"
		}));
		String resName = "/tcl/lang/cmd/namespace.test";
		tclTestResource(resName, expectedFailureList);
	}
}
