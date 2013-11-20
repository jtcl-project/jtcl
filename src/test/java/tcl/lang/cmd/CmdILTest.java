package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

import java.util.LinkedList;

public class CmdILTest extends TclCmdTest {

    @Test
    public void cmdILCommand() throws Exception {
        LinkedList<String> expectedFailureList = expectedFailures(
                // These fail because JTCL uses a different sorting algorithm than C Tcl.
                // Identical elements, according to the lsort comparison method, might be swapped
                // in JTCL relative to C Tcl - for example 'lsort -index 0 {{a b} {a c}}' can sort into either order
                // and be correctly sorted.
                // Also, the compare function might not be called in the same order,
                // which makes cmdIL-3.15 fail.
                "cmdIL-1.6", "cmdIL-1.23", "cmdIL-3.15"
        );
        String resName = "/tcl/lang/cmd/cmdIL.test";
        tclTestResource(resName, expectedFailureList);
    }

}
