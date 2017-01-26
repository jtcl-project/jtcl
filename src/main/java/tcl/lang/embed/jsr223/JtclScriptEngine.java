package tcl.lang.embed.jsr223;

/*
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 * Use is subject to license terms.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met: Redistributions of source code
 * must retain the above copyright notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials
 * provided with the distribution. Neither the name of the Sun Microsystems nor the names of
 * is contributors may be used to endorse or promote products derived from this software
 * without specific prior written permission.

 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY
 * AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * JtclScriptEngine.java
 * @author A. Sundararajan
 */

import javax.script.*;
import java.io.*;
import tcl.lang.*;

public class JtclScriptEngine extends AbstractScriptEngine {

    // my factory, may be null
    private ScriptEngineFactory factory;

    // we maintain thread-local cache of interpreters
    private static ThreadLocal<Interp> interpreters;
    static {
        interpreters = new ThreadLocal<Interp>();
    }


    private static final String CONTEXT = "javax.script.ScriptContext";

    private static class ContextData implements AssocData {
        ContextData(ScriptContext ctx) {
            context = ctx;
        }

        ScriptContext getContext() {
            return context;
        }

        public void disposeAssocData(Interp interp) {
            // do nothing...
        }

        private ScriptContext context;
    }


    private static TclObject java2tcl(Interp interp, Object javaObj)
            throws TclException {
        Class cls = (javaObj == null)? Object.class : javaObj.getClass();

        if (cls == Integer.class) {
            return TclInteger.newInstance(((Integer) javaObj).intValue());
        } else if (cls == Long.class) {
            // A long can not be represented as a TclInteger
            return TclString.newInstance(javaObj.toString());
        } else if (cls == Short.class) {
            return TclInteger.newInstance(((Short) javaObj).intValue());
        } else if (cls == Byte.class) {
            return TclInteger.newInstance(((Byte) javaObj).intValue());
        } else if (cls == Double.class) {
            return TclDouble.newInstance(((Double) javaObj).doubleValue());
        } else if (cls == Float.class) {
            return TclDouble.newInstance(((Float) javaObj).doubleValue());
        } else if (cls == Boolean.class) {
            return TclBoolean.newInstance(((Boolean) javaObj).booleanValue());
        } else if (cls == Character.class) {
            return TclString.newInstance(((Character) javaObj).toString());
        } else if (cls == String.class) {
            return TclString.newInstance((String)javaObj);
        } else {
            return tcl.pkg.java.ReflectObject.newInstance(interp, cls, javaObj);
        }
    }

    private static Object tcl2java(Interp interp, TclObject tclObj)
            throws TclException {
        Object javaObject = null;
        boolean isReflectObj = false;

        try {
            javaObject = tcl.pkg.java.ReflectObject.get(interp, tclObj);
            isReflectObj = true;
        } catch (TclException e) {
            interp.resetResult();
        }

        if (isReflectObj) {
            return javaObject;
        } else {
            // TCL primitive type
            InternalRep rep = tclObj.getInternalRep();
            if (rep instanceof TclBoolean) {
                return new Boolean(TclBoolean.get(interp, tclObj));
            } else if (rep instanceof TclInteger) {
                return new Integer(TclInteger.get(interp, tclObj));
            } else if (rep instanceof TclDouble) {
                return new Double(TclDouble.get(interp, tclObj));
            } else if (rep instanceof TclString) {
                return tclObj.toString();
            } else {
                // FIXME: Is this an error??
                return tclObj;
            }
        }
    }

    private static class ContextCommand implements Command {
        private void getVariable(Interp interp, ScriptContext ctx,
                                 String name) throws TclException {
            synchronized(ctx) {
                int scope = ctx.getAttributesScope(name);
                if (scope != -1) {
                    Object val = ctx.getAttribute(name, scope);
                    interp.setResult(java2tcl(interp, val));
                } else {
                    throw new TclException(interp, "can't read \"" + name +
                            "\": no such context variable");
                }
            }
        }

        private void setVariable(Interp interp, ScriptContext ctx,
                                 String name, TclObject value) throws TclException {
            synchronized (ctx) {
                int scope = ctx.getAttributesScope(name);
                if (scope == -1) {
                    scope = ScriptContext.ENGINE_SCOPE;
                }
                ctx.setAttribute(name, tcl2java(interp, value), scope);
                interp.setResult(value);
            }
        }

        private void deleteVariable(Interp interp, ScriptContext ctx,
                                    String name) throws TclException {
            synchronized (ctx) {
                int scope = ctx.getAttributesScope(name);
                if (scope == -1) {
                    // nothing to delete
                    interp.setResult(false);
                } else {
                    ctx.removeAttribute(name, scope);
                    interp.setResult(true);
                }
            }
        }

        public void cmdProc(Interp interp, TclObject[] argv)
                throws TclException {
            AssocData data = interp.getAssocData(CONTEXT);
            if (data != null && data instanceof ContextData) {
                ScriptContext context = ((ContextData)data).getContext();
                switch (argv.length) {
                    case 1:
                        // default variable is context itself!
                        getVariable(interp, context, "context");
                        break;
                    case 2:
                        getVariable(interp, context, argv[1].toString());
                        break;
                    case 3: {
                        String arg1 = argv[1].toString();
                        if (arg1.equals("-del")) {
                            String arg2 = argv[2].toString();
                            deleteVariable(interp, context, arg2);
                        } else {
                            setVariable(interp, context,
                                    argv[1].toString(), argv[2]);
                        }
                        break;
                    }
                    default:
                        throw new TclNumArgsException(interp, 1, argv,
                                "?varName? ?-del? ?newValue?");
                }
            } else {
                throw new TclException(interp, "invalid script context");
            }
        }
    }

    private static class EchoCommand implements Command {
        public void cmdProc(Interp interp, TclObject[] argv)
                throws TclException {
            AssocData data = interp.getAssocData(CONTEXT);
            Writer writer;
            if (data != null && data instanceof ContextData) {
                ScriptContext context = ((ContextData)data).getContext();
                writer = context.getWriter();
            } else {
                writer = new PrintWriter(System.out);
            }

            try {
                for (int i = 1; i < argv.length; i++) {
                    writer.write(argv[i].toString());
                    writer.write(' ');
                }
                writer.flush();
            } catch (IOException exp) {
                throw new TclException(interp, "I/O error: " + exp.getMessage());
            }
        }
    }


    public Object eval(String str, ScriptContext ctx)
            throws ScriptException {
        Interp interp = getInterp();
        AssocData oldAssocData = interp.getAssocData(CONTEXT);
        try {
            interp.setAssocData(CONTEXT, new ContextData(ctx));
            ctx.setAttribute("context", ctx, ScriptContext.ENGINE_SCOPE);
            interp.eval(str);
            return interp.getResult();
        } catch (TclException exp) {
            String errMsg = interp.getResult().toString();
            ScriptException se = new ScriptException(errMsg);
            se.initCause(exp);
            throw se;
        } finally {
            if (oldAssocData != null) {
                interp.setAssocData(CONTEXT, oldAssocData);
            }
        }
    }

    public Object eval(Reader reader, ScriptContext ctx)
            throws ScriptException {
        return eval(readFully(reader), ctx);
    }

    public ScriptEngineFactory getFactory() {
        synchronized (this) {
            if (factory == null) {
                factory = new JtclScriptEngineFactory();
            }
        }
        return factory;
    }

    public Bindings createBindings() {
        return new SimpleBindings();
    }

    void setFactory(ScriptEngineFactory factory) {
        this.factory = factory;
    }

    private String readFully(Reader reader) throws ScriptException {
        char[] arr = new char[8*1024]; // 8K at a time
        StringBuilder buf = new StringBuilder();
        int numChars;
        try {
            while ((numChars = reader.read(arr, 0, arr.length)) > 0) {
                buf.append(arr, 0, numChars);
            }
        } catch (IOException exp) {
            throw new ScriptException(exp);
        }
        return buf.toString();
    }

    private Interp getInterp() {
        Interp interp;
        if ((interp = interpreters.get()) == null) {
            interp = new Interp();
            ContextCommand cmd = new ContextCommand();
            interp.createCommand("context", cmd);
            // alias for 'context' command...
            interp.createCommand("var", cmd);
            interp.createCommand("echo", new EchoCommand());
            interpreters.set(interp);
        }
        return interp;
    }

}
