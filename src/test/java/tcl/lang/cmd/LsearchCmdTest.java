package tcl.lang.cmd;

import java.util.Collections;

import tcl.lang.TclCmdTest;

public class LsearchCmdTest extends TclCmdTest {
	public void testCmd() throws Exception {
		// FIXME: lsearch-10.1  causes TclObject has been deallocated
		String resName = "/tcl/lang/cmd/lsearch.test";
		tclTestResource("tcltest::configure -verbose {start pass body error skip}", resName, Collections.EMPTY_LIST);
	}
}
