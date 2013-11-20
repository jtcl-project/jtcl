package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

public class ConcatCmdText  extends TclCmdTest {

    @Test
	public void concatCommand() throws Exception {
		String resName = "/tcl/lang/cmd/concat.test";
		tclTestResource(resName);
	}

}
