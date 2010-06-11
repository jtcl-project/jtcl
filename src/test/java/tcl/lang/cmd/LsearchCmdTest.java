package tcl.lang.cmd;

import java.util.Arrays;
import java.util.Collections;

import tcl.lang.TclCmdTest;

public class LsearchCmdTest extends TclCmdTest {
	public void testCmd() throws Exception {
		String resName = "/tcl/lang/cmd/lsearch.test";
		tclTestResource(resName, Collections.EMPTY_LIST);
	}
}
