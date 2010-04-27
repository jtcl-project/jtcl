/*
 * BeanEventMgr.java --
 *
 *	The Bean Event Manager: This class manages beans event
 *	handlers for a Tcl interpreter.
 *
 * Copyright (c) 1997 Sun Microsystems, Inc.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 * 
 * RCS: @(#) $Id: BeanEventMgr.java,v 1.1.1.1 1998/10/14 21:09:14 cvsadmin Exp $
 *
 */

package tcl.pkg.java;

import java.beans.EventSetDescriptor;
import java.lang.reflect.Method;
import java.util.EmptyStackException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Stack;

import tcl.lang.AssocData;
import tcl.lang.Interp;
import tcl.lang.TclException;
import tcl.lang.TclList;
import tcl.lang.TclObject;

/*
 * This class manages beans event handlers for a Tcl interpreter:
 * the event object stack, etc.
 */

class BeanEventMgr implements AssocData {

	/*
	 * Stores all of the available event adaptor classes.
	 */

	private static Hashtable adaptorClsTab = new Hashtable();

	/*
	 * The class loader for loading automatically generated event adaptor
	 * classes.
	 */

	private static AdaptorClassLoader adaptorLoader = new AdaptorClassLoader();

	/*
	 * When a event handler is invoked, it is given a set of parameters (stored
	 * in an Object array.) The eventParamSetStack variable is used to store the
	 * parameter sets in a LIFO order when nested event handlers are invoked.
	 * Event parameters can be queried by the "java::event" command.
	 */

	Stack eventParamSetStack;

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * BeanEventMgr --
	 * 
	 * Creates a new BeanEventMgr instance.
	 * 
	 * Side effects: Member fields are initialized.
	 * 
	 * ----------------------------------------------------------------------
	 */

	private BeanEventMgr() {
		eventParamSetStack = new Stack();
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * getBeanEventMgr --
	 * 
	 * Returns the BeanEventMgr instance for the given interp. A new
	 * BeanEventMgr is created if no such BeanEventMgr exists for the interp.
	 * 
	 * Results: The BeanEventMgr instance for the given interp.
	 * 
	 * Side effects: A new BeanEventMgr may be created and registered as an
	 * AssocData in the given interp.
	 * 
	 * ----------------------------------------------------------------------
	 */

	static BeanEventMgr getBeanEventMgr(Interp interp) // Query the BeanEventMgr
	// of this interp.
	{
		BeanEventMgr mgr = (BeanEventMgr) interp.getAssocData("tclBeanEvent");
		if (mgr == null) {
			mgr = new BeanEventMgr();
			interp.setAssocData("tclBeanEvent", mgr);
		}

		return mgr;
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * pushEventParamSet --
	 * 
	 * Pushes a set of event parameters to the top of the stack.
	 * 
	 * Results: None.
	 * 
	 * Side effects: The set of parameters are pushed to the top of the stack.
	 * 
	 * ----------------------------------------------------------------------
	 */

	void pushEventParamSet(BeanEventParamSet p) // The parameters to push to the
	// top of the
	// stack.
	{
		eventParamSetStack.push(p);
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * popEventParamSet --
	 * 
	 * Pops the set of event parameters from the top of the stack.
	 * 
	 * Results: None.
	 * 
	 * Side effects: The size of the event parameter set stack is reduced by
	 * one.
	 * 
	 * ----------------------------------------------------------------------
	 */

	void popEventParamSet() throws EmptyStackException // If the stack is
	// already empty.
	{
		eventParamSetStack.pop();
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * peekEventParamSet --
	 * 
	 * Returns the set of event parameters at the top of the stack.
	 * 
	 * Results: If the event parameter stack is not empty, returns the set of
	 * parameters at the top of the stack. Otherwise, returns null.
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	BeanEventParamSet peekEventParamSet() {
		if (eventParamSetStack.size() == 0) {
			return null;
		} else {
			return (BeanEventParamSet) eventParamSetStack.peek();
		}
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * disposeAssocData --
	 * 
	 * This method is called when the interpreter is destroyed or when
	 * Interp.deleteAssocData is called on a registered AssocData instance.
	 * 
	 * Results: None.
	 * 
	 * Side effects: Removes any bgerror's that haven't been reported.
	 * 
	 * ----------------------------------------------------------------------
	 */

	public void disposeAssocData(Interp interp) // The interpreter in which this
	// AssocData
	// instance is registered in.
	{
		eventParamSetStack = null;
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * setBinding --
	 * 
	 * Sets the Tcl command to be executed when the given event fires in the
	 * reflectObj. A event adaptor is created when necessary.
	 * 
	 * Results: None.
	 * 
	 * Side effects: If the reflectObj doesn't yet have an EventAdaptor to
	 * handle the event, we will attempt to create it. This may cause an
	 * appropriate event adaptor class to be generated and loaded into the JVM;
	 * the EventAdaptor will be instantiated and registered as a listener of the
	 * given EventSet on the javaObj.
	 * 
	 * ----------------------------------------------------------------------
	 */

	void setBinding(Interp interp, // Current interpreter.
			ReflectObject reflectObj, // The reflection object to create
			// event binding for.
			EventSetDescriptor eventSet, // The EventSet to bind to.
			Method event, // Identifies a specific event in
			// the EventSet to create a
			// binding for.
			TclObject command) // The command to execute when the
			// given event fires.
			throws TclException // If the adaptor class cannot be
	// generated, or if the adaptor
	// cannot be instantiated.
	{
		EventAdaptor adaptor = null;

		if (reflectObj.bindings == null) {
			reflectObj.bindings = new Hashtable();
		} else {
			adaptor = (EventAdaptor) reflectObj.bindings.get(eventSet);
		}

		if (adaptor == null) {
			Class lsnType = eventSet.getListenerType();
			Class adaptorCls = (Class) adaptorClsTab.get(lsnType);

			if (adaptorCls == null) {
				/*
				 * We have never processed this type of EventSet yet. Generate
				 * an appropriate event adaptor class and load it into the JVM.
				 */

				adaptorCls = adaptorLoader.loadEventAdaptor(interp, eventSet);
				adaptorClsTab.put(lsnType, adaptorCls);
			}

			try {
				adaptor = (EventAdaptor) adaptorCls.newInstance();
			} catch (InstantiationException e1) {
				/*
				 * adaptor will remain null. This will trigger the exception
				 * later on.
				 */
			} catch (IllegalAccessException e2) {
				/*
				 * adaptor will remain null. This will trigger the exception
				 * later on.
				 */
			}

			if (adaptor == null) {
				throw new TclException(interp,
						"couldn't instantiate adaptor class for eventset \""
								+ eventSet + "\"");
			}

			adaptor.init(interp, reflectObj.javaObj, eventSet);

			/*
			 * Save the adaptor -- we only need a single adaptor for each
			 * EventSet to handle all possible events in this set.
			 */

			reflectObj.bindings.put(eventSet, adaptor);
		}

		if (command.toString().length() > 0) {
			adaptor.setCallback(event.getName(), command);
		} else {
			/*
			 * The callback command is the empty string. This means remove any
			 * existing callback scripts. If no more callback scripts are
			 * registered in the adaptor, we'll remove it from the hashtable.
			 */

			if (adaptor.deleteCallback(event.getName()) == 0) {
				reflectObj.bindings.remove(eventSet);
				if (reflectObj.bindings.size() == 0) {
					reflectObj.bindings = null;
				}
			}
		}
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * getBinding --
	 * 
	 * Queries the command to be executed when the given event fires in this
	 * object.
	 * 
	 * Results: The command to execute when the event fires. null if no such
	 * command has be registered with the setBinding() method.
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	TclObject getBinding(Interp interp, // Current interpreter.
			ReflectObject reflectObj, // The reflection object to query.
			EventSetDescriptor eventSet, // The EventSet to bind to.
			Method event) // Identifies a specific event in
	// the EventSet to query the
	// binding for.
	{
		EventAdaptor adaptor = null;

		if (reflectObj.bindings != null) {
			adaptor = (EventAdaptor) reflectObj.bindings.get(eventSet);
		}

		if (adaptor == null) {
			return null;
		} else {
			return adaptor.getCallback(event.getName());
		}
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * getHandledEvents --
	 * 
	 * Queries all the events that are currently handled by for this object.
	 * 
	 * Results: A Tcl list of the events that are currently handled by for this
	 * object. The list is a valid empty Tcl list if this object handles no
	 * event.
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	TclObject getHandledEvents(ReflectObject reflectObj) // The reflection
	// object to query.
	{
		TclObject list = TclList.newInstance();

		if (reflectObj.bindings != null) {
			for (Enumeration e = reflectObj.bindings.elements(); e
					.hasMoreElements();) {
				EventAdaptor adaptor = (EventAdaptor) e.nextElement();
				adaptor.getHandledEvents(list);
			}
		}

		return list;
	}

} // end BeanEventMgr

