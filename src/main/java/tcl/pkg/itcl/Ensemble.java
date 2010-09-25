/*
 * ------------------------------------------------------------------------
 *      PACKAGE:  [incr Tcl]
 *  DESCRIPTION:  Object-Oriented Extensions to Tcl
 *
 *  [incr Tcl] provides object-oriented extensions to Tcl, much as
 *  C++ provides object-oriented extensions to C.  It provides a means
 *  of encapsulating related procedures together with their shared data
 *  in a local namespace that is hidden from the outside world.  It
 *  promotes code re-use through inheritance.  More than anything else,
 *  it encourages better organization of Tcl applications through the
 *  object-oriented paradigm, leading to code that is easier to
 *  understand and maintain.
 *
 *  This part handles ensembles, which support compound commands in Tcl.
 *  The usual "info" command is an ensemble with parts like "info body"
 *  and "info globals".  Extension developers can extend commands like
 *  "info" by adding their own parts to the ensemble.
 *
 * ========================================================================
 *  AUTHOR:  Michael J. McLennan
 *           Bell Labs Innovations for Lucent Technologies
 *           mmclennan@lucent.com
 *           http://www.tcltk.com/itcl
 *
 *     RCS:  $Id: Ensemble.java,v 1.4 2009/07/10 13:56:07 rszulgo Exp $
 * ========================================================================
 *           Copyright (c) 1993-1998  Lucent Technologies, Inc.
 * ------------------------------------------------------------------------
 * See the file "license.itcl" for information on usage and redistribution
 * of this file, and for a DISCLAIMER OF ALL WARRANTIES.
 */

package tcl.pkg.itcl;

import tcl.lang.AssocData;
import tcl.lang.Command;
import tcl.lang.CommandWithDispose;
import tcl.lang.InternalRep;
import tcl.lang.Interp;
import tcl.lang.Namespace;
import tcl.lang.Procedure;
import tcl.lang.TCL;
import tcl.lang.TclException;
import tcl.lang.TclList;
import tcl.lang.TclObject;
import tcl.lang.TclString;
import tcl.lang.WrappedCommand;

//  Data used to represent an ensemble:

class EnsemblePart {
	String name; // name of this part
	int minChars; // chars needed to uniquely identify part
	Command cmd; // command handling this part
	WrappedCommand wcmd; // wrapped for command
	String usage; // usage string describing syntax
	Ensemble ensemble; // ensemble containing this part
}

// Data shared by ensemble access commands and ensemble parser:

class EnsembleParser implements AssocData {
	Interp master; // master interp containing ensembles
	Interp parser; // slave interp for parsing
	Ensemble ensData; // add parts to this ensemble

	public void disposeAssocData(Interp interp) {
		Ensemble.DeleteEnsParser(this, this.master);
	}
}

// This class defines a Tcl object type that takes the
// place of a part name during ensemble invocations. When an
// error occurs and the caller tries to print objv[0], it will
// get a string that contains a complete path to the ensemble
// part.

class ItclEnsInvoc implements InternalRep /* , CommandWithDispose */{
	EnsemblePart ensPart;
	TclObject chainObj;

	// Implement InternalRep interface
	// Note: SetEnsInvocFromAny is not used

	public InternalRep duplicate() {
		return Ensemble.DupEnsInvocInternalRep(this);
	}

	public void dispose() {
		Ensemble.FreeEnsInvocInternalRep(this);
	}

	public String toString() {
		return Ensemble.UpdateStringOfEnsInvoc(this);
	}

	public static TclObject newInstance() {
		return new TclObject(new ItclEnsInvoc());
	}

	/*
	 * // Implement CommandWithDispose interface
	 * 
	 * public void cmdProc(Interp interp, TclObject argv[]) throws TclException
	 * {}
	 * 
	 * public void disposeCmd() {}
	 */
}

// Data/Methods in Ensemble class

class Ensemble {
	Interp interp; // interpreter containing this ensemble
	EnsemblePart[] parts; // list of parts in this ensemble
	int numParts; // number of parts in part list
	int maxParts; // current size of parts list
	WrappedCommand wcmd; // command representing this ensemble
	EnsemblePart parent; // parent part for sub-ensembles

	// NULL => toplevel ensemble

	// Helper function used throughout this module to parse
	// and Ensemble name into an array of String objects.

	static String[] SplitEnsemble(Interp interp, String ensName)
			throws TclException {
		TclObject list = TclString.newInstance(ensName);
		TclObject[] objArgv;
		String[] strArgv;

		objArgv = TclList.getElements(interp, list);
		strArgv = new String[objArgv.length];

		for (int i = 0; i < objArgv.length; i++) {
			strArgv[i] = objArgv[i].toString();
		}

		return strArgv;
	}

	// Helper function that merges an ensemble array back
	// into a valid Tcl list contained in a String.

	static String MergeEnsemble(Interp interp, String[] nameArgv, int nameArgc)
			throws TclException {
		TclObject list = TclList.newInstance();

		for (int i = 0; i < nameArgc; i++) {
			TclList.append(interp, list, TclString.newInstance(nameArgv[i]));
		}

		return list.toString();
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * Itcl_EnsembleInit -> Ensemble.EnsembleInit
	 * 
	 * Called when any interpreter is created to make sure that things are
	 * properly set up for ensembles.
	 * 
	 * Results: None.
	 * 
	 * Side effects: On the first call, the "ensemble" object type is registered
	 * with the Tcl interpreter.
	 * 
	 * ----------------------------------------------------------------------
	 */

	static void EnsembleInit(Interp interp) // interpreter being initialized
	{
		// ItclEnsInvoc obj type need not be registered with Jacl

		interp.createCommand("::itcl::ensemble", new EnsembleCmd(null));
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * Itcl_CreateEnsemble -> Ensemble.CreateEnsemble
	 * 
	 * Creates an ensemble command, or adds a sub-ensemble to an existing
	 * ensemble command. The ensemble name is a space- separated list. The first
	 * word in the list is the command name for the top-level ensemble. Other
	 * names do not have commands associated with them; they are merely
	 * sub-ensembles within the ensemble. So a name like "a::b::foo bar baz"
	 * represents an ensemble command called "foo" in the namespace "a::b" that
	 * has a sub-ensemble "bar", that has a sub-ensemble "baz".
	 * 
	 * If the name is a single word, then this procedure creates a top-level
	 * ensemble and installs an access command for it. If a command already
	 * exists with that name, it is deleted.
	 * 
	 * If the name has more than one word, then the leading words are treated as
	 * a path name for an existing ensemble. The last word is treated as the
	 * name for a new sub-ensemble. If an part already exists with that name, it
	 * is an error.
	 * 
	 * Results: Raises a TclException if anything goes wrong.
	 * 
	 * ----------------------------------------------------------------------
	 */

	static void CreateEnsemble(Interp interp, // interpreter to be updated
			String ensName) // name of the new ensemble
			throws TclException {
		Ensemble parentEnsData = null;
		TclObject list;
		String[] nameArgv = null;

		// Split the ensemble name into its path components.

		try {
			nameArgv = SplitEnsemble(interp, ensName);
		} catch (TclException ex) {
			CreateEnsembleFailed(interp, ensName, ex);
		}
		if (nameArgv.length < 1) {
			TclException ex = new TclException(interp,
					"invalid ensemble name \"" + ensName + "\"");
			CreateEnsembleFailed(interp, ensName, ex);
		}

		// If there is more than one path component, then follow
		// the path down to the last component, to find the containing
		// ensemble.

		parentEnsData = null;
		if (nameArgv.length > 1) {
			try {
				parentEnsData = FindEnsemble(interp, nameArgv,
						nameArgv.length - 1);
			} catch (TclException ex) {
				CreateEnsembleFailed(interp, ensName, ex);
			}

			if (parentEnsData == null) {
				String pname = MergeEnsemble(interp, nameArgv,
						nameArgv.length - 1);
				TclException ex = new TclException(interp,
						"invalid ensemble name \"" + pname + "\"");
				CreateEnsembleFailed(interp, ensName, ex);
			}
		}

		// Create the ensemble.

		try {
			CreateEnsemble(interp, parentEnsData, nameArgv[nameArgv.length - 1]);
		} catch (TclException ex) {
			CreateEnsembleFailed(interp, ensName, ex);
		}
	}

	// Helper function used when CreateEnsemble fails

	static void CreateEnsembleFailed(Interp interp, String ensName,
			TclException ex) throws TclException {
		StringBuffer buffer = new StringBuffer(64);

		buffer.append("\n    (while creating ensemble \"");
		buffer.append(ensName);
		buffer.append("\")");
		interp.addErrorInfo(buffer.toString());

		throw ex;
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * Itcl_AddEnsemblePart -> Ensemble.AddEnsemblePart
	 * 
	 * Adds a part to an ensemble which has been created by Itcl_CreateEnsemble.
	 * Ensembles are addressed by name, as described in Itcl_CreateEnsemble.
	 * 
	 * If the ensemble already has a part with the specified name, this
	 * procedure returns an error. Otherwise, it adds a new part to the
	 * ensemble.
	 * 
	 * Any client data specified is automatically passed to the handling
	 * procedure whenever the part is invoked. It is automatically destroyed by
	 * the deleteProc when the part is deleted.
	 * 
	 * Results: Raises a TclException if anything goes wrong.
	 * 
	 * ----------------------------------------------------------------------
	 */

	static void AddEnsemblePart(Interp interp, // interpreter to be updated
			String ensName, // ensemble containing this part
			String partName, // name of the new part
			String usageInfo, // usage info for argument list
			Command objCmd) // handling procedure for part
			throws TclException {
		String[] nameArgv = null;
		Ensemble ensData = null;
		EnsemblePart ensPart;

		// Parse the ensemble name and look for a containing ensemble.

		try {
			nameArgv = SplitEnsemble(interp, ensName);
		} catch (TclException ex) {
			AddEnsemblePartFailed(interp, ensName, ex);
		}
		try {
			ensData = FindEnsemble(interp, nameArgv, nameArgv.length);
		} catch (TclException ex) {
			AddEnsemblePartFailed(interp, ensName, ex);
		}

		if (ensData == null) {
			String pname = MergeEnsemble(interp, nameArgv, nameArgv.length);
			TclException ex = new TclException(interp,
					"invalid ensemble name \"" + pname + "\"");
			AddEnsemblePartFailed(interp, ensName, ex);
		}

		// Install the new part into the part list.

		try {
			ensPart = AddEnsemblePart(interp, ensData, partName, usageInfo,
					objCmd);
		} catch (TclException ex) {
			AddEnsemblePartFailed(interp, ensName, ex);
		}
	}

	// Helper function used when AddEnsemblePart fails

	static void AddEnsemblePartFailed(Interp interp, String ensName,
			TclException ex) throws TclException {
		StringBuffer buffer = new StringBuffer();

		buffer.append("\n    (while adding to ensemble \"");
		buffer.append(ensName);
		buffer.append("\")");
		interp.addErrorInfo(buffer.toString());

		throw ex;
	}

	// Note: Itcl_GetEnsemblePart not ported since it seems to be unused
	// Note: Itcl_IsEnsemble not ported since it seems to be unused

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * Itcl_GetEnsembleUsage -> Ensemble.GetEnsembleUsage
	 * 
	 * Returns a summary of all of the parts of an ensemble and the meaning of
	 * their arguments. Each part is listed on a separate line. Having this
	 * summary is sometimes useful when building error messages for the "@error"
	 * handler in an ensemble.
	 * 
	 * Ensembles are accessed by name, as described in Itcl_CreateEnsemble.
	 * 
	 * Results: If the ensemble is found, its usage information is appended to
	 * the buffer argument and the function returns true. If anything goes
	 * wrong, this procedure returns false;
	 * 
	 * Side effects: Buffer passed in is modified.
	 * 
	 * ----------------------------------------------------------------------
	 */

	static boolean GetEnsembleUsage(Interp interp, // interpreter containing the
			// ensemble
			String ensName, // name of the ensemble
			StringBuffer buffer) // returns: summary of usage info
	{
		String[] nameArgv = null;
		Ensemble ensData;
		Itcl_InterpState state;
		String retval;

		// Parse the ensemble name and look for the ensemble.
		// Save the interpreter state before we do this. If we get
		// any errors, we don't want them to affect the interpreter.

		state = Util.SaveInterpState(interp, 0);

		try {
			nameArgv = SplitEnsemble(interp, ensName);
		} catch (TclException ex) {
			Util.RestoreInterpState(interp, state);
			return false;
		}

		try {
			ensData = FindEnsemble(interp, nameArgv, nameArgv.length);
		} catch (TclException ex) {
			Util.RestoreInterpState(interp, state);
			return false;
		}

		if (ensData == null) {
			Util.RestoreInterpState(interp, state);
			return false;
		}

		// Add a summary of usage information to the return buffer.

		GetEnsembleUsage(ensData, buffer);

		Util.DiscardInterpState(state);

		return true;
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * Itcl_GetEnsembleUsageForObj -> Ensemble.GetEnsembleUsageForObj
	 * 
	 * Returns a summary of all of the parts of an ensemble and the meaning of
	 * their arguments. This procedure is just like Itcl_GetEnsembleUsage, but
	 * it determines the desired ensemble from a command line argument. The
	 * argument should be the first argument on the command line--the ensemble
	 * command or one of its parts.
	 * 
	 * Results: If the ensemble is found, its usage information is appended onto
	 * the buffer, and this procedure returns true. It is the responsibility of
	 * the caller to init the buffer. If anything goes wrong, this procedure
	 * returns false.
	 * 
	 * Side effects: Buffer passed in is modified.
	 * ----------------------------------------------------------------------
	 */

	static boolean GetEnsembleUsageForObj(Interp interp, // interpreter
			// containing the
			// ensemble
			TclObject ensObj, // argument representing ensemble
			StringBuffer buffer) {
		Ensemble ensData;
		TclObject chainObj = null;
		Command cmd;

		// If the argument is an ensemble part, then follow the chain
		// back to the command word for the entire ensemble.

		chainObj = ensObj;
		while (chainObj != null
				&& (chainObj.getInternalRep() instanceof ItclEnsInvoc)) {
			ItclEnsInvoc t = (ItclEnsInvoc) chainObj.getInternalRep();
			chainObj = t.chainObj;
		}

		if (chainObj != null) {
			cmd = interp.getCommand(chainObj.toString());
			if (cmd != null && (cmd instanceof HandleEnsemble)) {
				ensData = ((HandleEnsemble) cmd).ensData;
				GetEnsembleUsage(ensData, buffer);
				return true;
			}
		}
		return false;
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * GetEnsembleUsage -> Ensemble.GetEnsembleUsage
	 * 
	 * 
	 * Returns a summary of all of the parts of an ensemble and the meaning of
	 * their arguments. Each part is listed on a separate line. This procedure
	 * is used internally to generate usage information for error messages.
	 * 
	 * Results: Appends usage information onto the bufer.
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	static void GetEnsembleUsage(Ensemble ensData, // ensemble data
			StringBuffer buffer) // returns: summary of usage info
	{
		String spaces = "  ";
		boolean isOpenEnded = false;

		EnsemblePart ensPart;

		for (int i = 0; i < ensData.numParts; i++) {
			ensPart = ensData.parts[i];

			if (ensPart.name.equals("@error")) {
				isOpenEnded = true;
			} else {
				buffer.append(spaces);
				GetEnsemblePartUsage(ensPart, buffer);
				spaces = "\n  ";
			}
		}
		if (isOpenEnded) {
			buffer.append("\n...and others described on the man page");
		}
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * GetEnsemblePartUsage -> Ensemble.GetEnsemblePartUsage
	 * 
	 * Determines the usage for a single part within an ensemble, and appends a
	 * summary onto a dynamic string. The usage is a combination of the part
	 * name and the argument summary.
	 * 
	 * Results: Returns usage information in the buffer.
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	static void GetEnsemblePartUsage(EnsemblePart ensPart, // ensemble part for
			// usage info
			StringBuffer buffer) // returns: usage information
	{
		EnsemblePart part;
		WrappedCommand wcmd;
		String name;
		Itcl_List trail;
		Itcl_ListElem elem;

		// Build the trail of ensemble names leading to this part.

		trail = new Itcl_List();
		Util.InitList(trail);
		for (part = ensPart; part != null; part = part.ensemble.parent) {
			Util.InsertList(trail, part);
		}

		wcmd = ensPart.ensemble.wcmd;
		name = ensPart.ensemble.interp.getCommandName(wcmd);
		Util.AppendElement(buffer, name);

		for (elem = Util.FirstListElem(trail); elem != null; elem = Util
				.NextListElem(elem)) {
			part = (EnsemblePart) Util.GetListValue(elem);
			Util.AppendElement(buffer, part.name);
		}
		Util.DeleteList(trail);

		// If the part has usage info, use it directly.

		if (ensPart.usage != null && ensPart.usage.length() > 0) {
			buffer.append(" ");
			buffer.append(ensPart.usage);
		}

		// If the part is itself an ensemble, summarize its usage.
		else if (ensPart.cmd != null && (ensPart.cmd instanceof HandleEnsemble)) {
			buffer.append(" option ?arg arg ...?");
		}
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * CreateEnsemble -> Ensemble.CreateEnsemble
	 * 
	 * Creates an ensemble command, or adds a sub-ensemble to an existing
	 * ensemble command. Works like Itcl_CreateEnsemble, except that the
	 * ensemble name is a single name, not a path. If a parent ensemble is
	 * specified, then a new ensemble is added to that parent. If a part already
	 * exists with the same name, it is an error. If a parent ensemble is not
	 * specified, then a top-level ensemble is created. If a command already
	 * exists with the same name, it is deleted.
	 * 
	 * Results: If anything goes wrong, this procedure raises a TclException
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	static void CreateEnsemble(Interp interp, // interpreter to be updated
			Ensemble parentEnsData, // parent ensemble or null
			String ensName) // name of the new ensemble
			throws TclException {
		Ensemble ensData;
		EnsemblePart ensPart;
		WrappedCommand wcmd;

		// Create the data associated with the ensemble.

		ensData = new Ensemble();
		ensData.interp = interp;
		ensData.numParts = 0;
		ensData.maxParts = 10;
		ensData.parts = new EnsemblePart[ensData.maxParts];
		ensData.wcmd = null;
		ensData.parent = null;

		// If there is no parent data, then this is a top-level
		// ensemble. Create the ensemble by installing its access
		// command.
		//
		if (parentEnsData == null) {
			interp.createCommand(ensName, new HandleEnsemble(ensData));
			wcmd = Namespace.findCommand(interp, ensName, null,
					TCL.NAMESPACE_ONLY);
			ensData.wcmd = wcmd;
			return;
		}

		// Otherwise, this ensemble is contained within another parent.
		// Install the new ensemble as a part within its parent.

		try {
			ensPart = CreateEnsemblePart(interp, parentEnsData, ensName);
		} catch (TclException ex) {
			DeleteEnsemble(ensData);
			throw ex;
		}

		ensData.wcmd = parentEnsData.wcmd;
		ensData.parent = ensPart;

		// For an ensemble part that is itself an ensemble,
		// create an instance of HandleInstance and associate
		// it with the Ensemble instance. Note that the
		// Command instance is not installed into the interpreter.

		ensPart.cmd = new HandleEnsemble(ensData);
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * AddEnsemblePart -> Ensemble.AddEnsemblePart
	 * 
	 * Adds a part to an existing ensemble. Works like Itcl_AddEnsemblePart, but
	 * the part name is a single word, not a path.
	 * 
	 * If the ensemble already has a part with the specified name, this
	 * procedure returns an error. Otherwise, it adds a new part to the
	 * ensemble.
	 * 
	 * Any client data specified is automatically passed to the handling
	 * procedure whenever the part is invoked. It is automatically destroyed by
	 * the deleteProc when the part is deleted.
	 * 
	 * Results: If anything goes wrong, this procedure raises a TclException
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	static EnsemblePart AddEnsemblePart(Interp interp, // interpreter to be
			// updated
			Ensemble ensData, // ensemble that will contain this part
			String partName, // name of the new part
			String usageInfo, // usage info for argument list
			Command objProc) // handling procedure for part
			throws TclException {
		EnsemblePart ensPart;
		WrappedCommand wcmd;

		// Install the new part into the part list.

		ensPart = CreateEnsemblePart(interp, ensData, partName);

		if (usageInfo != null) {
			ensPart.usage = usageInfo;
		}

		// Install the passed in Command in the ensemble part.

		wcmd = new WrappedCommand();
		wcmd.ns = ensData.wcmd.ns;
		wcmd.cmd = objProc;
		ensPart.cmd = objProc;
		ensPart.wcmd = wcmd;

		return ensPart;
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * DeleteEnsemble -> Ensemble.DeleteEnsemble
	 * 
	 * Invoked when the command associated with an ensemble is destroyed, to
	 * delete the ensemble. Destroys all parts included in the ensemble, and
	 * frees all memory associated with it.
	 * 
	 * Results: None.
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	static void DeleteEnsemble(Ensemble ensData) {
		// BE CAREFUL: Each ensemble part removes itself from the list.
		// So keep deleting the first part until all parts are gone.
		while (ensData.numParts > 0) {
			DeleteEnsemblePart(ensData.parts[0]);
		}
		ensData.parts = null;
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * FindEnsemble -> Ensemble.FindEnsemble
	 * 
	 * Searches for an ensemble command and follows a path to sub-ensembles.
	 * 
	 * Results: If the ensemble name is invalid then null will be returned. If
	 * anything goes wrong, this procedure raises a TclException
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	static Ensemble FindEnsemble(Interp interp, // interpreter containing the
			// ensemble
			String[] nameArgv, // path of names leading to ensemble
			int nameArgc) // number of nameArgv to process
			throws TclException {
		WrappedCommand wcmd;
		Command cmd;
		Ensemble ensData;
		EnsemblePart ensPart;

		// If there are no names in the path, then return an error.

		if (nameArgc < 1) {
			return null; // Caller should create "invalid ensemble" error.
		}

		// Use the first name to find the command for the top-level
		// ensemble.

		wcmd = Namespace.findCommand(interp, nameArgv[0], null,
				TCL.LEAVE_ERR_MSG);

		if (wcmd == null || !(wcmd.cmd instanceof HandleEnsemble)) {
			throw new TclException(interp, "command \"" + nameArgv[0]
					+ "\" is not an ensemble");
		}
		ensData = ((HandleEnsemble) wcmd.cmd).ensData;

		// Follow the trail of sub-ensemble names.

		for (int i = 1; i < nameArgc; i++) {
			ensPart = FindEnsemblePart(interp, ensData, nameArgv[i]);
			if (ensPart == null) {
				String pname = MergeEnsemble(interp, nameArgv, i);
				TclException ex = new TclException(interp,
						"invalid ensemble name \"" + pname + "\"");
			}

			cmd = ensPart.cmd;
			if (cmd == null || !(cmd instanceof HandleEnsemble)) {
				throw new TclException(interp, "part \"" + nameArgv[i]
						+ "\" is not an ensemble");
			}
			ensData = ((HandleEnsemble) cmd).ensData;
		}

		return ensData;
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * CreateEnsemblePart -> Ensemble.CreateEnsemblePart
	 * 
	 * Creates a new part within an ensemble.
	 * 
	 * Results: If anything goes wrong, this procedure raises a TclException
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	static EnsemblePart CreateEnsemblePart(Interp interp, // interpreter
			// containing the
			// ensemble
			Ensemble ensData, // ensemble being modified
			String partName) // name of the new part
			throws TclException {
		int i, pos;
		EnsemblePart[] partList;
		EnsemblePart part;

		// If a matching entry was found, then return an error.

		FindEnsemblePartIndexResult res = FindEnsemblePartIndex(ensData,
				partName);

		if (res.status) {
			throw new TclException(interp, "part \"" + partName
					+ "\" already exists in ensemble");
		}
		pos = res.pos;

		// Otherwise, make room for a new entry. Keep the parts in
		// lexicographical order, so we can search them quickly
		// later.

		if (ensData.numParts >= ensData.maxParts) {
			partList = new EnsemblePart[ensData.maxParts * 2];
			for (i = 0; i < ensData.maxParts; i++) {
				partList[i] = ensData.parts[i];
			}
			ensData.parts = null;
			ensData.parts = partList;
			ensData.maxParts = partList.length;
		}

		for (i = ensData.numParts; i > pos; i--) {
			ensData.parts[i] = ensData.parts[i - 1];
		}
		ensData.numParts++;

		part = new EnsemblePart();
		part.name = partName;
		part.cmd = null;
		part.usage = null;
		part.ensemble = ensData;

		ensData.parts[pos] = part;

		// Compare the new part against the one on either side of
		// it. Determine how many letters are needed in each part
		// to guarantee that an abbreviated form is unique. Update
		// the parts on either side as well, since they are influenced
		// by the new part.

		ComputeMinChars(ensData, pos);
		ComputeMinChars(ensData, pos - 1);
		ComputeMinChars(ensData, pos + 1);

		return part;
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * DeleteEnsemblePart -> Ensemble.DeleteEnsemblePart
	 * 
	 * Deletes a single part from an ensemble. The part must have been created
	 * previously by CreateEnsemblePart.
	 * 
	 * Invoke disposeCmd() if the part has a delete callback.
	 * 
	 * Results: None.
	 * 
	 * Side effects: Delete proc is called.
	 * 
	 * ----------------------------------------------------------------------
	 */

	static void DeleteEnsemblePart(EnsemblePart ensPart) // part being destroyed
	{
		int i, pos;
		Ensemble ensData;
		Command cmd = ensPart.cmd;

		// If this part has a delete proc, then call it to free
		// up the client data.

		if (cmd instanceof CommandWithDispose) {
			((CommandWithDispose) cmd).disposeCmd();
		}
		ensPart.cmd = null;

		// Find this part within its ensemble, and remove it from
		// the list of parts.

		FindEnsemblePartIndexResult res = FindEnsemblePartIndex(
				ensPart.ensemble, ensPart.name);

		if (res.status) {
			pos = res.pos;
			ensData = ensPart.ensemble;
			for (i = pos; i < ensData.numParts - 1; i++) {
				ensData.parts[i] = ensData.parts[i + 1];
			}
			ensData.numParts--;
		}

		// Free the memory associated with the part.

		if (ensPart.usage != null) {
			ensPart.usage = null;
		}
		ensPart.name = null;
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * FindEnsemblePart -> Ensemble.FindEnsemblePart
	 * 
	 * Searches for a part name within an ensemble. Recognizes unique
	 * abbreviations for part names.
	 * 
	 * Results: If the part name is not a unique abbreviation, this procedure
	 * raises a TclException. If the part can be found, returns a reference to
	 * the part. Otherwise, it returns NULL.
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	static EnsemblePart FindEnsemblePart(Interp interp, // interpreter
			// containing the
			// ensemble
			Ensemble ensData, // ensemble being searched
			String partName) // name of the desired part
			throws TclException {
		int pos = 0;
		int first, last, nlen;
		int i, cmp;
		EnsemblePart rensPart = null;

		// Search for the desired part name.
		// All parts are in lexicographical order, so use a
		// binary search to find the part quickly. Match only
		// as many characters as are included in the specified
		// part name.

		first = 0;
		last = ensData.numParts - 1;
		nlen = partName.length();

		while (last >= first) {
			pos = (first + last) >>> 1;
			if (partName.charAt(0) == ensData.parts[pos].name.charAt(0)) {
				cmp = partName.substring(0, nlen).compareTo(
						ensData.parts[pos].name);
				if (cmp == 0) {
					break; // found it!
				}
			} else if (partName.charAt(0) < ensData.parts[pos].name.charAt(0)) {
				cmp = -1;
			} else {
				cmp = 1;
			}

			if (cmp > 0) {
				first = pos + 1;
			} else {
				last = pos - 1;
			}
		}

		// If a matching entry could not be found, then quit.

		if (last < first) {
			return rensPart;
		}

		// If a matching entry was found, there may be some ambiguity
		// if the user did not specify enough characters. Find the
		// top-most match in the list, and see if the part name has
		// enough characters. If there are two parts like "foo"
		// and "food", this allows us to match "foo" exactly.

		if (nlen < ensData.parts[pos].minChars) {
			while (pos > 0) {
				pos--;
				if (partName.substring(0, nlen).compareTo(
						ensData.parts[pos].name) != 0) {
					pos++;
					break;
				}
			}
		}
		if (nlen < ensData.parts[pos].minChars) {
			StringBuffer buffer = new StringBuffer(64);

			buffer.append("ambiguous option \"" + partName
					+ "\": should be one of...");

			for (i = pos; i < ensData.numParts; i++) {
				if (partName.substring(0, nlen)
						.compareTo(ensData.parts[i].name) != 0) {
					break;
				}
				buffer.append("\n  ");
				GetEnsemblePartUsage(ensData.parts[i], buffer);
			}
			throw new TclException(interp, buffer.toString());
		}

		// Found a match. Return the desired part.

		rensPart = ensData.parts[pos];
		return rensPart;
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * FindEnsemblePartIndex -> Ensemble.FindEnsemblePartIndex
	 * 
	 * Searches for a part name within an ensemble. The part name must be an
	 * exact match for an existing part name in the ensemble. This procedure is
	 * useful for managing (i.e., creating and deleting) parts in an ensemble.
	 * 
	 * Results: If an exact match is found, this procedure returns a status of
	 * true, along with the index of the part in pos. Otherwise, it returns a
	 * status of false, along with an index in pos indicating where the part
	 * should be.
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	static FindEnsemblePartIndexResult FindEnsemblePartIndex(Ensemble ensData, // ensemble
			// being
			// searched
			String partName) // name of desired part
	{
		int pos = 0;
		int first, last;
		int cmp;
		int posRes;

		// Search for the desired part name.
		// All parts are in lexicographical order, so use a
		// binary search to find the part quickly.

		first = 0;
		last = ensData.numParts - 1;

		while (last >= first) {
			pos = (first + last) >>> 1;
			if (partName.charAt(0) == ensData.parts[pos].name.charAt(0)) {
				cmp = partName.compareTo(ensData.parts[pos].name);
				if (cmp == 0) {
					break; // found it!
				}
			} else if (partName.charAt(0) < ensData.parts[pos].name.charAt(0)) {
				cmp = -1;
			} else {
				cmp = 1;
			}

			if (cmp > 0) {
				first = pos + 1;
			} else {
				last = pos - 1;
			}
		}

		FindEnsemblePartIndexResult res = new FindEnsemblePartIndexResult();

		if (last >= first) {
			res.status = true;
			res.pos = pos;
			return res;
		}
		res.status = false;
		res.pos = first;
		return res;
	}

	static class FindEnsemblePartIndexResult {
		boolean status;
		int pos;
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * ComputeMinChars -> Ensemble.ComputeMinChars
	 * 
	 * Compares part names on an ensemble's part list and determines the minimum
	 * number of characters needed for a unique abbreviation. The parts on
	 * either side of a particular part index are compared. As long as there is
	 * a part on one side or the other, this procedure updates the parts to have
	 * the proper minimum abbreviations.
	 * 
	 * Results: None.
	 * 
	 * Side effects: Updates three parts within the ensemble to remember the
	 * minimum abbreviations.
	 * 
	 * ----------------------------------------------------------------------
	 */

	static void ComputeMinChars(Ensemble ensData, // ensemble being modified
			int pos) // index of part being updated
	{
		int min, max;
		int p, q;
		String pstr, qstr;

		// If the position is invalid, do nothing.

		if (pos < 0 || pos >= ensData.numParts) {
			return;
		}

		// Start by assuming that only the first letter is required
		// to uniquely identify this part. Then compare the name
		// against each neighboring part to determine the real minimum.

		ensData.parts[pos].minChars = 1;

		if (pos - 1 >= 0) {
			pstr = ensData.parts[pos].name;
			p = 0;
			qstr = ensData.parts[pos - 1].name;
			q = 0;
			final int plen = pstr.length();
			final int qlen = qstr.length();
			for (min = 1; p < plen && q < qlen
					&& pstr.charAt(p) == qstr.charAt(q); min++) {
				p++;
				q++;
			}
			if (min > ensData.parts[pos].minChars) {
				ensData.parts[pos].minChars = min;
			}
		}

		if (pos + 1 < ensData.numParts) {
			pstr = ensData.parts[pos].name;
			p = 0;
			qstr = ensData.parts[pos + 1].name;
			q = 0;
			final int plen = pstr.length();
			final int qlen = qstr.length();
			for (min = 1; p < plen && q < qlen
					&& pstr.charAt(p) == qstr.charAt(q); min++) {
				p++;
				q++;
			}
			if (min > ensData.parts[pos].minChars) {
				ensData.parts[pos].minChars = min;
			}
		}

		max = ensData.parts[pos].name.length();
		if (ensData.parts[pos].minChars > max) {
			ensData.parts[pos].minChars = max;
		}
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * HandleEnsemble -> Ensemble.HandleEnsemble.cmdProc
	 * 
	 * Invoked by Tcl whenever the user issues an ensemble-style command.
	 * Handles commands of the form:
	 * 
	 * <ensembleName> <partName> ?<arg> <arg>...?
	 * 
	 * Looks for the <partName> within the ensemble, and if it exists, the
	 * procedure transfers control to it.
	 * 
	 * Results: If anything goes wrong, this procedure raises a TclException.
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	static class HandleEnsemble implements CommandWithDispose {
		Ensemble ensData;

		HandleEnsemble(Ensemble ensData) {
			this.ensData = ensData;
		}

		public void disposeCmd() {
			DeleteEnsemble(ensData);
		}

		public void cmdProc(Interp interp, // Current interp.
				TclObject[] objv) // Args passed to the command.
				throws TclException {
			Command cmd;
			EnsemblePart ensPart;
			String partName;
			final int partNameLen;
			// Tcl_Obj *cmdlinePtr, *chainObj;
			// int cmdlinec;
			// Tcl_Obj **cmdlinev;
			TclObject cmdline, chainObj;
			TclObject[] cmdlinev;

			// If a part name is not specified, return an error that
			// summarizes the usage for this ensemble.

			if (objv.length < 2) {
				StringBuffer buffer = new StringBuffer(64);
				buffer.append("wrong # args: should be one of...\n");
				GetEnsembleUsage(ensData, buffer);

				throw new TclException(interp, buffer.toString());
			}

			// Lookup the desired part. If an ambiguous abbrevition is
			// found, return an error immediately.

			partName = objv[1].toString();
			partNameLen = partName.length();
			ensPart = FindEnsemblePart(interp, ensData, partName);

			// If the part was not found, then look for an "@error" part
			// to handle the error.

			if (ensPart == null) {
				ensPart = FindEnsemblePart(interp, ensData, "@error");
				if (ensPart != null) {
					cmd = ensPart.cmd;
					if (ensPart.wcmd.mustCallInvoke(interp)) ensPart.wcmd.invoke(interp, objv);
					else cmd.cmdProc(interp, objv);
					return;
				}
			}
			if (ensPart == null) {
				EnsembleErrorCmd(ensData, interp, objv, 1);
			}

			// Pass control to the part, and return the result.

			chainObj = ItclEnsInvoc.newInstance();
			ItclEnsInvoc irep = (ItclEnsInvoc) chainObj.getInternalRep();
			irep.ensPart = ensPart;
			irep.chainObj = objv[0];

			objv[1].preserve();
			objv[0].preserve();

			cmdline = TclList.newInstance();
			TclList.append(interp, cmdline, chainObj);

			for (int i = 2; i < objv.length; i++) {
				TclList.append(interp, cmdline, objv[i]);
			}
			cmdline.preserve();

			try {
				cmdlinev = TclList.getElements(interp, cmdline);

				cmd = ensPart.cmd;
				if (ensPart.wcmd.mustCallInvoke(interp)) ensPart.wcmd.invoke(interp, cmdlinev);
				else cmd.cmdProc(interp, cmdlinev);
			} finally {
				cmdline.release();
			}
		}
	} // end class HandleEnsemble

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * Itcl_EnsembleCmd -> Ensemble.EnsembleCmd.cmdProc
	 * 
	 * Invoked by Tcl whenever the user issues the "ensemble" command to
	 * manipulate an ensemble. Handles the following syntax:
	 * 
	 * ensemble <ensName> ?<command> <arg> <arg>...? ensemble <ensName> { part
	 * <partName> <args> <body> ensemble <ensName> { ... } }
	 * 
	 * Finds or creates the ensemble <ensName>, and then executes the commands
	 * to add parts.
	 * 
	 * Results: If anything goes wrong, this procedure raises a TclException.
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	static class EnsembleCmd implements Command {
		EnsembleParser ensParser;

		EnsembleCmd(EnsembleParser ensParser) {
			this.ensParser = ensParser;
		}

		public void cmdProc(Interp interp, // Current interp.
				TclObject[] objv) // Args passed to the command.
				throws TclException {
			String ensName;
			EnsembleParser ensInfo;
			Ensemble ensData, savedEnsData;
			EnsemblePart ensPart;
			WrappedCommand wcmd;
			Command cmd;

			// Make sure that an ensemble name was specified.

			if (objv.length < 2) {
				StringBuffer buffer = new StringBuffer(64);
				buffer.append("wrong # args: should be \"");
				buffer.append(objv[0].toString());
				buffer.append(" name ?command arg arg...?\"");
				throw new TclException(interp, buffer.toString());
			}

			// If this is the "ensemble" command in the main interpreter,
			// then the client data will be null. Otherwise, it is
			// the "ensemble" command in the ensemble body parser, and
			// the client data indicates which ensemble we are modifying.

			if (ensParser != null) {
				ensInfo = ensParser;
			} else {
				ensInfo = GetEnsembleParser(interp);
			}
			ensData = ensInfo.ensData;

			// Find or create the desired ensemble. If an ensemble is
			// being built, then this "ensemble" command is enclosed in
			// another "ensemble" command. Use the current ensemble as
			// the parent, and find or create an ensemble part within it.

			ensName = objv[1].toString();

			if (ensData != null) {
				try {
					ensPart = FindEnsemblePart(interp, ensData, ensName);
				} catch (TclException ex) {
					ensPart = null;
				}
				if (ensPart == null) {
					CreateEnsemble(interp, ensData, ensName);
					try {
						ensPart = FindEnsemblePart(interp, ensData, ensName);
					} catch (TclException ex) {
						ensPart = null;
					}
					Util.Assert(ensPart != null,
							"Itcl_EnsembleCmd: can't create ensemble");
				}

				cmd = ensPart.cmd;
				if (cmd == null || !(cmd instanceof HandleEnsemble)) {
					throw new TclException(interp, "part \""
							+ objv[1].toString() + "\" is not an ensemble");
				}
				ensData = ((HandleEnsemble) cmd).ensData;
			}

			// Otherwise, the desired ensemble is a top-level ensemble.
			// Find or create the access command for the ensemble, and
			// then get its data.

			else {
				try {
					wcmd = Namespace.findCommand(interp, ensName, null, 0);
				} catch (TclException ex) {
					wcmd = null;
				}
				if (wcmd == null) {
					CreateEnsemble(interp, null, ensName);
					wcmd = Namespace.findCommand(interp, ensName, null, 0);
				}
				if (wcmd == null) {
					cmd = null;
				} else {
					cmd = wcmd.cmd;
				}

				if (cmd == null || !(cmd instanceof HandleEnsemble)) {
					throw new TclException(interp, "command \""
							+ objv[1].toString() + "\" is not an ensemble");
				}
				ensData = ((HandleEnsemble) cmd).ensData;
			}

			// At this point, we have the data for the ensemble that is
			// being manipulated. Plug this into the parser, and then
			// interpret the rest of the arguments in the ensemble parser.

			TclException evalEx = null;
			savedEnsData = ensInfo.ensData;
			ensInfo.ensData = ensData;

			if (objv.length == 3) {
				try {
					ensInfo.parser.eval(objv[2].toString());
				} catch (TclException ex) {
					evalEx = ex;
				}
			} else if (objv.length > 3) {
				TclObject tlist = TclList.newInstance();
				for (int i = 2; i < objv.length; i++) {
					TclList.append(interp, tlist, objv[i]);
				}
				try {
					ensInfo.parser.eval(tlist.toString());
				} catch (TclException ex) {
					evalEx = ex;
				}
			}

			// Copy the result from the parser interpreter to the
			// master interpreter. If an error was encountered,
			// copy the error info first, and then set the result.
			// Otherwise, the offending command is reported twice.

			if (evalEx != null) {
				TclObject errInfoObj = ensInfo.parser.getVar("::errorInfo",
						TCL.GLOBAL_ONLY);

				if (errInfoObj != null) {
					interp.addErrorInfo(errInfoObj.toString());
				}

				if (objv.length == 3) {
					String msg = "\n    (\"ensemble\" body line "
							+ ensInfo.parser.getErrorLine() + ")";
					interp.addErrorInfo(msg);
				}

				ensInfo.ensData = savedEnsData;
				throw new TclException(interp, ensInfo.parser.getResult()
						.toString());
			}

			ensInfo.ensData = savedEnsData;
			interp.setResult(ensInfo.parser.getResult().toString());
		}
	} // end class EnsembleCmd

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * GetEnsembleParser -> Ensemble.GetEnsembleParser
	 * 
	 * Returns the slave interpreter that acts as a parser for the body of an
	 * "ensemble" definition. The first time that this is called for an
	 * interpreter, the parser is created and registered as associated data.
	 * After that, it is simply returned.
	 * 
	 * Results: Returns a reference to the ensemble parser data structure.
	 * 
	 * Side effects: On the first call, the ensemble parser is created and
	 * registered as "itcl_ensembleParser" with the interpreter.
	 * 
	 * ----------------------------------------------------------------------
	 */

	static EnsembleParser GetEnsembleParser(Interp interp) // interpreter
	// handling the
	// ensemble
	{
		Namespace ns, childNs;
		EnsembleParser ensInfo;
		WrappedCommand wcmd;

		// Look for an existing ensemble parser. If it is found,
		// return it immediately.

		ensInfo = (EnsembleParser) interp.getAssocData("itcl_ensembleParser");

		if (ensInfo != null) {
			return ensInfo;
		}

		// Create a slave interpreter that can be used to parse
		// the body of an ensemble definition.

		ensInfo = new EnsembleParser();
		ensInfo.master = interp;
		ensInfo.parser = new Interp();
		ensInfo.ensData = null;

		// Remove all namespaces and all normal commands from the
		// parser interpreter.

		ns = Namespace.getGlobalNamespace(ensInfo.parser);

		while ((childNs = (Namespace) ItclAccess.FirstHashEntry(ns.childTable)) != null) {
			Namespace.deleteNamespace(childNs);
		}

		while ((wcmd = (WrappedCommand) ItclAccess.FirstHashEntry(ns.cmdTable)) != null) {
			ensInfo.parser.deleteCommandFromToken(wcmd);
		}

		// Add the allowed commands to the parser interpreter:
		// part, delete, ensemble

		ensInfo.parser.createCommand("part", new EnsPartCmd(ensInfo));

		ensInfo.parser.createCommand("option", new EnsPartCmd(ensInfo));

		ensInfo.parser.createCommand("ensemble", new EnsembleCmd(ensInfo));

		// Install the parser data, so we'll have it the next time
		// we call this procedure.

		interp.setAssocData("itcl_ensembleParser", ensInfo);

		return ensInfo;
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * DeleteEnsParser -> Ensemble.DeleteEnsParser
	 * 
	 * Called when an interpreter is destroyed to clean up the ensemble parser
	 * within it. Destroys the slave interpreter and frees up the data
	 * associated with it.
	 * 
	 * Results: None.
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	static void DeleteEnsParser(EnsembleParser ensInfo, // parser for
			// ensemble-related
			// commands
			Interp interp) // interpreter containing the data
	{
		ensInfo.parser.dispose();
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * Itcl_EnsPartCmd -> Ensemble.EnsPartCmd.cmdProc
	 * 
	 * Invoked by Tcl whenever the user issues the "part" command to manipulate
	 * an ensemble. This command can only be used inside the "ensemble" command,
	 * which handles ensembles. Handles the following syntax:
	 * 
	 * ensemble <ensName> { part <partName> <args> <body> }
	 * 
	 * Adds a new part called <partName> to the ensemble. If a part already
	 * exists with that name, it is an error. The new part is handled just like
	 * an ordinary Tcl proc, with a list of <args> and a <body> of code to
	 * execute.
	 * 
	 * Results: If anything goes wrong, this procedure raises a TclException.
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	static class EnsPartCmd implements Command {
		EnsembleParser ensParser;

		EnsPartCmd(EnsembleParser ensParser) {
			this.ensParser = ensParser;
		}

		public void cmdProc(Interp interp, // Current interp.
				TclObject[] objv) // Args passed to the command.
				throws TclException {
			EnsembleParser ensInfo = ensParser;
			Ensemble ensData = ensInfo.ensData;

			boolean varArgs, space;
			String partName, usage;
			Procedure proc;
			WrappedCommand wcmd;
			// CompiledLocal *localPtr;
			EnsemblePart ensPart;
			StringBuffer buffer;

			if (objv.length != 4) {
				throw new TclException(interp, "wrong # args: should be \""
						+ objv[0].toString() + " name args body\"");
			}

			// Create a Tcl-style proc definition using the specified args
			// and body. This is not a proc in the usual sense. It belongs
			// to the namespace that contains the ensemble, but it is
			// accessed through the ensemble, not through a Tcl command.

			partName = objv[1].toString();
			wcmd = ensData.wcmd;

			proc = ItclAccess.newProcedure(interp, wcmd.ns, partName, objv[2],
					objv[3], "unknown", 0);

			// Deduce the usage information from the argument list.
			// We'll register this when we create the part, in a moment.

			buffer = new StringBuffer();
			varArgs = false;
			space = false;

			TclObject[][] argList = ItclAccess.getArgList(proc);

			for (int i = 0; i < argList.length; i++) {
				TclObject vname = argList[i][0];
				TclObject def = argList[i][1];

				varArgs = false;
				if (vname.toString().equals("args")) {
					varArgs = true;
				} else if (def != null) {
					if (space) {
						buffer.append(" ");
					}
					buffer.append("?");
					buffer.append(vname);
					buffer.append("?");
					space = true;
				} else {
					if (space) {
						buffer.append(" ");
					}
					buffer.append(vname);
					space = true;
				}
			}
			if (varArgs) {
				if (space) {
					buffer.append(" ");
				}
				buffer.append("?arg arg ...?");
			}

			usage = buffer.toString();

			// Create a new part within the ensemble. If successful,
			// plug the command token into the proc; we'll need it later.

			ensPart = AddEnsemblePart(interp, ensData, partName, usage, proc);

			ItclAccess.setWrappedCommand(proc, ensPart.wcmd);
		}
	} // end class EnsPartCmd

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * Itcl_EnsembleErrorCmd -> Ensemble.EnsembleErrorCmd
	 * 
	 * Invoked when the user tries to access an unknown part for an ensemble.
	 * Acts as the default handler for the "@error" part. Generates an error
	 * message like:
	 * 
	 * bad option "foo": should be one of... info args procname info body
	 * procname info cmdcount ...
	 * 
	 * Results: None.
	 * 
	 * Side effects: Raises a TclException with an error message.
	 * 
	 * ----------------------------------------------------------------------
	 */

	static void EnsembleErrorCmd(Ensemble ensData, // like client data in C
			// version
			Interp interp, TclObject[] objv, int skip) throws TclException {
		String cmdName;
		StringBuffer buffer = new StringBuffer(64);

		cmdName = objv[skip].toString();

		buffer.append("bad option \"");
		buffer.append(cmdName);
		buffer.append("\": should be one of...\n");
		GetEnsembleUsage(ensData, buffer);

		throw new TclException(interp, buffer.toString());
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * FreeEnsInvocInternalRep -> Ensemble.FreeEnsInvocInternalRep
	 * 
	 * Frees the resources associated with an ensembleInvoc object's internal
	 * representation.
	 * 
	 * Results: None.
	 * 
	 * Side effects: Decrements the ref count of the two objects referenced by
	 * this object. If there are no more uses, this will free the other objects.
	 * 
	 * ----------------------------------------------------------------------
	 */

	static void FreeEnsInvocInternalRep(ItclEnsInvoc obj) {
		TclObject prevArgObj = obj.chainObj;

		if (prevArgObj != null) {
			prevArgObj.release();
		}
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * DupEnsInvocInternalRep -> Ensemble.DupEnsInvocInternalRep
	 * 
	 * Duplicate the given internal representation of an ensembleInvoc.
	 * 
	 * This shouldn't be called. Normally, a temporary ensembleInvoc object is
	 * created while an ensemble call is in progress. This object may be
	 * converted to string form if an error occurs. It does not stay around
	 * long, and there is no reason for it to be duplicated.
	 * 
	 * Results: None.
	 * 
	 * Side effects: returns copy of internal rep with duplicates of the objects
	 * pointed to by src's internal rep.
	 * 
	 * ----------------------------------------------------------------------
	 */

	static InternalRep DupEnsInvocInternalRep(ItclEnsInvoc obj) // internal rep
	// to copy.

	{
		ItclEnsInvoc dup = new ItclEnsInvoc();
		dup.ensPart = obj.ensPart;
		dup.chainObj = obj.chainObj;

		if (dup.chainObj != null) {
			dup.chainObj.preserve();
		}

		return dup;
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * SetEnsInvocFromAny -> Ensemble.SetEnsInvocFromAny
	 * 
	 * Generates the internal representation for an ensembleInvoc object. This
	 * conversion really shouldn't take place. Normally, a temporary
	 * ensembleInvoc object is created while an ensemble call is in progress.
	 * This object may be converted to string form if an error occurs. But there
	 * is no reason for any other object to be converted to ensembleInvoc form.
	 * 
	 * Results: None.
	 * 
	 * Side effects: None.
	 * ----------------------------------------------------------------------
	 */

	static void SetEnsInvocFromAny(Interp interp, // Determines the context for
			// name resolution
			TclObject obj) // The object to convert
			throws TclException {
		// unused
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * UpdateStringOfEnsInvoc -> Ensemble.UpdateStringOfEnsInvoc
	 * 
	 * Updates the string representation for an ensembleInvoc object. This is
	 * called when an error occurs in an ensemble part, when the code tries to
	 * print objv[0] as the command name. This code automatically chains
	 * together all of the names leading to the ensemble part, so the error
	 * message references the entire command, not just the part name.
	 * 
	 * Results: Returns the full command name for the ensemble part.
	 * 
	 * ----------------------------------------------------------------------
	 */

	static String UpdateStringOfEnsInvoc(ItclEnsInvoc obj) // internal rep
	{
		EnsemblePart ensPart = obj.ensPart;
		TclObject chainObj = obj.chainObj;

		StringBuffer buffer = new StringBuffer(64);
		int length;
		String name;

		// Get the string representation for the previous argument.
		// This will force each ensembleInvoc argument up the line
		// to get its string representation. So we will get the
		// original command name, followed by the sub-ensemble, and
		// the next sub-ensemble, and so on. Then add the part
		// name from the ensPart argument.

		if (chainObj != null) {
			name = chainObj.toString();
			buffer.append(name);
		}

		if (ensPart != null) {
			Util.AppendElement(buffer, ensPart.name);
		}

		return buffer.toString();
	}

} // end class Ensemble

