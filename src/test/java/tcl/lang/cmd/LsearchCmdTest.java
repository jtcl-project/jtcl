package tcl.lang.cmd;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;

import tcl.lang.TclCmdTest;

public class LsearchCmdTest extends TclCmdTest {
	public void testCmd() throws Exception {
		LinkedList expectedFailureList = new LinkedList(Arrays.asList(new String[] {
			// FIXME: differences in regexp error message
			"lsearch-2.6" 
		}));
		
		String resName = "/tcl/lang/cmd/lsearch.test";
		tclTestResource(resName, expectedFailureList);
	}
}
