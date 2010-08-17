package tcl.lang.cmd;

import java.util.Arrays;
import java.util.LinkedList;

import tcl.lang.TclCmdTest;

public class CmdAHTest extends TclCmdTest {
	public void testCmd() throws Exception {
		LinkedList<String> expectedFailureList = new LinkedList<String>(Arrays.asList(new String[] {
			// these fail because 'file atime', 'file mtime', 'file stat' and 'file link' are incomplete
		    // because of JVM restrictions.  Could fix with Java 1.7 or native code
			"cmdAH-20.2", "cmdAH-24.3", "cmdAH-28.3", "cmdAH-28.4", "cmdAH-28.8", "cmdAH-28.12", "cmdAH-29.4.1"
		}));

		String resName = "/tcl/lang/cmd/cmdAH.test";
		tclTestResource(resName, expectedFailureList);
	}
}
