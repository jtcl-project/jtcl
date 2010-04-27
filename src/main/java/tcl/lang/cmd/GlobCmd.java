/*
 * GlobCmd.java
 *
 *	This file contains the Jacl implementation of the built-in Tcl "glob"
 *	command.
 *
 * Copyright (c) 1997-1998 Sun Microsystems, Inc.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 * 
 * RCS: @(#) $Id: GlobCmd.java,v 1.10 2009/07/20 08:50:56 rszulgo Exp $
 *
 */

package tcl.lang.cmd;

import java.io.File;
import java.lang.reflect.Array;
import java.util.TreeSet;

import tcl.lang.Command;
import tcl.lang.FileUtil;
import tcl.lang.Interp;
import tcl.lang.JACL;
import tcl.lang.TclException;
import tcl.lang.TclIndex;
import tcl.lang.TclList;
import tcl.lang.TclNumArgsException;
import tcl.lang.TclObject;
import tcl.lang.TclString;
import tcl.lang.Util;

/*
 * This class implements the built-in "glob" command in Tcl.
 */

public class GlobCmd implements Command {

	/*
	 * Special characters that are used for string matching.
	 */

	private static final char[] specCharArr = { '*', '[', ']', '?', '\\' };

	/*
	 * Path of directory to search. Default is empty. 'directory' switch changes
	 * this value
	 */

	private String pathOrDir = null;

	/*
	 * List of types to search. Default is null. 'types' switch adds values to
	 * this set
	 */

	private static TreeSet typeList = new TreeSet();

	/*
	 * Types to the glob -types command.
	 */

	static final private String validTypes[] = { "b", "c", "d", "f", "l", "p",
			"s", "r", "w", "x", "readonly", "hidden", "macintosh" };

	static final private int TYPE_B = 0;
	static final private int TYPE_C = 1;
	static final private int TYPE_D = 2;
	static final private int TYPE_F = 3;
	static final private int TYPE_L = 4;
	static final private int TYPE_P = 5;
	static final private int TYPE_S = 6;
	static final private int TYPE_R = 7;
	static final private int TYPE_W = 8;
	static final private int TYPE_X = 9;
	static final private int TYPE_READONLY = 10;
	static final private int TYPE_HIDDEN = 11;
	static final private int TYPE_MACINTOSH = 12;

	/*
	 * Macintosh opts for file types
	 */

	static final private String MAC_TYPE = "type";
	static final private String MAC_CREATOR = "creator";

	/*
	 * Options to the glob command.
	 */

	static final private String validOptions[] = { "-directory", "-join",
			"-nocomplain", "-path", "-tails", "-types", "--" };

	static final private int OPT_DIRECTORY = 0;
	static final private int OPT_JOIN = 1;
	static final private int OPT_NOCOMPLAIN = 2;
	static final private int OPT_PATH = 3;
	static final private int OPT_TAILS = 4;
	static final private int OPT_TYPES = 5;
	static final private int OPT_LAST = 6;

	/*
	 * --------------------------------------------------------------------------
	 * cmdProc --
	 * 
	 * This procedure is invoked to process the "glob" Tcl command. See the user
	 * documentation for details on what it does.
	 * 
	 * Results: None.
	 * 
	 * Side effects: See the user documentation.
	 * --------------------------------
	 * ------------------------------------------
	 */

	public void cmdProc(Interp interp, // Current interp to eval the file cmd.
			TclObject argv[]) // Args passed to the glob command.
			throws TclException {
		boolean noComplain = false; // If true, error msg * will not!* be
		// returned
		boolean join = false; // If true, args will be joined (see the user doc)
		boolean tails = false; // If true, returns only the part of each file
		// found which follows the last directory
		Boolean dirMode = Boolean.FALSE; // If true, 'directory' switch is on;
		Boolean pathMode = Boolean.FALSE; // If true, `path` switch is on

		String head = "";
		String tail = "";
		int firstArg = 1; // index of the first non-switch arg
		int i; // generic index
		TclObject resultList; // list of files that match the pattern

		if (argv.length == 1) {
			throw new TclNumArgsException(interp, 1, argv,
					"?switches? name ?name ...?");
		}

		resultList = TclList.newInstance();
		resultList.preserve();

		for (boolean last = false; (firstArg < argv.length) && (!last); firstArg++) {

			if (!argv[firstArg].toString().startsWith("-")) {
				break;
			}

			int opt = TclIndex.get(interp, argv[firstArg], validOptions,
					"option", 0);

			switch (opt) {
			case OPT_NOCOMPLAIN:
				noComplain = true;
				break;

			case OPT_DIRECTORY:
				if (argv.length < 3) {
					throw new TclException(interp,
							"missing argument to \"-directory\"");
				}

				if (pathMode.booleanValue()) {
					throw new TclException(interp,
							"\"-directory\" cannot be used with \"-path\"");
				}

				dirMode = Boolean.TRUE;
				pathOrDir = argv[++firstArg].toString();
				String sep = FileUtil.getSeparators(pathOrDir);

				if (!pathOrDir.endsWith(sep)) {
					pathOrDir += sep;
				}

				break;

			case OPT_JOIN:
				join = true;
				break;

			case OPT_PATH:
				if (firstArg == argv.length - 1) {
					throw new TclException(interp,
							"missing argument to \"-path\"");
				}

				if (dirMode.booleanValue()) {
					throw new TclException(interp,
							"\"-path\" cannot be used with \"-directory\"");
				}

				pathMode = Boolean.TRUE;
				pathOrDir = argv[++firstArg].toString();

				break;

			case OPT_TAILS:
				tails = true;
				break;

			case OPT_TYPES:
				if (firstArg == argv.length - 1) {
					throw new TclException(interp,
							"missing argument to \"-types\"");
				}
				TclObject[] types = TclList.getElements(interp,
						argv[++firstArg]);
				parseTypes(interp, types);
				break;

			case OPT_LAST:
				last = true;
				break;

			default:
				throw new TclException(interp, "GlobCmd.cmdProc: bad option "
						+ opt + " index to validOptions");
			}
		}

		if (firstArg >= argv.length) {
			throw new TclNumArgsException(interp, 1, argv,
					"?switches? name ?name ...?");
		}

		if (tails && pathOrDir == null) {
			throw new TclNumArgsException(interp, 1, argv,
					"\"-tails\" must be used with either \"-directory\" or \"-path\"");
		}

		for (i = firstArg; i < argv.length; i++) {
			String arg;
			String prevTail = "";
			arg = argv[i].toString();
			String sep = FileUtil.getSeparators(arg);

			// Perform tilde substitution, if needed.
			if (arg.startsWith("~")) { // Find the first path
				head = tildeSubst(interp, arg, sep, noComplain);
				prevTail = arg.substring(1); // the remaining file path and
				// pattern
			} else {
				head = "";
				prevTail = arg.toString();
			}

			if (pathOrDir != null) {
				head = pathOrDir.toString();
			}

			// if join switch enabled, concat args
			if (join) {
				tail += prevTail + sep;

				// if there are some more args to join
				if (i + 1 < argv.length) {
					continue;
				} else {
					// remove the last unnecessary separator
					tail = tail.substring(0, tail.length() - 1);

					// path is no longer needed (out of date as new path is set)
					pathOrDir = null;
				}
			} else {
				tail = prevTail.toString();
			}

			try {
				doGlob(interp, sep, new StringBuffer(head), tail, resultList);
			} catch (TclException e) {
				if (noComplain) {
					return;
				} else {
					throw new TclException(interp, e.getMessage());
				}
			}

		}

		// If the list is empty and the nocomplain switch was not set then
		// generate and throw an exception. Always release the TclList upon
		// completion.
		try {
			if ((TclList.getLength(interp, resultList) == 0) && !noComplain) {
				String sep = "";
				StringBuffer ret = new StringBuffer();

				ret.append("no files matched glob pattern");
				ret.append((argv.length == 2) ? " \"" : "s \"");

				for (i = firstArg; i < argv.length; i++) {
					ret.append(sep + argv[i].toString());
					if (i == firstArg) {
						sep = " ";
					}
				}
				ret.append("\"");
				throw new TclException(interp, ret.toString());
			} else if (TclList.getLength(interp, resultList) > 0) {
				interp.setResult(resultList);
			}
		} finally {
			resultList.release();
		}

	}

	/*
	 * resultListObj
	 * ------------------------------------------------------------
	 * 
	 * SkipToChar --
	 * 
	 * This function traverses a glob pattern looking for the next unquoted
	 * occurance of the specified character at the same braces nesting level.
	 * 
	 * Results: Returns -1 if no match is made. Otherwise returns the index in
	 * str in which the match is found.
	 * 
	 * Side effects: None.
	 * 
	 * 
	 * 
	 * --------------------------------------------------------------------------
	 */

	private static int SkipToChar(String str, // Strubg to check.
			int sIndex, // Index in str to begin search.
			char match) // Ccharacter to find.
	{
		int level, length, i;
		boolean quoted = false;
		char c;

		level = 0;

		for (i = sIndex, length = str.length(); i < length; i++) {
			if (quoted) {
				quoted = false;
				continue;
			}
			c = str.charAt(i);
			if ((level == 0) && (c == match)) {
				return i;
			}
			if (c == '{') {
				level++;
			} else if (c == '}') {
				level--;
			} else if (c == '\\') {
				quoted = true;
			}
		}
		return -1;
	}

	/*
	 * --------------------------------------------------------------------------
	 * 
	 * TclDoGlob --
	 * 
	 * This recursive procedure forms the heart of the globbing code. It
	 * performs a depth-first traversal of the tree given by the path name to be
	 * globbed. The directory and remainder are assumed to be native format
	 * paths.
	 * 
	 * Results: None.
	 * 
	 * Side effects: None.
	 * 
	 * 
	 * 
	 * --------------------------------------------------------------------------
	 */

	private final void doGlob(Interp interp, // Interpreter to use for
			// error reporting
			String separators, // String containing separator characters
			StringBuffer headBuf, // Completely expanded prefix.
			String tail, // The unexpanded remainder of the path.
			TclObject resultList) // list of files that match the pattern
			throws TclException {
		int count = 0; // Counts the number of leading file
		// spearators for the tail.
		int pIndex; // Current index into tail
		int tailIndex; // First char after initial file
		// separators of the tail
		int tailLen = tail.length(); // Cache the length of the tail
		int headLen = headBuf.length(); // Cache the length of the head
		int baseLen; // Len of the substring from tailIndex
		// to the current specChar []*?{}\\
		int openBraceIndex; // Index of the current open brace
		int closeBraceIndex; // Index of the current closed brace
		int firstSpecCharIndex; // Index of the FSC, if any
		char lastChar = 0; // Used to see if last char is a file
		// separator.
		char ch; // Generic storage variable
		boolean quoted; // True if a char is '\\'

		if (headLen > 0) {
			lastChar = headBuf.charAt(headLen - 1);
		}

		// Consume any leading directory separators, leaving tailIndex
		// just past the last initial separator.

		String name = tail;
		for (tailIndex = 0; tailIndex < tailLen; tailIndex++) {
			char c = tail.charAt(tailIndex);
			if ((c == '\\') && ((tailIndex + 1) < tailLen)
					&& (separators.indexOf(tail.charAt(tailIndex + 1)) != -1)) {
				tailIndex++;
			} else if (separators.indexOf(c) == -1) {
				break;
			}
			count++;
		}

		// Deal with path separators. On the Mac, we have to watch out
		// for multiple separators, since they are special in Mac-style
		// paths.

		switch (JACL.PLATFORM) {
		case JACL.PLATFORM_MAC:

			if (separators.charAt(0) == '/') {
				if (((headLen == 0) && (count == 0))
						|| ((headLen > 0) && (lastChar != ':'))) {
					headBuf.append(":");
				}
			} else {
				if (count == 0) {
					if ((headLen > 0) && (lastChar != ':')) {
						headBuf.append(":");
					}
				} else {
					if (lastChar == ':') {
						count--;
					}
					while (count-- > 0) {
						headBuf.append(":");
					}
				}
			}
			break;

		case JACL.PLATFORM_WINDOWS:
			// If this is a drive relative path, add the colon and the
			// trailing slash if needed. Otherwise add the slash if
			// this is the first absolute element, or a later relative
			// element. Add an extra slash if this is a UNC path.
			if (name.startsWith(":")) {
				headBuf.append(":");
				if (count > 1) {
					headBuf.append("/");
				}
			} else if ((tailIndex < tailLen)
					&& (((headLen > 0) && (separators.indexOf(lastChar) == -1)) || ((headLen == 0) && (count > 0)))) {
				headBuf.append("/");
				if ((headLen == 0) && (count > 1)) {
					headBuf.append("/");
				}
			}
			break;
		default:
			// Add a separator if this is the first absolute element, or
			// a later relative element.

			if ((tailIndex < tailLen)
					&& (((headLen > 0) && (separators.indexOf(lastChar) == -1)) || ((headLen == 0) && (count > 0)))) {
				headBuf.append("/");
			}
		}

		// Look for the first matching pair of braces or the first
		// directory separator that is not inside a pair of braces.

		openBraceIndex = closeBraceIndex = -1;
		quoted = false;

		for (pIndex = tailIndex; pIndex != tailLen; pIndex++) {
			ch = tail.charAt(pIndex);
			if (quoted) {
				quoted = false;
			} else if (ch == '\\') {
				quoted = true;
				if (((pIndex + 1) < tailLen)
						&& (separators.indexOf(tail.charAt(pIndex + 1)) != -1)) {
					// Quoted directory separator.

					break;
				}
			} else if (separators.indexOf(ch) != -1) {
				// Unquoted directory separator.
				pIndex++;
				break;
			} else if (ch == '{') {
				openBraceIndex = pIndex;
				pIndex++;
				if ((closeBraceIndex = SkipToChar(tail, pIndex, '}')) != -1) {
					break;
				}
				throw new TclException(interp,
						"unmatched open-brace in file name");
			} else if (ch == '}') {
				throw new TclException(interp,
						"unmatched close-brace in file name");
			}
		}

		// Substitute the alternate patterns from the braces and recurse.

		if (openBraceIndex != -1) {
			int nextIndex;
			StringBuffer baseBuf = new StringBuffer();

			// For each element within in the outermost pair of braces,
			// append the element and the remainder to the fixed portion
			// before the first brace and recursively call doGlob.

			baseBuf.append(tail.substring(tailIndex, openBraceIndex));
			baseLen = baseBuf.length();
			headLen = headBuf.length();

			for (pIndex = openBraceIndex; pIndex < closeBraceIndex;) {
				pIndex++;
				nextIndex = SkipToChar(tail, pIndex, ',');
				if (nextIndex == -1 || nextIndex > closeBraceIndex) {
					nextIndex = closeBraceIndex;
				}

				headBuf.setLength(headLen);
				baseBuf.setLength(baseLen);

				baseBuf.append(tail.substring(pIndex, nextIndex));
				baseBuf.append(tail.substring(closeBraceIndex + 1));

				pIndex = nextIndex;
				doGlob(interp, separators, headBuf, baseBuf.toString(),
						resultList);
			}
			return;
		}

		// At this point, there are no more brace substitutions to perform on
		// this path component. The variable p is pointing at a quoted or
		// unquoted directory separator or the end of the string. So we need
		// to check for special globbing characters in the current pattern.
		// We avoid modifying tail if p is pointing at the end of the string.

		if (pIndex < tailLen) {
			firstSpecCharIndex = strpbrk(tail.substring(0, pIndex)
					.toCharArray(), specCharArr);
		} else {
			firstSpecCharIndex = strpbrk(tail.substring(tailIndex)
					.toCharArray(), specCharArr);
		}

		if (firstSpecCharIndex != -1) {
			// Look for matching files in the current directory. matchFiles
			// may recursively call TclDoGlob. For each file that matches,
			// it will add the match onto the interp->result, or call TclDoGlob
			// if there are more characters to be processed.

			matchFiles(interp, separators, headBuf.toString(), tail
					.substring(tailIndex), (pIndex - tailIndex), resultList);
			return;
		}
		headBuf.append(tail.substring(tailIndex, pIndex));
		if (pIndex < tailLen) {
			doGlob(interp, separators, headBuf, tail.substring(pIndex),
					resultList);
			return;
		}

		// There are no more wildcards in the pattern and no more unprocessed
		// characters in the tail, so now we can construct the path and verify
		// the existence of the file.

		String head;
		switch (JACL.PLATFORM) {
		case JACL.PLATFORM_MAC:
			if (headBuf.toString().indexOf(':') == -1) {
				headBuf.append(":");
			}
			head = headBuf.toString();
			break;
		case JACL.PLATFORM_WINDOWS:
			if (headBuf.length() == 0) {
				if (((name.length() > 1) && (name.charAt(0) == '\\') && ((name
						.charAt(1) == '/') || (name.charAt(1) == '\\')))
						|| ((name.length() > 0) && (name.charAt(0) == '/'))) {
					headBuf.append("\\");
				} else {
					headBuf.append(".");
				}
			}
			head = headBuf.toString().replace('\\', '/');
			break;
		default:
			if (headBuf.length() == 0) {
				if (name.startsWith("\\/") || name.startsWith("/")) {
					headBuf.append("/");
				} else {
					headBuf.append(".");
				}
			}
			head = headBuf.toString();
		}
		addFileToResult(interp, head, separators, resultList);
	}

	/*
	 * --------------------------------------------------------------------------
	 * 
	 * matchFiles --
	 * 
	 * This routine is used by the globbing code to search a directory for all
	 * files which match a given pattern. This is a routine contains
	 * platform-specific code.
	 * 
	 * Results: If the tail argument is NULL, then the matching files are added
	 * to the result list. Otherwise, TclDoGlob is called recursively for each
	 * matching subdirectory.
	 * 
	 * Side effects: None.
	 * ------------------------------------------------------
	 * -------------------- ---
	 */

	private final void matchFiles(Interp interp, // Interpreter to use
			// for error reporting
			String separators, // String containing separator characters
			String dirName, // Path of directory to search.
			String pattern, // Pattern to match against.
			int pIndex, // Index of end of pattern.
			TclObject resultList) // list of files that match the pattern
			throws TclException {
		boolean matchHidden; // True if were matching hidden file
		int patternEnd = pIndex; // Stores end index of the pattern
		int dirLen = dirName.length(); // Caches the len of the dirName
		int patLen = pattern.length(); // Caches the len of the pattern
		String[] dirListing; // Listing of files in dirBuf
		File dirObj; // File object of dirBuf
		StringBuffer dirBuf = new StringBuffer();
		// Converts the dirName to string
		// buffer or initializes it with '.'

		switch (JACL.PLATFORM) {
		case JACL.PLATFORM_WINDOWS:
			// Convert the path to normalized form since some interfaces only
			// accept backslashes. Also, ensure that the directory ends with
			// a separator character.

			if (pathOrDir != "") {
				dirBuf.append(pathOrDir);
			} else if (dirLen == 0) {
				dirBuf.append("./");
			} else {
				dirBuf.append(dirName);
				char c = dirBuf.charAt(dirLen - 1);
				if (((c == ':') && (dirLen == 2))
						|| (separators.indexOf(c) == -1)) {
					dirBuf.append("/");
				}
			}

			// All comparisons should be case insensitive on Windows.

			pattern = pattern.toLowerCase();
			break;
		case JACL.PLATFORM_MAC:
			// Fall through to unix case--mac is not yet implemented.

		default:
			// Make sure that the directory part of the name really is a
			// directory. If the directory name is "", use the name "."
			// instead, because some UNIX systems don't treat "" like "."
			// automatically. Keep the "" for use in generating file names,
			// otherwise "glob foo.c" would return "./foo.c".

			if (pathOrDir != "") {
				dirBuf.append(pathOrDir);
			} else if (dirLen == 0) {
				dirBuf.append(".");
			} else {
				dirBuf.append(dirName);
			}
		}

		dirObj = createAbsoluteFileObj(interp, dirBuf.toString());
		if (!dirObj.isDirectory()) {
			return;
		}

		// Check to see if the pattern needs to compare with hidden files.
		// Get a list of the directory's contents.

		if (pattern.startsWith(".") || pattern.startsWith("\\.")) {
			matchHidden = true;
			dirListing = addHiddenToDirList(dirObj);
		} else {
			matchHidden = false;
			dirListing = dirObj.list();
		}

		// Iterate over the directory's contents.

		// if (dirListing.length == 0) {
		// Strip off a trailing '/' if necessary, before reporting
		// the error.

		// if (dirName.endsWith("/")) {
		// dirName = dirName.substring(0, (dirLen - 1));
		// }
		// }

		// Clean up the end of the pattern and the tail pointer. Leave
		// the tail pointing to the first character after the path
		// separator following the pattern, or NULL. Also, ensure that
		// the pattern is null-terminated.

		if ((pIndex < patLen) && (pattern.charAt(pIndex) == '\\')) {
			pIndex++;
		}
		if (pIndex < (patLen - 1)) {
			pIndex++;
		}

		for (int i = 0; i < dirListing.length; i++) {
			// Don't match names starting with "." unless the "." is
			// present in the pattern.

			if (!matchHidden && (dirListing[i].startsWith("."))) {
				continue;
			}

			// Now check to see if the file matches. If there are more
			// characters to be processed, then ensure matching files are
			// directories before calling TclDoGlob. Otherwise, just add
			// the file to the resultList.

			String tmp = dirListing[i];
			if (JACL.PLATFORM == JACL.PLATFORM_WINDOWS) {
				tmp = tmp.toLowerCase();
			}
			if (Util.stringMatch(tmp, pattern.substring(0, patternEnd))) {

				dirBuf.setLength(dirLen);
				dirBuf.append(dirListing[i]);
				if (pIndex == pattern.length()) {
					addFileToResult(interp, dirBuf.toString(), separators,
							resultList);
				} else {
					dirObj = createAbsoluteFileObj(interp, dirBuf.toString());
					if (dirObj.isDirectory()) {
						dirBuf.append("/");
						doGlob(interp, separators, dirBuf, pattern
								.substring(patternEnd + 1), resultList);
					}
				}
			}
		}
	}

	/*
	 * --------------------------------------------------------------------------
	 * 
	 * strpbrk --
	 * 
	 * Returns the index into src of the first occurrence in array src of any
	 * character from the array matches, or a -1 if no character from matches
	 * exists in src.
	 * 
	 * Results: Returns the index of first occurence of a match or -1 if no
	 * match found.
	 * 
	 * Side effects: None.
	 * ------------------------------------------------------
	 * -------------------- ---
	 */

	private static final int strpbrk(char[] src, // The char array to search.
			char[] matches) // The chars to search for in src.
	{
		for (int i = 0; i < src.length; i++) {
			for (int j = 0; j < matches.length; j++) {
				if (src[i] == matches[j]) {
					return (i);
				}
			}
		}
		return -1;
	}

	/*
	 * --------------------------------------------------------------------------
	 * 
	 * addHiddenToDirList --
	 * 
	 * The method dirObj.list() returns a list of files in the directory. This
	 * method adds the files "." and ".." to create a full list.
	 * 
	 * Results: Retruns the full list of files in the directory dirObj.
	 * 
	 * Side effects: None.
	 * ------------------------------------------------------
	 * --------------------
	 */

	private static final String[] addHiddenToDirList(File dirObj) // File object
	// to list
	// contents
	// of
	{
		String[] dirListing; // Listing of files in dirObj
		String[] fullListing; // dirListing + .. and .
		int i, arrayLen;

		dirListing = dirObj.list();
		arrayLen = Array.getLength(dirListing);

		fullListing = (String[]) Array.newInstance(String.class, arrayLen + 2);

		for (i = 0; i < arrayLen; i++) {
			fullListing[i] = dirListing[i];
		}
		fullListing[arrayLen] = ".";
		fullListing[arrayLen + 1] = "..";

		return fullListing;
	}

	/*
	 * --------------------------------------------------------------------------
	 * 
	 * addFileToResult --
	 * 
	 * This recursive procedure forms the heart of the globbing code. It
	 * performs a depth-first traversal of the tree given by the path name to be
	 * globbed. The directory and remainder are assumed to be native format
	 * paths.
	 * 
	 * Results: None.
	 * 
	 * Side effects: Appends a string to TclObject resultList.
	 * ------------------
	 * --------------------------------------------------------
	 */

	private static void addFileToResult(Interp interp, // Interpreter to use for
			// error reporting
			String fileName, // Name of file to add to result list
			String separators, // String containing separator characters
			TclObject resultList) // list of files that match the pattern

			throws TclException {
		String prettyFileName = fileName;
		int prettyLen = fileName.length();

		// Java IO reuqires Windows volumes [A-Za-z]: to be followed by '\\'.

		if ((JACL.PLATFORM == JACL.PLATFORM_WINDOWS) && (prettyLen >= 2)
				&& (fileName.charAt(1) == ':')) {
			if (prettyLen == 2) {
				fileName = fileName + '\\';
			} else if (fileName.charAt(2) != '\\') {
				fileName = fileName.substring(0, 2) + '\\'
						+ fileName.substring(2);
			}
		}

		TclObject arrayObj[] = TclList.getElements(interp, FileUtil
				.splitAndTranslate(interp, fileName));
		fileName = FileUtil.joinPath(interp, arrayObj, 0, arrayObj.length);

		File f;
		if (FileUtil.getPathType(fileName) == FileUtil.PATH_ABSOLUTE) {
			f = FileUtil.getNewFileObj(interp, fileName);
		} else {
			f = new File(interp.getWorkingDir(), fileName);
		}

		// If the last character is a spearator, make sure the file is an
		// existing directory, otherwise check that the file exists.

		if ((prettyLen > 0)
				&& (separators.indexOf(prettyFileName.charAt(prettyLen - 1)) != -1)) {
			if (f.isDirectory()) {
				TclList.append(interp, resultList, TclString
						.newInstance(prettyFileName));
			}
		} else if (f.exists()) {
			TclList.append(interp, resultList, TclString
					.newInstance(prettyFileName));
		}
	}

	/*
	 * --------------------------------------------------------------------------
	 * createAbsoluteFileObj --
	 * 
	 * Creates and returns a File object from the String fileName. If fileName
	 * is not null, it verifies that the file path is absolute, setting it if it
	 * is not.
	 * 
	 * Results: Returns the fully qualified File object.
	 * 
	 * Side effects: None.
	 * ------------------------------------------------------
	 */

	private static final File createAbsoluteFileObj(Interp interp, // Interpreter
			// for error
			// reports.
			String fileName) // Name of file.
			throws TclException {
		if (fileName.equals("")) {
			return (interp.getWorkingDir());
		}

		if ((JACL.PLATFORM == JACL.PLATFORM_WINDOWS)
				&& (fileName.length() >= 2) && (fileName.charAt(1) == ':')) {
			String tmp = null;
			if (fileName.length() == 2) {
				tmp = fileName.substring(0, 2) + '\\';
			} else if (fileName.charAt(2) != '\\') {
				tmp = fileName.substring(0, 2) + '\\' + fileName.substring(2);
			}
			if (tmp != null) {
				return FileUtil.getNewFileObj(interp, tmp);
			}
		}

		return FileUtil.getNewFileObj(interp, fileName);
	}

	/*
	 * --------------------------------------------------------------------------
	 * 
	 * tildeSubst --
	 * 
	 * Substitutes the tilde (~) character to the full path of user's home
	 * directory.
	 * 
	 * Results: Full path of user's home directory.
	 * 
	 * Side effects: None.
	 * 
	 * 
	 * 
	 * --------------------------------------------------------------------------
	 */
	private static final String tildeSubst(Interp interp, String arg,
			String separators, boolean noComplain) throws TclException {
		int index = 0;
		String head;
		// separator after the tilde.

		for (; index < arg.length(); index++) {
			char c = arg.charAt(index);
			if (c == '\\') {
				if (separators.indexOf(arg.charAt(index + 1)) != -1) {
					break;
				}
			} else if (separators.indexOf(c) != -1) {
				break;
			}
		}

		// Determine the home directory for the specified user. Note //
		// that
		// we don't allow special characters in the user name.

		if (strpbrk(arg.substring(1, index).toCharArray(), specCharArr) < 0) {
			try {
				head = FileUtil.doTildeSubst(interp, arg.substring(1, index));
			} catch (TclException e) {
				if (noComplain) {
					head = null;
				} else {
					throw new TclException(interp, e.getMessage());
				}
			}
		} else {
			if (!noComplain) {
				throw new TclException(interp,
						"globbing characters not supported in user names");
			}
			head = null;
		}

		if (head == null) {
			if (noComplain) {
				interp.setResult("");
				return null;
			} else {
				return null;
			}
		}
		if (index != arg.length()) {
			index++;
		}
		return head;
	}

	private static final void parseTypes(Interp interp, TclObject[] types)
			throws TclException {

		for (int i = 0; i < types.length; i++) {
			int opt = TclIndex.get(interp, types[i], validTypes, "types", 1);
			switch (opt) {
			case TYPE_B:
			case TYPE_C:
			case TYPE_D:
			case TYPE_F:
			case TYPE_L:
			case TYPE_P:
			case TYPE_S:
			case TYPE_R:
			case TYPE_W:
			case TYPE_X:
			case TYPE_READONLY:
			case TYPE_HIDDEN:
				typeList.add(validTypes[opt]);
				break;

			case TYPE_MACINTOSH:
				if (MAC_TYPE.equals(validTypes[opt + 1])) {

					i++;
				} else if (MAC_CREATOR.equals(validTypes[opt + 1])) {

					i++;
				}
				break;

			default:
				throw new TclException(interp, "bad argument to \"-types\": "
						+ validTypes[opt]);
			}
		}

		return;
	}
} // end GlobCmd class
