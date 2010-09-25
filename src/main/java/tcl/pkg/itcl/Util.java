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
 *  This segment provides common utility functions used throughout
 *  the other [incr Tcl] source files.
 *
 * ========================================================================
 *  AUTHOR:  Michael J. McLennan
 *           Bell Labs Innovations for Lucent Technologies
 *           mmclennan@lucent.com
 *           http://www.tcltk.com/itcl
 *
 *     RCS:  $Id: Util.java,v 1.2 2005/09/12 00:00:50 mdejong Exp $
 * ========================================================================
 *           Copyright (c) 1993-1998  Lucent Technologies, Inc.
 * ------------------------------------------------------------------------
 * See the file "license.itcl" for information on usage and redistribution
 * of this file, and for a DISCLAIMER OF ALL WARRANTIES.
 */

package tcl.pkg.itcl;

import java.util.Hashtable;
import java.util.Stack;

import tcl.lang.CallFrame;
import tcl.lang.Interp;
import tcl.lang.Namespace;
import tcl.lang.TCL;
import tcl.lang.TclException;
import tcl.lang.TclList;
import tcl.lang.TclObject;
import tcl.lang.TclRuntimeError;
import tcl.lang.TclString;
import tcl.lang.WrappedCommand;

//  These records are used to keep track of reference-counted data
//  for Itcl_PreserveData and Itcl_ReleaseData.

class ItclPreservedData {
	int usage; // number of active uses
	ItclEventuallyFreed fobj; // Object to be freed eventually
}

interface ItclEventuallyFreed {
	// Invoked when data is no longer being used
	void eventuallyFreed();
}

// This structure is used to take a snapshot of the interpreter
// state in Itcl_SaveInterpState. You can snapshot the state,
// execute a command, and then back up to the result or the
// error that was previously in progress.

class Itcl_InterpState {
	int validate; // validation stamp
	int status; // return code status
	TclObject objResult; // result object
	TclObject errorInfo; // contents of errorInfo variable
	TclObject errorCode; // contents of errorCode variable
}

class Util {

	// POOL OF LIST ELEMENTS FOR LINKED LIST

	static Itcl_ListElem listPool = null;
	static int listPoolLen = 0;

	static Hashtable ItclPreservedList = new Hashtable();
	// Mutex ItclPreservedListLock

	static int VALID_LIST = 0x01face10; // magic bit pattern for validation
	static int LIST_POOL_SIZE = 200; // max number of elements in listPool

	static int STATE_VALID = 0x01233210; // magic bit pattern for validation

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_Assert -> Util.Assert
	 * 
	 * Called to mimic an assert() statement in C. Raises a TclRuntimeError if
	 * the test expression evaluates to false.
	 * ------------------------------------------------------------------------
	 */

	static void Assert(boolean tExpr, // test expression
			String tExprStr) // string representing test expression
	{
		if (!tExpr)
			throw new TclRuntimeError("Itcl Assertion failed: \"" + tExprStr
					+ "\"");
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_InitStack -> Util.InitStack
	 * 
	 * Initializes a stack structure, allocating a certain amount of memory for
	 * the stack and setting the stack length to zero.
	 * ------------------------------------------------------------------------
	 */

	static void InitStack(Itcl_Stack stack) // stack to be initialized
	{
		stack.s = new Stack();
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_DeleteStack -> Util.DeleteStack
	 * 
	 * Destroys a stack structure, freeing any memory that may have been
	 * allocated to represent it.
	 * ------------------------------------------------------------------------
	 */

	static void DeleteStack(Itcl_Stack stack) // stack to be deleted
	{
		stack.s.clear();
		stack.s = null;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_PushStack -> Util.PushStack
	 * 
	 * Pushes a piece of client data onto the top of the given stack. If the
	 * stack is not large enough, it is automatically resized.
	 * ------------------------------------------------------------------------
	 */

	static void PushStack(Object cdata, // data to be pushed onto stack
			Itcl_Stack stack) // stack
	{
		stack.s.push(cdata);
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_PopStack -> Util.PopStack
	 * 
	 * Pops a bit of client data from the top of the given stack.
	 * ------------------------------------------------------------------------
	 */

	static Object PopStack(Itcl_Stack stack) // stack to be manipulated
	{
		if (stack.s != null && !stack.s.empty()) {
			return stack.s.pop();
		}
		return null;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_PeekStack -> Util.PeekStack
	 * 
	 * Gets the current value from the top of the given stack.
	 * ------------------------------------------------------------------------
	 */

	static Object PeekStack(Itcl_Stack stack) // stack to be examined
	{
		if (stack.s == null)
			return null;
		return stack.s.peek();
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_GetStackValue -> Util.GetStackValue
	 * 
	 * Gets a value at some index within the stack. Index "0" is the first value
	 * pushed onto the stack.
	 * ------------------------------------------------------------------------
	 */

	static Object GetStackValue(Itcl_Stack stack, // stack to be examined
			int pos) // get value at this index
	{
		if (stack.s == null)
			return null;
		return stack.s.elementAt(pos);
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_GetStackSize -> Util.GetStackSize
	 * 
	 * Gets the number of elements in the stack.
	 * ------------------------------------------------------------------------
	 */

	static int GetStackSize(Itcl_Stack stack) // stack
	{
		if (stack.s == null)
			return 0;
		return stack.s.size();
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_InitList -> Util.InitList
	 * 
	 * Initializes a linked list structure, setting the list to the empty state.
	 * ------------------------------------------------------------------------
	 */

	static void InitList(Itcl_List list) // list to be initialized
	{
		list.validate = Util.VALID_LIST;
		list.num = 0;
		list.head = null;
		list.tail = null;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_DeleteList -> Util.DeleteList
	 * 
	 * Destroys a linked list structure, deleting all of its elements and
	 * setting it to an empty state. If the elements have memory associated with
	 * them, this memory must be freed before deleting the list or it will be
	 * lost.
	 * ------------------------------------------------------------------------
	 */

	static void DeleteList(Itcl_List list) // list to be deleted
	{
		Itcl_ListElem elem;

		Assert(list.validate == Util.VALID_LIST,
				"list.validate == Util.VALID_LIST");

		elem = list.head;
		while (elem != null) {
			elem = Util.DeleteListElem(elem);
		}
		list.validate = 0;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_CreateListElem -> Util.CreateListElem
	 * 
	 * Low-level routined used by procedures like InsertList() and AppendList()
	 * to create new list elements. If elements are available, one is taken from
	 * the list element pool. Otherwise, a new one is allocated.
	 * ------------------------------------------------------------------------
	 */

	static Itcl_ListElem CreateListElem(Itcl_List list) // list that will
	// contain this new
	// element
	{
		Itcl_ListElem elem;

		if (listPoolLen > 0) {
			elem = listPool;
			listPool = elem.next;
			--listPoolLen;
		} else {
			elem = new Itcl_ListElem();
		}
		elem.owner = list;
		elem.value = null;
		elem.next = null;
		elem.prev = null;

		return elem;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_DeleteListElem -> Util.DeleteListElem
	 * 
	 * Destroys a single element in a linked list, returning it to a pool of
	 * elements that can be later reused. Returns a pointer to the next element
	 * in the list.
	 * ------------------------------------------------------------------------
	 */

	static Itcl_ListElem DeleteListElem(Itcl_ListElem elem) // list element to
	// be deleted
	{
		Itcl_List list;
		Itcl_ListElem next;

		next = elem.next;

		if (elem.prev != null) {
			elem.prev.next = elem.next;
		}
		if (elem.next != null) {
			elem.next.prev = elem.prev;
		}

		list = elem.owner;
		if (elem == list.head)
			list.head = elem.next;
		if (elem == list.tail)
			list.tail = elem.prev;
		--list.num;

		if (listPoolLen < Util.LIST_POOL_SIZE) {
			elem.next = listPool;
			listPool = elem;
			++listPoolLen;
		} else {
			elem = null;
		}
		return next;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_InsertList -> Util.InsertList
	 * 
	 * Creates a new list element containing the given value and returns a
	 * pointer to it. The element is inserted at the beginning of the specified
	 * list.
	 * ------------------------------------------------------------------------
	 */

	static Itcl_ListElem InsertList(Itcl_List list, // list being modified
			Object val) // value associated with new element
	{
		Itcl_ListElem elem;
		Assert(list.validate == Util.VALID_LIST,
				"list.validate == Util.VALID_LIST");

		elem = Util.CreateListElem(list);

		elem.value = val;
		elem.next = list.head;
		elem.prev = null;
		if (list.head != null) {
			list.head.prev = elem;
		}
		list.head = elem;
		if (list.tail == null) {
			list.tail = elem;
		}
		++list.num;

		return elem;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_InsertListElem -> Util.InsertListElem
	 * 
	 * Creates a new list element containing the given value and returns a
	 * pointer to it. The element is inserted in the list just before the
	 * specified element.
	 * ------------------------------------------------------------------------
	 */

	static Itcl_ListElem InsertListElem(Itcl_ListElem pos, // insert just before
			// this element
			Object val) // value associated with new element
	{
		Itcl_List list;
		Itcl_ListElem elem;

		list = pos.owner;
		Assert(list.validate == Util.VALID_LIST,
				"list.validate == Util.VALID_LIST");
		// Next assert makes no sense as pos was already accessed above
		// Assert(pos != null,
		// "pos != null");

		elem = Util.CreateListElem(list);
		elem.value = val;

		elem.prev = pos.prev;
		if (elem.prev != null) {
			elem.prev.next = elem;
		}
		elem.next = pos;
		pos.prev = elem;

		if (list.head == pos) {
			list.head = elem;
		}
		if (list.tail == null) {
			list.tail = elem;
		}
		++list.num;

		return elem;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_AppendList -> Util.AppendList
	 * 
	 * Creates a new list element containing the given value and returns a
	 * pointer to it. The element is appended at the end of the specified list.
	 * ------------------------------------------------------------------------
	 */

	static Itcl_ListElem AppendList(Itcl_List list, // list being modified
			Object val) // value associated with new element
	{
		Itcl_ListElem elem;
		Assert(list.validate == Util.VALID_LIST,
				"list.validate == Util.VALID_LIST");

		elem = Util.CreateListElem(list);

		elem.value = val;
		elem.prev = list.tail;
		elem.next = null;
		if (list.tail != null) {
			list.tail.next = elem;
		}
		list.tail = elem;
		if (list.head == null) {
			list.head = elem;
		}
		++list.num;

		return elem;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_AppendListElem -> Util.AppendListElem
	 * 
	 * Creates a new list element containing the given value and returns a
	 * pointer to it. The element is inserted in the list just after the
	 * specified element.
	 * ------------------------------------------------------------------------
	 */

	static Itcl_ListElem AppendListElem(Itcl_ListElem pos, // insert just after
			// this element
			Object val) // value associated with new element
	{
		Itcl_List list;
		Itcl_ListElem elem;

		list = pos.owner;
		Assert(list.validate == Util.VALID_LIST,
				"list.validate == Util.VALID_LIST");
		// Next assert makes no sense as pos was already accessed above
		// Assert(pos != null,
		// "pos != null");

		elem = Util.CreateListElem(list);
		elem.value = val;

		elem.next = pos.next;
		if (elem.next != null) {
			elem.next.prev = elem;
		}
		elem.prev = pos;
		pos.next = elem;

		if (list.tail == pos) {
			list.tail = elem;
		}
		if (list.head == null) {
			list.head = elem;
		}
		++list.num;

		return elem;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_SetListValue -> Util.SetListValue
	 * 
	 * Modifies the value associated with a list element.
	 * ------------------------------------------------------------------------
	 */

	static void SetListValue(Itcl_ListElem elem, // list element being modified
			Object val) // new value associated with element
	{
		Itcl_List list = elem.owner;
		Assert(list.validate == Util.VALID_LIST,
				"list.validate == Util.VALID_LIST");
		Assert(elem != null, "elem != null");

		elem.value = val;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_NextListElem -> Util.NextListElem
	 * 
	 * Returns the next list element.
	 * ------------------------------------------------------------------------
	 */

	static Itcl_ListElem NextListElem(Itcl_ListElem elem) {
		return elem.next;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_PrevListElem -> Util.PrevListElem
	 * 
	 * Returns the prev list element.
	 * ------------------------------------------------------------------------
	 */

	static Itcl_ListElem PrevListElem(Itcl_ListElem elem) {
		return elem.prev;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_FirstListElem -> Util.FirstListElem
	 * 
	 * Returns the first list element.
	 * ------------------------------------------------------------------------
	 */

	static Itcl_ListElem FirstListElem(Itcl_List l) {
		return l.head;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_LastListElem -> Util.LastListElem
	 * 
	 * Returns the last list element.
	 * ------------------------------------------------------------------------
	 */

	static Itcl_ListElem LastListElem(Itcl_List l) {
		return l.tail;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_GetListLength -> Util.GetListLength
	 * 
	 * Returns the list length.
	 * ------------------------------------------------------------------------
	 */

	static int GetListLength(Itcl_List l) {
		return l.num;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_GetListValue -> Util.GetListValue
	 * 
	 * Get the list element value.
	 * ------------------------------------------------------------------------
	 */

	static Object GetListValue(Itcl_ListElem elem) {
		return elem.value;
	}

	/*
	 * ========================================================================
	 * REFERENCE-COUNTED DATA
	 * 
	 * The following procedures manage generic reference-counted data. They are
	 * similar in spirit to the Tcl_Preserve/Tcl_Release procedures defined in
	 * the Tcl/Tk core. But these procedures use a hash table instead of a
	 * linked list to maintain the references, so they scale better. Also, the
	 * Tcl procedures have a bad behavior during the "exit" command. Their exit
	 * handler shuts them down when other data is still being reference-counted
	 * and cleaned up.
	 * 
	 * ------------------------------------------------------------------------
	 * Itcl_EventuallyFree -> Util.EventuallyFree
	 * 
	 * Registers a piece of data so that it will be freed when no longer in use.
	 * The data is registered with an initial usage count of "0". Future calls
	 * to Itcl_PreserveData() increase this usage count, and calls to
	 * Itcl_ReleaseData() decrease the count until it reaches zero and the data
	 * is freed.
	 * ------------------------------------------------------------------------
	 */

	static void EventuallyFree(ItclEventuallyFreed fobj) // Object to be freed
	// eventually
	{
		// No-op since Util.PreserveData() does everything we need.
		return;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_PreserveData -> Util.PreserveData
	 * 
	 * Increases the usage count for a piece of data that will be freed later
	 * when no longer needed. Each call to Itcl_PreserveData() puts one claim on
	 * a piece of data, and subsequent calls to Itcl_ReleaseData() remove those
	 * claims. When Itcl_EventuallyFree() is called, and when the usage count
	 * reaches zero, the data is freed.
	 * ------------------------------------------------------------------------
	 */

	static void PreserveData(ItclEventuallyFreed fobj) // data to be preserved
	{
		ItclPreservedData chunk;

		// If the fobj value is null, do nothing.

		if (fobj == null) {
			return;
		}

		// ItclPreservedList already intialized so that it
		// can be used as a monitor.

		synchronized (ItclPreservedList) {

			// Find the data in the global list and bump its usage count.

			chunk = (ItclPreservedData) ItclPreservedList.get(fobj);
			if (chunk == null) {
				chunk = new ItclPreservedData();
				chunk.fobj = fobj;
				chunk.usage = 0;
				ItclPreservedList.put(fobj, chunk);
			} else {
				// No-op
			}

			// Only increment the usage if it is non-negative.
			// Negative numbers mean that the data is in the process
			// of being destroyed by Itcl_ReleaseData(), and should
			// not be further preserved.

			if (chunk.usage >= 0) {
				chunk.usage++;
			}

		} // end synchronized block
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_ReleaseData -> Util.ReleaseData
	 * 
	 * Decreases the usage count for a piece of data that was registered
	 * previously via Itcl_PreserveData(). After Itcl_EventuallyFree() is called
	 * and the usage count reaches zero, the data is automatically freed.
	 * ------------------------------------------------------------------------
	 */

	static void ReleaseData(ItclEventuallyFreed fobj) // data to be released
	{
		ItclPreservedData chunk;
		boolean delEntry = false;

		// If the fobj value is null, do nothing.

		if (fobj == null) {
			return;
		}

		// Otherwise, find the data in the global list and
		// decrement its usage count.

		synchronized (ItclPreservedList) {

			chunk = (ItclPreservedData) ItclPreservedList.get(fobj);

			Assert(chunk != null, "chunk != null");

			// Only decrement the usage if it is non-negative.
			// When the usage reaches zero, set it to a negative number
			// to indicate that data is being destroyed, and then
			// invoke the client delete proc. When the data is deleted,
			// remove the entry from the preservation list.

			if (chunk.usage > 0 && --chunk.usage == 0) {
				chunk.usage = -1; // cannot preserve/release anymore
				delEntry = true;
			}

		} // end synchronized block

		if (delEntry) {
			fobj.eventuallyFreed();

			synchronized (ItclPreservedList) {
				ItclPreservedList.remove(fobj);
			} // end synchronized block
		}
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_SaveInterpState -> Util.SaveInterpState
	 * 
	 * Takes a snapshot of the current result state of the interpreter. The
	 * snapshot can be restored at any point by Itcl_RestoreInterpState. So if
	 * you are in the middle of building a return result, you can snapshot the
	 * interpreter, execute a command that might generate an error, restore the
	 * snapshot, and continue building the result string.
	 * 
	 * Once a snapshot is saved, it must be restored by calling
	 * Itcl_RestoreInterpState, or discarded by calling Itcl_DiscardInterpState.
	 * Otherwise, memory will be leaked.
	 * 
	 * Returns a token representing the state of the interpreter.
	 * ------------------------------------------------------------------------
	 */

	static Itcl_InterpState SaveInterpState(Interp interp, // interpreter being
			// modified
			int status) // integer status code for current operation
	{
		Itcl_InterpState info;
		TclObject val = null;

		info = new Itcl_InterpState();
		info.validate = Util.STATE_VALID;
		info.status = status;
		info.errorInfo = null;
		info.errorCode = null;

		// Get the result object from the interpreter. This synchronizes
		// the old-style result, so we don't have to worry about it.
		// Keeping the object result is enough.

		info.objResult = interp.getResult();
		info.objResult.preserve();

		// If an error is in progress, preserve its state.

		try {
			val = null;
			val = interp.getVar("errorInfo", TCL.GLOBAL_ONLY);
		} catch (TclException ex) {
		}
		if (val != null && (val.toString().length() > 0)) {
			info.errorInfo = val;
			info.errorInfo.preserve();
		}

		try {
			val = null;
			val = interp.getVar("errorCode", TCL.GLOBAL_ONLY);
		} catch (TclException ex) {
		}
		if (val != null && (val.toString().length() > 0)) {
			info.errorCode = val;
			info.errorCode.preserve();
		}

		// Now, reset the interpreter to a clean state.

		interp.resetResult();

		return info;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_RestoreInterpState -> Util.RestoreInterpState
	 * 
	 * Restores the state of the interpreter to a snapshot taken by
	 * Itcl_SaveInterpState. This affects variables such as "errorInfo" and
	 * "errorCode". After this call, the token for the interpreter state is no
	 * longer valid.
	 * 
	 * Returns the status code that was pending at the time the state was
	 * captured.
	 * ------------------------------------------------------------------------
	 */

	static int RestoreInterpState(Interp interp, // interpreter being modified
			Itcl_InterpState state) // token representing interpreter state
	{
		Itcl_InterpState info = state;
		int status;

		Assert(info.validate == Util.STATE_VALID,
				"info.validate == Util.STATE_VALID");

		interp.resetResult();

		// If an error is in progress, restore its state.
		// Set the error code the hard way--set the variable directly
		// and fix the interpreter flags. Otherwise, if the error code
		// string is really a list, it will get wrapped in extra {}'s.

		if (info.errorInfo != null) {
			interp.addErrorInfo(info.errorInfo.toString());
			info.errorInfo.release();
			info.errorInfo = null;
		}

		if (info.errorCode != null) {
			interp.setErrorCode(info.errorCode);
			info.errorCode.release();
			info.errorCode = null;
		}

		// Assign the object result back to the interpreter, then
		// release our hold on it.

		interp.setResult(info.objResult);
		info.objResult.release();
		info.objResult = null;

		status = info.status;
		info.validate = 0;
		info = null;

		return status;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_DiscardInterpState -> Util.DiscardInterpState
	 * 
	 * Frees the memory associated with an interpreter snapshot taken by
	 * Itcl_SaveInterpState. If the snapshot is not restored, this procedure
	 * must be called to discard it, or the memory will be lost. After this
	 * call, the token for the interpreter state is no longer valid.
	 * ------------------------------------------------------------------------
	 */

	static void DiscardInterpState(Itcl_InterpState state) // token representing
	// interpreter state
	{
		Itcl_InterpState info = state;

		Assert(info.validate == Util.STATE_VALID,
				"info.validate == Util.STATE_VALID");

		if (info.errorInfo != null) {
			info.errorInfo.release();
			info.errorInfo = null;
		}
		if (info.errorCode != null) {
			info.errorCode.release();
			info.errorCode = null;
		}
		info.objResult.release();
		info.objResult = null;

		info.validate = 0;
		info = null;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_Protection -> Util.Protection
	 * 
	 * Used to query/set the protection level used when commands/variables are
	 * defined within a class. The default protection level (when no
	 * public/protected/private command is active) is ITCL_DEFAULT_PROTECT. In
	 * the default case, new commands are treated as public, while new variables
	 * are treated as protected.
	 * 
	 * If the specified level is 0, then this procedure returns the current
	 * value without changing it. Otherwise, it sets the current value to the
	 * specified protection level, and returns the previous value.
	 * ------------------------------------------------------------------------
	 */

	static int Protection(Interp interp, // interpreter being queried
			int newLevel) // new protection level or 0
	{
		int oldVal;
		ItclObjectInfo info;

		// If a new level was specified, then set the protection level.
		// In any case, return the protection level as it stands right now.

		info = (ItclObjectInfo) interp.getAssocData(ItclInt.INTERP_DATA);

		Assert(info != null, "info != null");
		oldVal = info.protection;

		if (newLevel != 0) {
			Assert(newLevel == Itcl.PUBLIC || newLevel == Itcl.PROTECTED
					|| newLevel == Itcl.PRIVATE
					|| newLevel == Itcl.DEFAULT_PROTECT, "newLevel Protection");
			info.protection = newLevel;
		}
		return oldVal;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_ProtectionStr -> Util.ProtectionStr
	 * 
	 * Converts an integer protection code (ITCL.PUBLIC, ITCL.PROTECTED, or
	 * ITCL.PRIVATE) into a human-readable character string. Returns a pointer
	 * to this string.
	 * ------------------------------------------------------------------------
	 */

	static String ProtectionStr(int pLevel) // protection level
	{
		switch (pLevel) {
		case Itcl.PUBLIC:
			return "public";
		case Itcl.PROTECTED:
			return "protected";
		case Itcl.PRIVATE:
			return "private";
		}
		return "<bad-protection-code>";
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_CanAccess -> Util.CanAccess
	 * 
	 * Checks to see if a class member can be accessed from a particular
	 * namespace context. Public things can always be accessed. Protected things
	 * can be accessed if the "from" namespace appears in the inheritance
	 * hierarchy of the class namespace. Private things can be accessed only if
	 * the "from" namespace is the same as the class that contains them.
	 * 
	 * Returns true/false.
	 * ------------------------------------------------------------------------
	 */

	static boolean CanAccess(ItclMember member, // class member being tested
			Namespace fromNs) // namespace requesting access
	{
		ItclClass fromCd;
		Object entry;

		// If the protection level is "public" or "private", then the
		// answer is known immediately.

		if (member.protection == Itcl.PUBLIC) {
			return true;
		} else if (member.protection == Itcl.PRIVATE) {
			return (member.classDefn.namesp == fromNs);
		}

		// If the protection level is "protected", then check the
		// heritage of the namespace requesting access. If cdefnPtr
		// is in the heritage, then access is allowed.

		Assert(member.protection == Itcl.PROTECTED,
				"member.protection == Itcl.PROTECTED");

		if (Class.IsClassNamespace(fromNs)) {
			fromCd = Class.GetClassFromNamespace(fromNs);

			entry = fromCd.heritage.get(member.classDefn);

			if (entry != null) {
				return true;
			}
		}
		return false;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_CanAccessFunc -> Util.CanAccessFunc
	 * 
	 * Checks to see if a member function with the specified protection level
	 * can be accessed from a particular namespace context. This follows the
	 * same rules enforced by Itcl_CanAccess, but adds one special case: If the
	 * function is a protected method, and if the current context is a base
	 * class that has the same method, then access is allowed.
	 * 
	 * Returns true/false.
	 * ------------------------------------------------------------------------
	 */

	static boolean CanAccessFunc(ItclMemberFunc mfunc, // member function being
			// tested
			Namespace fromNs) // namespace requesting access
	{
		ItclClass cd, fromCd;
		ItclMemberFunc ovlfunc;
		Object entry;

		// Apply the usual rules first.

		if (Util.CanAccess(mfunc.member, fromNs)) {
			return true;
		}

		// As a last resort, see if the namespace is really a base
		// class of the class containing the method. Look for a
		// method with the same name in the base class. If there
		// is one, then this method overrides it, and the base class
		// has access.

		if ((mfunc.member.flags & ItclInt.COMMON) == 0
				&& Class.IsClassNamespace(fromNs)) {

			cd = mfunc.member.classDefn;
			fromCd = Class.GetClassFromNamespace(fromNs);

			if (cd.heritage.get(fromCd) != null) {
				entry = fromCd.resolveCmds.get(mfunc.member.name);

				if (entry != null) {
					ovlfunc = (ItclMemberFunc) entry;
					if ((ovlfunc.member.flags & ItclInt.COMMON) == 0
							&& ovlfunc.member.protection < Itcl.PRIVATE) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_GetTrueNamespace -> Util.GetTrueNamespace
	 * 
	 * Returns the current namespace context. This procedure is similar to
	 * Tcl_GetCurrentNamespace, but it supports the notion of "transparent" call
	 * frames installed by Itcl_HandleInstance.
	 * 
	 * Returns a pointer to the current namespace calling context.
	 * ------------------------------------------------------------------------
	 */

	static Namespace GetTrueNamespace(Interp interp, // interpreter being
			// queried
			ItclObjectInfo info) // object info associated with interp
	{
		int i;
		boolean transparent;
		CallFrame frame, transFrame;
		Namespace contextNs;

		// See if the current call frame is on the list of transparent
		// call frames.

		transparent = false;

		frame = Migrate.GetCallFrame(interp, 0);
		for (i = Util.GetStackSize(info.transparentFrames) - 1; i >= 0; i--) {
			transFrame = (CallFrame) Util.GetStackValue(info.transparentFrames,
					i);

			if (frame == transFrame) {
				transparent = true;
				break;
			}
		}

		// If this is a transparent call frame, return the namespace
		// context one level up.

		if (transparent) {
			frame = Migrate.GetCallFrame(interp, 1);
			if (frame != null) {
				contextNs = ItclAccess.getCallFrameNamespace(frame);
			} else {
				contextNs = Namespace.getGlobalNamespace(interp);
			}
		} else {
			contextNs = Namespace.getCurrentNamespace(interp);
		}
		return contextNs;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_ParseNamespPath -> Util.ParseNamespPath
	 * 
	 * Parses a reference to a namespace element of the form:
	 * 
	 * namesp::namesp::namesp::element
	 * 
	 * Returns and object that contains a head and tail part. head part
	 * ("namesp::namesp::namesp"), tail part ("element") If the head part is
	 * missing, a the head member will be null and the rest of the string is
	 * returneed as the tail.
	 * ------------------------------------------------------------------------
	 */

	static ParseNamespPathResult ParseNamespPath(String name) // path name to
	// class member
	{
		int i;
		String head, tail;

		// Copy the name into the buffer and parse it. Look
		// backward from the end of the string to the first '::'
		// scope qualifier.

		i = name.length();

		while (--i > 0) {
			if (name.charAt(i) == ':' && name.charAt(i - 1) == ':') {
				break;
			}
		}

		// Found head/tail parts. If there are extra :'s, keep backing
		// up until the head is found. This supports the Tcl namespace
		// behavior, which allows names like "foo:::bar".

		if (i > 0) {
			tail = name.substring(i + 1);

			while (i > 0 && name.charAt(i - 1) == ':') {
				i--;
			}
			head = name.substring(0, i);
		}

		// No :: separators--the whole name is treated as a tail.

		else {
			tail = name;
			head = null;
		}

		return new ParseNamespPathResult(head, tail);
	}

	static class ParseNamespPathResult {
		String head;
		String tail;

		ParseNamespPathResult(String head, String tail) {
			this.head = head;
			this.tail = tail;
		}
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_DecodeScopedCommand -> Util.DecodeScopedCommand
	 * 
	 * Decodes a scoped command of the form:
	 * 
	 * namespace inscope <namesp> <command>
	 * 
	 * If the given string is not a scoped value, this procedure does nothing
	 * and returns a null rNs and the passed in name value as the rCmd value. If
	 * the string is a scoped value then it is decoded, and the namespace, and
	 * the simple command string are returned. If anything goes wrong, this
	 * procedure raises a TclException.
	 * ------------------------------------------------------------------------
	 */

	static DecodeScopedCommandResult DecodeScopedCommand(Interp interp, // current
			// interpreter
			String name) // string to be decoded
			throws TclException {
		Namespace ns = null;
		String cmdName;
		final int len = name.length();
		int pos;
		TclObject[] listv = null;
		TclException ex = null;

		cmdName = name;

		if ((len > 17) && name.startsWith("namespace ")) {
			for (pos = 9; (pos < len && name.charAt(pos) == ' '); pos++) {
				// empty body: skip over spaces
			}
			if (((pos + 8) <= len) && (name.charAt(pos) == 'i')
					&& (name.substring(pos, pos + 8).equals("inscope "))) {
				try {
					listv = TclList.getElements(interp, TclString
							.newInstance(name));
				} catch (TclException e) {
					ex = e;
				}
				if (ex == null) {
					if (listv.length != 4) {
						// Create exception, then add error info below before
						// throwing
						ex = new TclException(interp, "malformed command \""
								+ name + "\": should be \""
								+ "namespace inscope namesp command\"");
					} else {
						String findNS = listv[2].toString();
						ns = Namespace.findNamespace(interp, findNS, null,
								TCL.LEAVE_ERR_MSG);

						if (ns == null) {
							ex = new TclException(interp, interp.getResult()
									.toString());
						} else {
							cmdName = listv[3].toString();
						}
					}
				}

				if (ex != null) {
					String msg = "\n    (while decoding scoped command \""
							+ name + "\")";
					interp.addErrorInfo(msg);
					throw ex;
				}
			}
		}

		DecodeScopedCommandResult r = new DecodeScopedCommandResult();
		r.rNS = ns;
		r.rCmd = cmdName;
		return r;
	}

	static class DecodeScopedCommandResult {
		Namespace rNS; // returns: namespace for scoped value
		String rCmd; // returns: simple command word
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_EvalArgs -> Util.EvalArgs
	 * 
	 * This procedure invokes a list of (objc,objv) arguments as a single
	 * command. It is similar to Tcl_EvalObj, but it doesn't do any parsing or
	 * compilation. It simply treats the first argument as a command and invokes
	 * that command in the current context.
	 * 
	 * Returns if successful. Otherwise, this procedure raises a TclException.
	 * ------------------------------------------------------------------------
	 */

	static void EvalArgs(Interp interp, // current interpreter
			TclObject[] objv) // argument objects
			throws TclException {
		WrappedCommand wcmd;
		TclObject cmdline = null;
		TclObject[] cmdlinev;

		// Resolve command name to WrappedCommand

		wcmd = Namespace.findCommand(interp, objv[0].toString(), null, 0);

		cmdlinev = objv;

		// If the command is still not found, handle it with the
		// "unknown" proc.

		if (wcmd == null) {
			wcmd = Namespace.findCommand(interp, "unknown", null,
					TCL.GLOBAL_ONLY);

			if (wcmd == null) {
				interp.resetResult();
				throw new TclException(interp, "invalid command name \""
						+ objv[0].toString() + "\"");
			}

			cmdline = Util.CreateArgs(interp, "unknown", objv, 0);
			cmdlinev = TclList.getElements(interp, cmdline);
		}

		// Finally, invoke the command's cmdProc()

		interp.resetResult();
		if (wcmd.mustCallInvoke(interp)) wcmd.invoke(interp, cmdlinev);
		else wcmd.cmd.cmdProc(interp, cmdlinev);
	}

	/*
	 * ------------------------------------------------------------------------
	 * Itcl_CreateArgs -> Util.CreateArgs
	 * 
	 * This procedure takes a string and a list of objv arguments, and glues
	 * them together in a single list. This is useful when a command word needs
	 * to be prepended or substituted into a command line before it is executed.
	 * The arguments are returned in a single list object, and they can be
	 * retrieved by calling Tcl_ListObjGetElements. When the arguments are no
	 * longer needed, they should be discarded by decrementing the reference
	 * count for the list object.
	 * 
	 * Returns a list object containing the arguments.
	 * ------------------------------------------------------------------------
	 */

	static TclObject CreateArgs(Interp interp, // current interpreter
			String string, // first command word
			TclObject[] objv, // argument objects, can be null
			int skip) // number of argument objects to skip
			throws TclException {
		int i;
		TclObject list;

		list = TclList.newInstance();
		if (string != null) {
			TclList.append(interp, list, TclString.newInstance(string));
		}

		if (objv != null) {
			for (i = skip; i < objv.length; i++) {
				TclList.append(interp, list, objv[i]);
			}
		}

		list.preserve();
		return list;
	}

	/*
	 * ------------------------------------------------------------------------
	 * Tcl_DStringStartSublist -> Util.StartSublist
	 * 
	 * This procedure appends a open brace character to a StringBuffer.
	 * ------------------------------------------------------------------------
	 */

	static void StartSublist(StringBuffer buffer) {
		if (NeedSpace(buffer)) {
			buffer.append(" {");
		} else {
			buffer.append('{');
		}
	}

	/*
	 * ------------------------------------------------------------------------
	 * Tcl_DStringEndSublist -> Util.EndSublist
	 * 
	 * This procedure appends a close brace character to a StringBuffer.
	 * ------------------------------------------------------------------------
	 */
	static void EndSublist(StringBuffer buffer) {
		buffer.append('}');
	}

	/*
	 * ------------------------------------------------------------------------
	 * Tcl_DStringAppendElement -> Util.AppendElement
	 * 
	 * This procedure appends a list element to a StringBuffer.
	 * ------------------------------------------------------------------------
	 */

	static void AppendElement(StringBuffer buffer, String elem) {
		if (NeedSpace(buffer)) {
			buffer.append(' ');
		}
		buffer.append(elem);
	}

	/*
	 * ------------------------------------------------------------------------
	 * TclNeedSpace -> Util.NeedSpace
	 * 
	 * This procedure checks to see whether it is appropriate to add a space
	 * before appending a new list element to an existing string.
	 * ------------------------------------------------------------------------
	 */

	static boolean NeedSpace(StringBuffer buffer) {
		final int len = buffer.length();

		// A space is needed unless either
		// (a) we're at the start of the string, or

		if (len == 0) {
			return false;
		}

		// (b) we're at the start of a nested list-element, quoted with an
		// open curly brace; we can be nested arbitrarily deep, so long
		// as the first curly brace starts an element, so backtrack over
		// open curly braces that are trailing characters of the string; and

		int end = len - 1;
		while (buffer.charAt(end) == '{') {
			if (end == 0) {
				return false;
			}
			end--;
		}

		// (c) the trailing character of the string is already a list-element
		// separator. With the condition that the penultimate character
		// is not a backslash.

		if (Character.isSpaceChar(buffer.charAt(end))
				&& ((end == 0) || (buffer.charAt(end - 1) != '\\'))) {
			return false;
		}

		return true;
	}

} // end class Util

