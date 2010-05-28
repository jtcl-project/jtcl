package tcl.lang.cmd;

import java.util.Arrays;
import java.util.LinkedList;

import tcl.lang.TclCmdTest;

public class SourceCmdTest extends TclCmdTest {
	public void testCmd() throws Exception {
		LinkedList expectedFailureList = new LinkedList(Arrays.asList( new String[] {
			// FIXME: differences in error mesasges:
			"source-2.3", "source-2.6"
		}));
		
		String resName = "/tcl/lang/cmd/source.test";
		tclTestResource(resName, expectedFailureList);
	}
}
