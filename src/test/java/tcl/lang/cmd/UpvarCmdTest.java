
package tcl.lang.cmd;

import java.util.Arrays;
import java.util.LinkedList;

import org.junit.Test;
import tcl.lang.TclCmdTest;

public class UpvarCmdTest extends TclCmdTest {

    @Test
	public void upvarCommand() throws Exception {
		LinkedList expectedFailureList = expectedFailures(
				// these test pass, except order of list returned by "array names" if different
				"upvar-3.5", "upvar-3.6"
			);
		
		String resName = "/tcl/lang/cmd/upvar.test";
		tclTestResource(resName, expectedFailureList);
	}

}
