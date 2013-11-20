package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

public class StringCmdTest extends TclCmdTest {

    @Test
	public void stringCommand() throws Exception {
		String resName = "/tcl/lang/cmd/string.test";
		tclTestResource(resName);
	}

    @Test
	public void stringCompCommand() throws Exception {
		String resName = "/tcl/lang/cmd/stringComp.test";
		tclTestResource(resName);
	}

}
