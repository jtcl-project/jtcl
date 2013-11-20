package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

public class ListCmdTest extends TclCmdTest {

    @Test
	public void listCommand() throws Exception {
		String resName = "/tcl/lang/cmd/list.test";
		tclTestResource(resName);
	}

}
