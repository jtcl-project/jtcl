package tcl.lang.cmd;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;

import tcl.lang.TclCmdTest;

public class FileNameCmdTest extends TclCmdTest {
	public void testCmd() throws Exception {
		LinkedList<String> expectedFailureList = new LinkedList<String>(Arrays.asList( new String[] {
				/*
				 * These tests fail because 'file link -symbolic' doesn't work in Java.  If someday we add
				 * platform-specific native code, file link -symbolic and glob -types l can be fixes, and these
				 * test should work.
				 */
				"filename-11.17.2",
				"filename-11.17.3",
				"filename-11.17.4",
	            "filename-11.17.7",
	            "filename-11.17.8" 
	        }));
		
		String resName = "/tcl/lang/cmd/fileName.test";
		
		// mostly tests the 'glob' command and some 'file ...' tests
		tclTestResource(resName,expectedFailureList);
	}
}