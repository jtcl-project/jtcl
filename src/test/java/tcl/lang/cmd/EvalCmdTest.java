package tcl.lang.cmd;

import java.util.Arrays;
import java.util.LinkedList;

import tcl.lang.TclCmdTest;

public class EvalCmdTest  extends TclCmdTest {
	public void testCmd() throws Exception {
		LinkedList<String> expectedFailureList = new LinkedList<String>(Arrays.asList( new String[] {
				// eval-46.5 and eval-46.6 assume integers are 32-bits; in JTCl they are 64-bits 
	            "eval-46.5",
	            "eval-46.6" 
	        }));
		String resName = "/tcl/lang/cmd/eval.test";
		tclTestResource(resName);
	}
}
