package tcl.lang.cmd;

import java.util.Collections;

import org.junit.Test;
import tcl.lang.TclCmdTest;

public class NamespaceCmdTest extends TclCmdTest {

    @Test
	public void namespaceCommand() throws Exception {
		String resName = "/tcl/lang/cmd/namespace.test";
		tclTestResource(resName, Collections.<String>emptyList());
	}

}
