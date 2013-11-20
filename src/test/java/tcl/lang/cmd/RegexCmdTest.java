package tcl.lang.cmd;

import org.junit.Test;
import tcl.lang.TclCmdTest;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;

public class RegexCmdTest extends TclCmdTest {

    @Test
	public void regexCommand() throws Exception {
		String resName = "/tcl/lang/cmd/regexp.test";
		tclTestResource(TCLTEST_NAMEOFEXECUTABLE, resName, Collections.<String>emptyList());
	}

    @Test
	public void regexpCompCommandegexComp() throws Exception {
		String resName = "/tcl/lang/cmd/regexpComp.test";
		tclTestResource(TCLTEST_NAMEOFEXECUTABLE, resName, Collections.<String>emptyList());
	}

    @Test
    public void regexSyntax() throws Exception {
        // These expected fails are due to the differences between Java's Matcher
        // and the C TCL regex engine.
        // TCL always attempts to match the longest string
        // starting from the outermost levels to the inner levels of parens.
        // With alternation (|) TCL chooses the longest match of all the branches.
        // Java, on the other hand, evaluates the RE from left to right, and returns
        // the first successful match, even if it's not the longest.  This class
        // follows the Java rules, because there doesn't appear to be a way to
        // influence Matcher's behavior to choose the longest. Probably the real solution
        // is to write a custom regex engine that performs according to TCL rules.
        //
        // In addition to these expectedFailures, reg.test has been modified to include
        // several testConstraints that turn off tests that don't apply to JTCL,
        // or for things that are not supported (BRE's, ERE's).  Due to the unique nature
        // of reg.test, it's much easier to turn off tests there than to use 
        // expectedFailureList here.  This list is reserved for real bugs.
		LinkedList<String> expectedFailureList = expectedFailures(
            "reg-3.5.execute",  "reg-14.9.execute", "reg-26.1.execute",
            "reg-26.2.execute", "reg-26.3.execute" 
        );
        String resName = "/tcl/lang/cmd/reg.test";
        
        // create the testregexp command, which is 'regexp' with the ability to process -xflags arg
        RegexpCmd testregexp = new RegexpCmd();
        testregexp.setAllowXFlags(true);
        getInterp().createCommand("testregexp",testregexp);

		tclTestResource(resName, expectedFailureList);
    }

}
