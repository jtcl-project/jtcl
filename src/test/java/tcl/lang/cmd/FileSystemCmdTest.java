package tcl.lang.cmd;

import java.util.Arrays;
import java.util.LinkedList;

import tcl.lang.TclCmdTest;

public class FileSystemCmdTest extends TclCmdTest {
	public void testCmd() throws Exception {
		LinkedList expectedFailureList = new LinkedList(Arrays.asList( new String[] {
			"filesystem-6.3", "filesystem-6.4", "filesystem-6.14", "filesystem-6.15", "filesystem-6.24",
			"filesystem-6.30"
		}));
			
		String resName = "/tcl/lang/cmd/fileSystem.test";
		tclTestResource(resName, expectedFailureList);
	}
}