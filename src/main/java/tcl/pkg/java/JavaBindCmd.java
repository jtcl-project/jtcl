/*
 * JavaBindCmd.java --
 *
 *	Implements the java::bind command.
 *
 * Copyright (c) 1997 Sun Microsystems, Inc.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 *
 * RCS: @(#) $Id: JavaBindCmd.java,v 1.3 1999/05/09 21:44:48 dejong Exp $
 */

package tcl.pkg.java;

import java.beans.BeanInfo;
import java.beans.EventSetDescriptor;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.lang.reflect.Method;
import java.util.Hashtable;

import tcl.lang.Command;
import tcl.lang.Interp;
import tcl.lang.TclException;
import tcl.lang.TclNumArgsException;
import tcl.lang.TclObject;

/**
 * This class implements the built-in "java::bind" command in Tcl.
 */

public class JavaBindCmd implements Command {

	// The Bean Event Manager associated with the interp that owns this
	// BindCmd instance.

	BeanEventMgr eventMgr = null;

	// Caches the BeanInfo for each Java class. The
	// Introspector.getBeanInfo class in JDK 1.2 returns new instances of
	// BeanInfo for each call. That causes a lot of problems in Jacl,
	// which assumes that there is the BeanInfo (and EventSetDescriptor,
	// etc) associated with each class is always constant (i.e., always the
	// same object).
	//
	// This cache allows us to always use the same BeanInfo instance for each
	// Java class.

	private Hashtable beanInfoCache = new Hashtable();

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * cmdProc --
	 * 
	 * This procedure is invoked as part of the Command interface to process the
	 * "java::bind" Tcl command. See the user documentation for details on what
	 * it does.
	 * 
	 * Results: None.
	 * 
	 * Side effects: See the user documentation.
	 * 
	 * ----------------------------------------------------------------------
	 */

	public void cmdProc(Interp interp, // Current interpreter.
			TclObject argv[]) // Argument list.
			throws TclException // A standard Tcl exception.
	{
		if ((argv.length < 2) || (argv.length > 4)) {
			throw new TclNumArgsException(interp, 1, argv,
					"javaObj ?eventName? ?command?");
		}

		ReflectObject robj = ReflectObject.getReflectObject(interp, argv[1]);

		if (eventMgr == null) {
			eventMgr = BeanEventMgr.getBeanEventMgr(interp);
		}

		if (argv.length == 2) {
			// Return the list of all events handled by this widget.

			interp.setResult(eventMgr.getHandledEvents(robj));
		} else {
			EventSetDescriptor eventDesc;
			Method method;

			Object arr[] = getEventMethod(interp, robj.javaObj, robj.javaClass,
					argv[2].toString());

			eventDesc = (EventSetDescriptor) arr[0];

			if (!eventDesc.getListenerType().isInterface()) {
				throw new TclException(interp, "Cannot handle event listener: "
						+ "listererType \"" + eventDesc.getListenerType()
						+ "\" is not an interface");
			}

			method = (Method) arr[1];

			if (argv.length == 3) {
				// Return the script for the given event.

				TclObject script = eventMgr.getBinding(interp, robj, eventDesc,
						method);

				if (script != null) {
					interp.setResult(script);
				} else {
					interp.resetResult();
				}
			} else {
				// Set the script for the given event.

				eventMgr.setBinding(interp, robj, eventDesc, method, argv[3]);
			}
		}
	}

	/*
	 * ----------------------------------------------------------------------
	 * 
	 * getEventMethod --
	 * 
	 * Returns the EventSet and event listener method represented by a string
	 * name. The string name must be in one of the following formats: +
	 * className.listenerMethod + listenerMethod The first format will always
	 * work. The second and third format may cause an error if there is an
	 * ambiguity.
	 * 
	 * Return value: If successful, returns an Object array of two elements.
	 * arr[0] is the EventSetDescriptor and arr[1] is the Method, as given by
	 * eventName.
	 * 
	 * Side effects: None.
	 * 
	 * ----------------------------------------------------------------------
	 */

	Object[] getEventMethod(Interp interp, // Current interpreter.
			Object obj, // The object whose event listener methods
			// are to be queried.
			Class cls, // The class of the event object
			String eventName) // The string name of the event.
			throws TclException // If the method cannot be found, or if
	// eventName is ambiguous.
	{
		EventSetDescriptor eventDesc = null;
		Method method = null;
		int dotPos, i;

		search: {
			BeanInfo beanInfo;

			try {
				beanInfo = (BeanInfo) beanInfoCache.get(cls);
				if (beanInfo == null) {
					// System.out.println("Introspecting " + cls);
					beanInfo = Introspector.getBeanInfo(cls);
					beanInfoCache.put(cls, beanInfo);

				}
			} catch (IntrospectionException e) {
				break search;
			}
			EventSetDescriptor[] events = beanInfo.getEventSetDescriptors();

			if (events == null) {
				break search;
			}

			dotPos = eventName.lastIndexOf('.');
			if (dotPos == -1) {
				// the event string specifies only the event method. Must
				// ensure that exactly one event interface has this
				// method.

				for (i = 0; i < events.length; i++) {
					Method methods[] = events[i].getListenerType().getMethods();
					for (int j = 0; j < methods.length; j++) {
						if (methods[j].getName().equals(eventName)) {
							if (method == null) {
								method = methods[j];
								eventDesc = events[i];
							} else {
								throw new TclException(interp,
										"ambiguous event \"" + eventName + "\"");
							}
						}
					}
				}
			} else {
				String evtCls = eventName.substring(0, dotPos);
				String evtMethod = eventName.substring(dotPos + 1);

				for (i = 0; i < events.length; i++) {
					Class lsnType = events[i].getListenerType();
					// System.out.println("event index " + i);
					// if (evtCls == null) {System.out.println("null 1");}
					// if (lsnType == null) {System.out.println("null 2");}
					// if ((lsnType != null) && (evtCls == null))
					// {System.out.println("null 3");}
					if (evtCls.equals(lsnType.getName())) {
						eventDesc = events[i];
						break;
					}
				}

				if (eventDesc == null) {
					break search;
				}

				Method methods[] = eventDesc.getListenerType().getMethods();

				if (methods == null) {
					break search;
				}
				for (int j = 0; j < methods.length; j++) {
					if (methods[j].getName().equals(evtMethod)) {
						method = methods[j];
						break;
					}
				}
			}

			if (method != null) {
				Object arr[] = new Object[2];
				arr[0] = eventDesc;
				arr[1] = method;
				return arr;
			}
		}

		throw new TclException(interp, "unknown event \"" + eventName + "\"");
	}

} // end JavaBindCmd

