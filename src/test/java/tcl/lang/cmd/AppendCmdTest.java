package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

public class AppendCmdTest extends TclCmdTest {

    @Test
	public void appendCommand() throws Exception {
		String resName = "/tcl/lang/cmd/append.test";
		tclTestResource(resName);
	}

    @Test
	public void appendCompCommand() throws Exception {
		String resName = "/tcl/lang/cmd/appendComp.test";
		tclTestResource(resName);
	}

}