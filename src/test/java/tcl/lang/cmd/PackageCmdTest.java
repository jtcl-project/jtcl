package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

public class PackageCmdTest extends TclCmdTest {

    @Test
	public void packageCommand() throws Exception {
		String resName = "/tcl/lang/cmd/package.test";
		tclTestResource(resName);
	}

}
