package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

public class UplevelCmdTest extends TclCmdTest {

    @Test
	public void uplevelCommand() throws Exception {
		String resName = "/tcl/lang/cmd/uplevel.test";
		tclTestResource(resName);
	}

}
