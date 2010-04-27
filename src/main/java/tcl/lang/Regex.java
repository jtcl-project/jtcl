/*
 * RegexpCmd.java --
 *
 * 	This file contains the Jacl implementation of the built-in Tcl
 *	"regexp" command. 
 *
 * Copyright (c) 2009 Radoslaw Szulgo (radoslaw@szulgo.pl)
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 * 
 * RCS: @(#) $Id: Regex.java,v 1.12 2010/02/21 18:30:44 mdejong Exp $
 */

package tcl.lang;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The <code>Regex</code> class can be used to match a pattern against a string
 * and optionally replace the matched parts with new strings.
 * <p>
 * Regular expressions are handled by java.util.regex package.
 * <hr>
 * Here is an example of how to use Regex Engine
 * 
 * <pre>
 * 
 * public static void main(String[] args) throws Exception {
 * 	Regex re;
 * 	String s;
 *  int group = 1;
 * 	/*
 * 	 * A regular expression to match the first line of a HTTP request.
 * 	 *
 * 	 * 1. &circ;               - starting at the beginning of the line
 * 	 * 2. ([A-Z]+)        - match and remember some upper case characters
 * 	 * 3. [ \t]+          - skip blank space
 * 	 * 4. ([&circ; \t]*)       - match and remember up to the next blank space
 * 	 * 5. [ \t]+          - skip more blank space
 * 	 * 6. (HTTP/1\\.[01]) - match and remember HTTP/1.0 or HTTP/1.1
 * 	 * 7. $		      - end of string - no chars left.
 * 	 &#42;/
 * 	s = &quot;GET http://a.b.com:1234/index.html HTTP/1.1&quot;;
 *  
 *  // Get the Regex object - compiled and matched	
 *  re = new Regex(&quot;&circ;([A-Z]+)[ \t]+([&circ; \t]+)[ \t]+(HTTP/1\\.[01])$&quot;, s, 0, 0);
 * 	
 *  while (re.match() {
 * 	if (group <= re.groupCount()) {
 * 		System.out.println(&quot;METHOD  &quot; + re.group(group++));
 * 		System.out.println(&quot;URL     &quot; + re.group(group++));
 * 		System.out.println(&quot;VERSION &quot; + re.group(group++));
 * 	}
 * 	/*
 * 	 * A regular expression to extract some simple comma-separated data,
 * 	 * reorder some of the columns, and discard column 2.
 * 	 &#42;/
 * 	s = &quot;abc,def,ghi,klm,nop,pqr&quot;;
 * 	re = new Regexp(&quot;&circ;([&circ;,]+),([&circ;,]+),([&circ;,]+),(.*)&quot;, s, 0, 0);
 * 	System.out.println(re.replaceFist(&quot;$3,$1,$4&quot;));
 * }
 * </pre>
 * 
 * @author Radoslaw Szulgo (radoslaw@szulgo.pl)
 * @version 1.0, 2009/08/05
 * 
 * @see java.util.regex.Matcher
 * @see java.util.regex.Pattern
 */

public class Regex {

	// Expressions that indicate use of the boundary matcher '^'

	private static final String REGEX_START1 = "^";
	private static final String REGEX_START2 = "|^";
	private static final String REGEX_START3 = "(^";

	// Pattern object

	private Pattern pattern;

	// Matcher object

	private Matcher matcher;

	// Flags of Pattern object

	private int flags;

	// Regular Expression string

	private String regexp;

	// Input string

	private String string;

	// Count of matches

	private int count;

	// Offset of the input string

	private int offset;

	/**
	 *------------------------------------------------------------------------
	 * -----
	 * 
	 * Constructor --
	 * 
	 * Stores params in object, compiles given regexp and matches input string.
	 * Additional param 'flags' sets flags of Pattern object that compiles
	 * regexp.
	 * 
	 * Results: None.
	 * 
	 * Side effects: None.
	 * 
	 * @param regexp
	 *            regular expression
	 * @param string
	 *            input string
	 * @param offset
	 *            offset of the input string
	 * @param flags
	 *            flags of pattern object that compiles regexp
	 * @throws PatternSyntaxException
	 *             thrown when there is an error during regexp compilation
	 *             ------
	 *             --------------------------------------------------------
	 *             ---------------
	 */

	public Regex(String regexp, String string, int offset, int flags)
			throws PatternSyntaxException {
		this.flags = flags;
		count = 0;
		this.regexp = regexp;
		this.string = string;

		// Record the offset in the string where a matching op should
		// begin, it is possible that the passed in offset is larger
		// than the actual length of the string.

		this.offset = offset;

		try {
			pattern = Pattern.compile(regexp, flags);
		} catch (PatternSyntaxException ex) {
			// Create new exception to replace known error messages.

			String msg = ex.getMessage();
			int index = ex.getIndex();

			// Either "(" or "["

			if (msg.indexOf("Unclosed group near index") != -1) {
				char c = regexp.charAt(index - 1);

				if (c == '(') {
					throw new PatternSyntaxException(
							"parentheses () not balanced", regexp, index);
				} else if (c == '[') {
					throw new PatternSyntaxException("braces [] not balanced",
							regexp, index);
				}
			}

			throw ex;
		}

		matcher = pattern.matcher(string);
	}

	/**
	 *------------------------------------------------------------------------
	 * -----
	 * 
	 * Constructor --
	 * 
	 * Stores params in object, compiles given regexp and matches input string.
	 * 
	 * Results: None.
	 * 
	 * Side effects: None.
	 * 
	 * @param regexp
	 *            regular expression
	 * @param string
	 *            input string
	 * @param offset
	 *            offset of the input string
	 * @throws PatternSyntaxException
	 *             thrown when there is an error during regexp compilation
	 *             ------
	 *             --------------------------------------------------------
	 *             ---------------
	 */

	public Regex(String regexp, String string, int offset)
			throws PatternSyntaxException {
		flags = 0;
		count = 0;
		this.regexp = regexp;
		this.string = string;

		// Record the offset in the string where a matching op should
		// begin, it is possible that the passed in offset is larger
		// than the actual length of the string.

		this.offset = offset;

		try {
			pattern = Pattern.compile(regexp);
		} catch (PatternSyntaxException ex) {
			// handling exception by caller
			throw ex;
		}

		matcher = pattern.matcher(string);
	}

	/**
	 *------------------------------------------------------------------------
	 * -----
	 * 
	 * match --
	 * 
	 * Match regular expression
	 * 
	 * Results: None.
	 * 
	 * Side effects: None.
	 * 
	 *------------------------------------------------------------------------
	 * -----
	 */

	public boolean match() {
		int fromOffset = offset;

		if (fromOffset > string.length()) {
			fromOffset = string.length();
		}

		return _match(matcher, fromOffset, offset);
	}

	private boolean _match(Matcher thisMatcher, int substringOffset,
			int entireStringOffset) {
		// if offset is a non-zero value, and regex
		// has '^', it will surely not match

		if (((pattern.flags() & Pattern.MULTILINE) == 0)
				&& (entireStringOffset != 0)
				&& (regexp.startsWith(REGEX_START1)
						|| regexp.indexOf(REGEX_START2) != -1 || regexp
						.indexOf(REGEX_START3) != -1)) {
			return false;
		} else {
			if (substringOffset == 0) {
				return thisMatcher.find();
			} else {
				return thisMatcher.find(substringOffset);
			}
		}
	}

	/**
	 *------------------------------------------------------------------------
	 * -----
	 * 
	 * replaceFirst --
	 * 
	 * Replaces the first subsequence of the input sequence that matches the
	 * pattern with the given replacement string.
	 * 
	 * Results: None.
	 * 
	 * Side effects: None.
	 * 
	 * @param subSpec
	 *            replacement string
	 * @return The string constructed by replacing the first matching
	 *         subsequence by the replacement string, substituting captured
	 *         subsequences as needed
	 *         --------------------------------------------
	 *         ---------------------------------
	 */

	public String replaceFirst(String subSpec) {
		boolean matches;
		String result;

		// we replace first matched occurence in the substring of the input
		// string

		String temp = string;
		int thisOffset = offset;

		if (thisOffset > 0) {
			if (thisOffset >= string.length()) {
				// -start larger than the last index indicates an empty
				// sring will be used as the input.

				thisOffset = string.length();
			}

			if (thisOffset == string.length()) {
				temp = "";
			} else {
				temp = string.substring(thisOffset);
			}
		}

		if ((temp.length() == 0) && ((this.flags & Pattern.MULTILINE) != 0)) {
			// Re-compile the expression without the Pattern.MULTILINE
			// flag so that matching to the empty string works as expected.

			int nomlFlags = this.flags & ~Pattern.MULTILINE;

			try {
				pattern = Pattern.compile(regexp, nomlFlags);
			} catch (PatternSyntaxException ex) {
				throw new TclRuntimeError(
						"regexp pattern could not be recompiled");
			}

			matcher = pattern.matcher("");
			matches = _match(matcher, 0, thisOffset);
			result = matcher.replaceFirst(subSpec);
		} else {
			matcher = pattern.matcher(temp);
			matches = _match(matcher, 0, thisOffset);
			result = matcher.replaceFirst(subSpec);
		}

		// if offset is set then we must join the substring that was
		// removed ealier (during matching)

		if (thisOffset > 0) {
			result = string.substring(0, thisOffset) + result;
		}

		if ((result == null) || (result.length() == 0) || !matches) {
			// if no match, return non-changed string
			result = string;
		} else {
			// if a replacement was done, increment count of matches
			count++;
		}

		return result;
	}

	/**
	 *------------------------------------------------------------------------
	 * -----
	 * 
	 * replaceAll --
	 * 
	 * Replaces every subsequence of the input sequence that matches the pattern
	 * with the given replacement string.
	 * 
	 * Results: None.
	 * 
	 * Side effects: None.
	 * 
	 * @param subSpec
	 *            the replacement string
	 * 
	 * @return The string constructed by replacing each matching subsequence by
	 *         the replacement string, substituting captured subsequences as
	 *         needed
	 *         ------------------------------------------------------------
	 *         -----------------
	 */

	public String replaceAll(String subSpec) {
		StringBuffer sb = new StringBuffer();

		// we replace first matched occurence in the substring of the input
		// string.
		// If matching starts at an offset other than 0, append literal text
		// from
		// the input string before we doing the match logic.

		String temp = string;
		int inputStringOffset = offset;

		if (inputStringOffset > 0) {
			if (inputStringOffset >= string.length()) {
				// -start larger than the last index indicates an empty
				// sring will be used as the input.

				inputStringOffset = string.length();
			}

			String strBeforeOffset = string.substring(0, inputStringOffset);
			sb.append(strBeforeOffset);

			if (inputStringOffset == string.length()) {
				temp = "";
			} else {
				temp = string.substring(inputStringOffset);
			}
		}

		Pattern tempPattern = pattern;
		Matcher tempMatcher;

		if ((temp.length() == 0) && ((this.flags & Pattern.MULTILINE) != 0)) {
			// Re-compile the expression without the Pattern.MULTILINE
			// flag so that matching to the empty string works as expected.

			int nomlFlags = this.flags & ~Pattern.MULTILINE;

			try {
				tempPattern = Pattern.compile(regexp, nomlFlags);
			} catch (PatternSyntaxException ex) {
				throw new TclRuntimeError(
						"regexp pattern could not be recompiled");
			}

			tempMatcher = tempPattern.matcher("");
		} else {
			tempMatcher = tempPattern.matcher(temp);
		}

		while (_match(tempMatcher, 0, inputStringOffset)) {
			count++;
			tempMatcher.appendReplacement(sb, subSpec);

			// Advance index in original string by the number
			// of characters up to the start of the match.

			inputStringOffset += tempMatcher.start();

			// Advance index in original string by the number
			// of characters in the match text, if the pattern
			// matches no characters then always advance by 1.

			int matchLen = tempMatcher.end() - tempMatcher.start();
			if (matchLen == 0) {
				// Matched, but length of match is zero characters,
				// so this might be a match to the start of a line.
				// Need to always advance one character to avoid
				// an infinite loop.

				if (inputStringOffset < string.length()) {
					char c = string.charAt(inputStringOffset);
					sb.append(c);
				}

				inputStringOffset++;

				if (inputStringOffset == string.length()) {
					// Zero length match at the last character
					// in the string, this loop will exit and
					// append any remaining characters, but we
					// just appended a character by default.
					// Create an empty matcher so that the
					// call to appendTail() at the end of this
					// loop does not append this same char again.
					tempMatcher = tempPattern.matcher("");
				}
			} else {
				inputStringOffset += matchLen;
			}

			if ((inputStringOffset == string.length())
					&& ((this.flags & Pattern.MULTILINE) != 0)
					&& (string.charAt(inputStringOffset - 1) == '\n')) {
				// Re-compile the expression without the Pattern.MULTILINE
				// flag so that matching to the empty string works as expected.

				int nomlFlags = this.flags & ~Pattern.MULTILINE;

				try {
					tempPattern = Pattern.compile(regexp, nomlFlags);
				} catch (PatternSyntaxException ex) {
					throw new TclRuntimeError(
							"regexp pattern could not be recompiled");
				}

				tempMatcher = tempPattern.matcher("");

				if (_match(tempMatcher, 0, 0)) {
					count++;
					tempMatcher.appendReplacement(sb, subSpec);
				}

				break;
			} else if (inputStringOffset >= string.length()) {
				break;
			}

			String substr = string.substring(inputStringOffset);
			tempMatcher = pattern.matcher(substr);
		}

		tempMatcher.appendTail(sb);

		String result = sb.toString();

		return result;
	}

	/**
	 *------------------------------------------------------------------------
	 * -----
	 * 
	 * getInfo --
	 * 
	 * Returns a list containing information about the regular expression. The
	 * first element of the list is a subexpression count. The second element is
	 * a list of property names that describe various attributes of the regular
	 * expression. Actually, properties are flags of Pattern object used in
	 * regexp.
	 * 
	 * Primarily intended for debugging purposes.
	 * 
	 * 
	 * Results: None.
	 * 
	 * Side effects: None.
	 * 
	 * @param interp
	 *            current Jacl interpreter object
	 * @return A list containing information about the regular expression.
	 * @throws TclException
	 *            --------------------------------------------------------------
	 *             ---------------
	 */

	public TclObject getInfo(Interp interp) throws TclException {
		TclObject props = TclList.newInstance();
		String groupCount = String.valueOf(matcher.groupCount());
		int f = pattern.flags();

		try {
			TclList.append(interp, props, TclString.newInstance(groupCount));

			if ((f | Pattern.CANON_EQ) != 0) {
				TclList
						.append(interp, props, TclString
								.newInstance("CANON_EQ"));
			}

			if ((f | Pattern.CASE_INSENSITIVE) != 0) {
				TclList.append(interp, props, TclString
						.newInstance("CASE_INSENSITIVE"));
			}

			if ((f | Pattern.COMMENTS) != 0) {
				TclList
						.append(interp, props, TclString
								.newInstance("COMMENTS"));
			}

			if ((f | Pattern.DOTALL) != 0) {
				TclList.append(interp, props, TclString.newInstance("DOTALL"));
			}

			if ((f | Pattern.MULTILINE) != 0) {
				TclList.append(interp, props, TclString
						.newInstance("MULTILINE"));
			}

			if ((f | Pattern.UNICODE_CASE) != 0) {
				TclList.append(interp, props, TclString
						.newInstance("UNICODE_CASE"));
			}

			if ((f | Pattern.UNIX_LINES) != 0) {
				TclList.append(interp, props, TclString
						.newInstance("UNIX_LINES"));
			}
		} catch (TclException e) {
			// handling exception by caller
			throw e;
		}

		return props;
	}

	/**
	 *------------------------------------------------------------------------
	 * -----
	 * 
	 * parseSubSpec --
	 * 
	 * Parses the replacement string (subSpec param) which is in Tcl's form.
	 * This method replaces Tcl's '&' and '\N' where 'N' is a number 0-9. to
	 * Java's reference characters. This method also quotes any characters that
	 * have special meaning to Java's regular expression APIs.
	 * 
	 * The replacement string (subSpec param) may contain references to
	 * subsequences captured during the previous match: Each occurrence of $g
	 * will be replaced by the result of evaluating group(g). The first number
	 * after the $ is always treated as part of the group reference. Subsequent
	 * numbers are incorporated into g if they would form a legal group
	 * reference. Only the numerals '0' through '9' are considered as potential
	 * components of the group reference. If the second group matched the string
	 * "foo", for example, then passing the replacement string "$2bar" would
	 * cause "foobar" to be appended to the string buffer. A dollar sign ($) may
	 * be included as a literal in the replacement string by preceding it with a
	 * backslash (\$).
	 * 
	 * Results: None.
	 * 
	 * Side effects: None.
	 * 
	 * @param subSpec
	 *            The replacement string
	 * @return The replacement string in Java's form
	 *         ----------------------------
	 *         -------------------------------------------------
	 */

	public static String parseSubSpec(String subSpec) {
		boolean escaped = false;

		StringBuffer sb = new StringBuffer();
		final int len = subSpec.length();

		for (int i = 0; i < len; i++) {
			char c = subSpec.charAt(i);

			if (c == '&') {
				// & indicates a whole match spec

				if (escaped) {
					sb.append(c);
					escaped = false;
				} else {
					sb.append("$0");
				}
			} else if (escaped && (c == '0')) {
				// \0 indicates a whole match spec

				escaped = false;
				sb.append("$0");
			} else if (escaped && (c >= '1' && c <= '9')) {
				// \N indicates a sub match spec

				escaped = false;
				sb.append('$');
				sb.append(c);
			} else if (c == '$') {
				// Dollar sign literal in the Tcl subst
				// spec must be escaped so that $0 is
				// not seen as a replacement spec by
				// the Java regexp API

				if (escaped) {
					sb.append("\\\\");
					escaped = false;
				}

				sb.append("\\$");
			} else if (c == '\\') {
				if (escaped) {
					sb.append("\\\\");
					escaped = false;
				} else {
					escaped = true;
				}
			} else {
				if (escaped) {
					// The previous character was an escape, so
					// emit it now before appending this char

					sb.append("\\\\");
					escaped = false;
				}

				sb.append(c);
			}
		}
		if (escaped) {
			// The last character was an escape
			sb.append("\\\\");
		}

		return sb.toString();
	}

	/**
	 *------------------------------------------------------------------------
	 * -----
	 * 
	 * groupCount --
	 * 
	 * Results: Returns the number of capturing groups in this matcher's
	 * pattern.
	 * 
	 * Side effects: None.
	 * 
	 *------------------------------------------------------------------------
	 * -----
	 */

	public int groupCount() {
		return matcher.groupCount();
	}

	/**
	 *------------------------------------------------------------------------
	 * -----
	 * 
	 * start --
	 * 
	 * Results: Returns the index of the first character matched
	 * 
	 * Side effects: None.
	 * 
	 *------------------------------------------------------------------------
	 * -----
	 */

	public int start() {
		return matcher.start();
	}

	/**
	 *------------------------------------------------------------------------
	 * -----
	 * 
	 * start --
	 * 
	 * Results: Returns the start index of the subsequence captured by the given
	 * group during the previous match operation.
	 * 
	 * Side effects: None.
	 * 
	 *------------------------------------------------------------------------
	 * -----
	 */

	public int start(int group) {
		return matcher.start(group);
	}

	/**
	 *------------------------------------------------------------------------
	 * -----
	 * 
	 * end --
	 * 
	 * Results: Returns the index of the last character matched, plus one.
	 * 
	 * Side effects: None.
	 * 
	 *------------------------------------------------------------------------
	 * -----
	 */

	public int end() {
		return matcher.end();
	}

	/**
	 *------------------------------------------------------------------------
	 * -----
	 * 
	 * end --
	 * 
	 * @param group
	 *            The index of a capturing group in this matcher's pattern
	 * @return The index of the last character captured by the group, plus one,
	 *         or -1 if the match was successful but the group itself did not
	 *         match anything
	 * @see java.util.regex.Matcher#end(int)
	 *     ----------------------------------------------------------------------
	 *      -------
	 */

	public int end(int group) {
		return matcher.end(group);
	}

	/**
	 *------------------------------------------------------------------------
	 * -----
	 * 
	 * group --
	 * 
	 * @return The (possibly empty) subsequence matched by the previous match,
	 *         in string form.
	 *         --------------------------------------------------
	 *         ---------------------------
	 */
	String group() {
		return matcher.group();
	}

	/**
	 *------------------------------------------------------------------------
	 * -----
	 * 
	 * group --
	 * 
	 * Returns the input subsequence captured by the given group during the
	 * previous match operation.
	 * 
	 * @param group
	 *            The index of a capturing group in this matcher's pattern
	 * @return The (possibly empty) subsequence captured by the group during the
	 *         previous match, or null if the group failed to match part of the
	 *         input
	 * @see java.util.regex.Matcher#group(int)
	 *     ----------------------------------------------------------------------
	 *      -------
	 */

	String group(int group) {
		return matcher.group(group);
	}

	/**
	 *------------------------------------------------------------------------
	 * -----
	 * 
	 * getPattern --
	 * 
	 * @return the pattern object
	 *         ------------------------------------------------
	 *         -----------------------------
	 */

	Pattern getPattern() {
		return pattern;
	}

	/**
	 *------------------------------------------------------------------------
	 * -----
	 * 
	 * getMatcher --
	 * 
	 * @return the matcher object
	 *         ------------------------------------------------
	 *         -----------------------------
	 */

	Matcher getMatcher() {
		return matcher;
	}

	/**
	 *------------------------------------------------------------------------
	 * -----
	 * 
	 * getFlags --
	 * 
	 * @return the flags of the pattern object
	 *         ----------------------------------
	 *         -------------------------------------------
	 */

	int getFlags() {
		return flags;
	}

	/**
	 *------------------------------------------------------------------------
	 * -----
	 * 
	 * getRegexp --
	 * 
	 * @return the regexp string
	 *         ------------------------------------------------
	 *         -----------------------------
	 */

	String getRegexp() {
		return regexp;
	}

	/**
	 *------------------------------------------------------------------------
	 * -----
	 * 
	 * getString --
	 * 
	 * @return the input string
	 *         --------------------------------------------------
	 *         ---------------------------
	 */

	String getString() {
		return string;
	}

	/**
	 *------------------------------------------------------------------------
	 * -----
	 * 
	 * getCount --
	 * 
	 * @return the count of correctly matched subsequences of the input string
	 *         --
	 *         ----------------------------------------------------------------
	 *         -----------
	 */

	public int getCount() {
		return count;
	}

	/**
	 *------------------------------------------------------------------------
	 * -----
	 * 
	 * getOffset --
	 * 
	 * @return the offset of the input string
	 *         ------------------------------------
	 *         -----------------------------------------
	 */

	public int getOffset() {
		return offset;
	}

	/**
	 *------------------------------------------------------------------------
	 * -----
	 * 
	 * setOffset --
	 * 
	 * @param offset
	 *            the offset to set
	 *            ----------------------------------------------
	 *            -------------------------------
	 */

	public void setOffset(int offset) {
		this.offset = offset;
	}

	/**
	 *------------------------------------------------------------------------
	 * -----
	 * 
	 * getPatternSyntaxMessage --
	 * 
	 * Return a regexp pattern syntax error message in a format expected by Tcl.
	 * 
	 * Results: None.
	 * 
	 * Side effects: None.
	 * ------------------------------------------------------
	 * -----------------------
	 */

	public static String getPatternSyntaxMessage(PatternSyntaxException ex) {
		String prefix = "couldn't compile regular expression pattern: ";
		String suffix = null;

		String msg = ex.getMessage();
		int index = ex.getIndex();
		String regexp = ex.getPattern();

		// Either '(' or '[' without a closing ')' or ']'

		if (msg.indexOf("parentheses () not balanced near") != -1) {
			suffix = "parentheses () not balanced";
		} else if (msg.indexOf("Unclosed character class near") != -1) {
			suffix = "brackets [] not balanced";
		}

		if (suffix == null) {
			suffix = ex.getMessage();
		}

		return prefix + suffix;
	}

} // end of class Regex

