package tcl.lang;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import tcl.lang.Interp;
import tcl.lang.TclException;
import junit.framework.TestCase;

public class TclCmdTest extends TestCase {

	public static final String TCLTEST_VERBOSE = "tcltest::configure -verbose {start pass body error skip}";
	
	private Interp interp;
	
	public void setUp() throws Exception {
		interp = new Interp();
	}
	
	public void tearDown() {
		interp.dispose();
	}
	
	/**
	 * Test a Tcl test file resource, test file is assumed to 'package require tcltest'.
	 * Failures in tcl test cases are ignored.
	 * @param resName The name of a tcltest file as a resource path.  
	 * @throws Exception
	 */
	public void tclTestResourceIgnoreFailures(String resName) throws Exception {
		try {
			interp.evalResource(resName);
		} catch (TclException e) {
			String errStr = interp.getVar("errorInfo", 0).toString();
			throw new Exception(errStr, e);
		}
	}
	
	/**
	 * Test a Tcl test file resource.
	 * No failures are expected in running of the tcl test cases, if any occur, the junit test will fail.
	 * @param resName The name of a tcltest file as a resource path.  
	 * @throws Exception
	 */
	public void tclTestResource(String resName) throws Exception {
		tclTestResource(resName, Collections.EMPTY_LIST);
	}
	
	/**
	 * Test a Tcl test file resource.
	 * Examine test output, check expected and unexpected test case failures, if any occur, the 
	 * junit test will fail.
	 * @param resName The name of a tcltest file as a resource path.  
	 * @param expectedFailureCases The list of expected test case failures (List of String).
	 * @throws Exception
	 */
	public void tclTestResource(String resName, List expectedFailureCases) throws Exception {
		tclTestResource(null, resName, expectedFailureCases);
	}
	
	/**
	 * Test a Tcl test file resource.
	 * Examine test output, check expected and unexpected test case failures, if any occur, the 
	 * junit test will fail.
	 * @param preTestCode A string of Tcl code to evaluate before running the test case.  Hint:
	 * use TCLTEST_VERBOSE to show progress of tcl test cases.
	 * @param resName The name of a tcltest file as a resource path.  
	 * @param expectedFailureCases The list of expected test case failures (List of String).
	 * @throws Exception
	 */
	public void tclTestResource(String preTestCode, String resName, List expectedFailureCases) throws Exception {
		List unexpectedFailures = new LinkedList();
		
		// set up temporary file for tcltest output
		File tmpFile = File.createTempFile("tclCmdTest", ".txt");
		String tmpFileStr = tmpFile.getCanonicalPath();
		
		// load the tcltest package, export namespace procs, 
		// configure output file to the temporary file we created.
		try {
			interp.eval("package require tcltest; " 
					+ "namespace import -force ::tcltest::*; " 
					+ "tcltest::configure -outfile " + tmpFileStr);
		} catch (TclException e) {
			String errStr = interp.getVar("errorInfo", 0).toString();
			throw new Exception(errStr, e);
		}

		// run the preTestCode, if any
		if (preTestCode != null) {
			try {
				interp.eval(preTestCode);
			} catch (TclException e) {
				tmpFile.delete();
				String errStr = interp.getVar("errorInfo", 0).toString()
					+ "\nwhile running preTestCode:\n" 
					+ preTestCode;
				throw new Exception(errStr, e);
			} catch (Exception e) {
				tmpFile.delete();
				throw new Exception("Exception while running preTestCode:\n:" + preTestCode, e);
			}
		}
		
		// run the test case 
		try {
			interp.evalResource(resName);
		} catch (TclException e) {
			String errStr = interp.getVar("errorInfo", 0).toString()
				+ "\nwhile running tcltest\ncontents of test output:\n"
				+ readFile(tmpFile);
			tmpFile.delete();
			throw new Exception(errStr, e);
		} catch (Exception e) {
			String errStr = "Exception while running tcltest\ncontents of test output:\n"
				+ readFile(tmpFile);
			tmpFile.delete();
			throw new Exception(errStr, e);
		}
		
				
		// for failures that are expected, remove failed cases from expected list,
		// and record any unexpected failed test cases.
		// parsing tcltest output is extremely dependent on format of tcltest reporting.
		// two lines contain "FAILED", a beginning line with extra information, followed
		// by the differences, followed by a trailing line contain "FAILED".  we just check
		// the last line, which doesn't contain extra failure textual information.
		BufferedReader in = new BufferedReader(new FileReader(tmpFile));
		String line = in.readLine();
		while (line != null) {
			System.out.println(line);
			if (line.indexOf("FAILED") > 0) {
				String[] words = line.split(" ");
				if (words.length == 3) {   // example:  "---- for-6.13 FAILED"
					String testCaseName = words[1];
					if (! expectedFailureCases.remove(testCaseName)) {
						unexpectedFailures.add(testCaseName);
					}
				}
			}
			line = in.readLine();
		}
		in.close();
		
		tmpFile.delete();
		
		// if we found unexpected failed tcl test cases, or
		// if expected failure cases is non-empty, fail this junit test.
		// the latter means that we expected to see a test case as failed, but in fact it 
		// passed (or we didn't notice it as failed.)
		
		if (! expectedFailureCases.isEmpty() || ! unexpectedFailures.isEmpty()) {
			String unFailedExpected = expectedFailureCases.isEmpty() ? "" :
				"Expected-to-fail tcl test cases that were not reported as failed: " + expectedFailureCases.toString() + "\n";
			String unExpectedFailed = unexpectedFailures.isEmpty() ? "" :
				"Unexpected failed tcl test cases: " + unexpectedFailures.toString();
			fail(unFailedExpected +  unExpectedFailed);
		}
	}
	
	private String readFile(File file) {
		StringBuffer buf = new StringBuffer();
		try {
			BufferedReader in = new BufferedReader(new FileReader(file));
			String line = in.readLine();
			while (line != null) {
				buf.append(line).append('\n');
				line = in.readLine();
			}
			in.close();
		} catch (FileNotFoundException e) {
			// ignore 
		} catch (IOException e) {
			// ignore
		}
		return buf.toString();
	}
	
	/**
	 * Dummy test method to keep JUnit happy.
	 * @throws Exception
	 */
	public void XXtest() throws Exception {
		// nothing
	}
}
