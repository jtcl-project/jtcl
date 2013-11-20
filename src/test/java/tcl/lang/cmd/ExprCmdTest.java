package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

import java.util.LinkedList;

public class ExprCmdTest extends TclCmdTest {

    @Test
    public void exprCommand() throws Exception {
        LinkedList<String> expectedFailureList = expectedFailures(
                "expr-13.9", // not possible to match error message without significant recoding
                "expr-14.23", // widespread, pesky "invoked from within" instead of "while executing" in error message
                "expr-14.29", // widespread, pesky "invoked from within" instead of "while executing" in error message
                "expr-20.2", // not possible to match error message without significant recoding
                "expr-46.5", "expr-46.6" // JTCL uses 64-bit integer, so these tests don't overflow
        );
        String resName = "/tcl/lang/cmd/expr.test";
        tclTestResource(resName, expectedFailureList);
    }

}
