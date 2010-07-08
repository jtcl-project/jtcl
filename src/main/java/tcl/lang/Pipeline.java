package tcl.lang;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import tcl.lang.channel.Channel;
import tcl.lang.channel.FileChannel;
import tcl.lang.channel.TclByteArrayChannel;

/**
 * This class encapsulates a pipeline of operating system commands
 */

public class Pipeline implements Runnable {

	/**
	 * List of operating system commands in pipeline, in input to output order
	 */
	private ArrayList<ProcessBuilder> commands = new ArrayList<ProcessBuilder>();

	/**
	 * The processes that are started
	 */
	private ArrayList<Process> processes = new ArrayList<Process>();

	/**
	 * Channel the provides this Pipeline with input. If null, an instance of
	 * ManagedSystemIn is used for input
	 */
	private Channel pipelineInputChannel = null;

	/**
	 * Channel that this Pipeline writes the standard output of the last command
	 * to. If null, Java's System.out is used
	 */
	private Channel pipelineOutputChannel = null;

	/**
	 * Channel that this Pipeline writes standard error of all commands to. If
	 * null, Java's System.err is used
	 */
	private Channel pipelineErrorChannel = null;


	/**
	 * If true, run Pipeline in the background
	 */
	private boolean execInBackground = false;

	/**
	 * Interpreter that this Pipeline is executing in
	 */
	Interp interp = null;

	/**
	 * List of PipelineCouplers that are created for the Pipeline
	 */
	private ArrayList<PipelineCoupler> couplers = null;

	/**
	 * List of Channels that must be closed when the Pipeline completes
	 */
	private ArrayList<Channel> channelsToClose = null;

	/**
	 * Our own Managed System.in
	 */
	ManagedSystemInStream managedSystemInStream = null;
	/**
	 * The stdin coupler, which must be stopped
	 */
	PipelineCoupler stdinCoupler = null;

	/**
	 * A set of redirectors that the parser recognizes
	 */
	private static final Set<String> redirectors;
	static {
		Set<String> rd = new HashSet<String>(12);
		rd.add("<");
		rd.add("<@");
		rd.add("<<");
		rd.add(">");
		rd.add("2>");
		rd.add(">&");
		rd.add(">>");
		rd.add("2>>");
		rd.add(">>&");
		rd.add(">@");
		rd.add("2>@");
		rd.add(">&@");
		redirectors = Collections.unmodifiableSet(rd);
	}

	/**
	 * Create a Pipeline initialized with commands.
	 * 
	 * @param interp
	 *            TCL interpreter for this Pipeline
	 * @param objv
	 *            Array of TclObjects that are in the for of TCL's 'exec' args
	 * @param startIndex
	 *            index of the objv object at which to start parsing
	 * 
	 * @throws TclException
	 *             if a syntax error occurs
	 */
	public Pipeline(Interp interp, TclObject[] objv, int startIndex) throws TclException {
		ArrayList<String> commandList = new ArrayList<String>();
		File cwd = interp.getWorkingDir();
		this.interp = interp;

		/*
		 * Parse the objv array into a Pipeline
		 */
		int endIndex = objv.length - 1;
		if (objv[endIndex].toString().equals("&")) {
			setExecInBackground(true);
			--endIndex;
		}

		channelsToClose = new ArrayList<Channel>();

		for (int i = startIndex; i <= endIndex; i++) {
			String arg = objv[i].toString();

			if (arg.equals("|")) {
				if (commandList.size() == 0 || i == endIndex) {
					// error message could be more specific, but this matches
					// exec.test
					throw new TclException(interp, "illegal use of | or |& in command");
				}
				addCommand(commandList, cwd);
				commandList = new ArrayList<String>(); // reset command list
				continue;
			}

			if (arg.equals("|&")) {
				if (commandList.size() == 0 || i == endIndex) {
					// error message could be more specific, but this matches
					// exec.test
					throw new TclException(interp, "illegal use of | or |& in command");
				}
				redirectErrorStream(addCommand(commandList, cwd), true);
				commandList = new ArrayList<String>(); // reset command list
				continue;
			}

			/*
			 * Is the next object a redirector, or something prefixed with
			 * redirector (">", ">>", etc.)
			 */
			int argLen = arg.length();
			String redirector = null;
			for (int j = argLen > 3 ? 3 : argLen; j >= 1; --j) {
				redirector = arg.substring(0, j);
				if (redirectors.contains(redirector)) {
					break;
				}
				redirector = null;
			}
			if (redirector == null) {
				/*
				 * Not a redirector, so append it to the commandList and go on
				 * to next object
				 */
				commandList.add(arg);
				continue;
			} else {
				/* It was a redirector, so let's figure out what to do with it */

				/* Get the redirectee (the file or channel name) */
				String redirectee = null;
				if (arg.length() > redirector.length()) {
					redirectee = arg.substring(redirector.length());
				} else {
					/* Get it from the next object in the list */
					++i;
					if (i > endIndex) {
						throw new TclException(interp, "can't specify \"" + redirector + "\" as last word in command");
					}
					redirectee = objv[i].toString();
				}

				/* Convert redirectee to a Channel */
				Channel channel = null;
				if (redirector.contains("@")) {
					/* The redirectee is a channel name */
					channel = TclIO.getChannel(interp, redirectee);
					if (channel == null) {
						throw new TclException(interp, "could not find channel named \"" + redirectee + "\"");
					}
					if (! channel.isReadOnly()) {
						try {
							channel.flush(interp);
						} catch (IOException e) {
							throw new TclException(interp, e.getMessage());
						}
					}
				} else if (redirector.equals("<<")) {
					channel = new TclByteArrayChannel(interp, TclString.newInstance(redirectee));
					TclIO.registerChannel(interp, channel);
					channelsToClose.add(channel);
				} else {
					/* must be a file, so open it */
					FileChannel fc = new FileChannel();
					int modeFlags = 0;
					if (redirector.contains(">>")) {
						modeFlags = TclIO.CREAT | TclIO.WRONLY | TclIO.APPEND;
					} else if (redirector.contains(">")) {
						modeFlags = TclIO.CREAT | TclIO.WRONLY | TclIO.TRUNC;
					} else if (redirector.contains("<")) {
						modeFlags = TclIO.RDONLY;
					}
					try {
						fc.open(interp, redirectee, modeFlags);
						TclIO.registerChannel(interp, fc);
						channelsToClose.add(fc);
					} catch (IOException e) {
						throw new TclPosixException(interp, e, true, "couldn't " +
								(modeFlags == TclIO.RDONLY ? "read" : "write") +
								" file \"" + redirectee + "\"");
					} catch (TclPosixException e1) {
						if (interp.getResult().toString().indexOf("no such file or directory") != -1) {
							// reformat filename
							interp.setResult("couldn't " +
									(modeFlags == TclIO.RDONLY ? "read" : "write") +
									" file \"" + redirectee + "\": no such file or directory");
						}
						throw e1;
					}
					channel = fc;
				}

				/* Does this redirector apply to stderr, stdout or stdin? */
				if (redirector.startsWith("2")) {
					if (channel.isReadOnly()) {
						throw new TclException(interp, "channel \"" + channel.getChanName()
								+ "\" wasn't opened for writing");
					}
					setPipelineErrorChannel(channel);
				} else if (redirector.contains(">")) {
					if (channel.isReadOnly()) {
						throw new TclException(interp, "channel \"" + channel.getChanName()
								+ "\" wasn't opened for writing");
					}
					setPipelineOutputChannel(channel);
					if (redirector.contains("&")) {
						setPipelineErrorChannel(channel);
					}
				} else {
					// std input is redirected
					if (channel.isWriteOnly()) {
						throw new TclException(interp, "channel \"" + channel.getChanName()
								+ "\" wasn't opened for reading");
					}
					if (channel.getChanName() != null && channel.getChanName().equals("stdin")) {
						// if reading from stdin channel, substitute our own
						// ManagedSystemInStream
						// in exec(). This helps stdin interactivity by avoiding
						// Channel buffering
						channel = null;
					} else {
						setPipelineInputChannel(channel);
					}
				}
			}
		}
		if (commandList.size() > 0) {
			addCommand(commandList, cwd);
		}
	}

	/**
	 * Add an operating system command to the end of the pipeline
	 * 
	 * @param command
	 *            The command and its arguments. The List is not copied, so any
	 *            changes will be reflected in the Pipeline
	 * @param workingDir
	 *            New command starts in this directory
	 * @return index of the new command in the pipeline
	 */
	public int addCommand(List<String> command, File workingDir) throws TclException {
		String cmd = command.get(0);
		if (cmd.startsWith("~")) {
			/* Do tilde substitution on command */
			int end = 1;
			while (end < cmd.length() && (Character.isLetterOrDigit(cmd.charAt(end)) || cmd.charAt(end)=='_'))
					++end;
			String username = cmd.substring(1,end);
			String userdir = FileUtil.doTildeSubst(interp, username);
			cmd = userdir + cmd.substring(end);
			command.set(0, cmd);
		}
		ProcessBuilder pb = new ProcessBuilder(command);
		if (workingDir != null)
			pb.directory(workingDir);
		commands.add(pb);
		return commands.size() - 1;
	}

	/**
	 * Sets the redirectErrorStream property of one of the commands in this
	 * pipeline. If true, the error stream for that command is redirected to its
	 * output stream
	 * 
	 * @param commandIndex
	 *            index of command to set the redirectErrorStream property, as
	 *            returned from addCommand
	 * @param redirect
	 *            new value of redirectErrorStreamProperty for the command
	 */
	public void redirectErrorStream(int commandIndex, boolean redirect) {
		commands.get(commandIndex).redirectErrorStream(redirect);
	}


	/**
	 * Executes the pipeline. If the execInBackground property is false, exec()
	 * waits for processes to complete before returning. If any input and output
	 * channels (pipeline*Channel) have not been set either in the constructor
	 * with the TCL redirection operator, or with setPipeline*Channel(), data
	 * will be read/written from System.in, System.out, System.err.
	 * 
	 * @throws TclException
	 */
	public void exec() throws TclException {
		if (commands.size() == 0)
			return;

		/*
		 * Couplers at the boundary of this Pipeline that we want to join()
		 */
		couplers = new ArrayList<PipelineCoupler>();

		PipelineCoupler.ExceptionReceiver inputException = new PipelineCoupler.ExceptionReceiver();
		PipelineCoupler.ExceptionReceiver outputException = new PipelineCoupler.ExceptionReceiver();
		PipelineCoupler.ExceptionReceiver errorException = new PipelineCoupler.ExceptionReceiver();

		/*
		 * Execute each command in the pipeline and create the PipelineCoupler
		 * threads that copy data from one thread to the next in the pipeline
		 */
		for (int i = 0; i < commands.size(); i++) {

			ProcessBuilder pb = commands.get(i);

			/* Start the command */
			Process process = null;
			try {
				process = pb.start();
			} catch (IOException e) {
				throw new TclPosixException(interp, e, true, "couldn't execute \""+pb.command().get(0)+"\"");
			}
			processes.add(process);

			/* Tie together streams with PipelineCouplers */
			if (i == 0) {
				/*
				 * Provide a coupler from the pipelineInputChannel to the first
				 * process
				 */
				PipelineCoupler c;
				if (pipelineInputChannel == null) {
					// provide standard input directly
					managedSystemInStream = new ManagedSystemInStream();
					c = new PipelineCoupler(managedSystemInStream, process.getOutputStream());
				} else {
					c = new PipelineCoupler(interp, this.pipelineInputChannel, process.getOutputStream());
				}
				c.setExceptionReceiver(inputException);
				c.setName("stdin PipelineCoupler for " + this);
				stdinCoupler = c;
				c.start();
				couplers.add(c);

			} else {
				/*
				 * Provide a coupler between the previous process and this
				 * process
				 */
				PipelineCoupler c = new PipelineCoupler(processes.get(i - 1).getInputStream(), process
						.getOutputStream());

				couplers.add(c);
				c.setName("" + (i - 1) + "->" + i + " PipelineCoupler for " + this);

				c.start();
			}

			/* Couple all the error output to one error stream */
			if (!pb.redirectErrorStream()) {
				PipelineCoupler c;
	
				if (this.pipelineErrorChannel == null) {
					// write to System.err
					c = new PipelineCoupler(process.getErrorStream(), System.err);
				} else {
					c = new PipelineCoupler(interp, process.getErrorStream(), this.pipelineErrorChannel);
					c.setWriteMutex(pipelineErrorChannel);
				}

				c.setExceptionReceiver(errorException);
				c.start();
				c.setName("" + i + " stderr PipelineCoupler for " + this);

				couplers.add(c);
			}

			/* Couple last command to standard output */
			if (i == commands.size() - 1) {
				PipelineCoupler c;
				if (this.pipelineOutputChannel == null) {
					c = new PipelineCoupler(process.getInputStream(), System.out);
				} else {
					c = new PipelineCoupler(interp, process.getInputStream(), this.pipelineOutputChannel);
					c.setWriteMutex(pipelineOutputChannel);
				}
				c.setExceptionReceiver(outputException);

				c.start();
				c.setName("stdout PipelineCoupler for " + this);

				couplers.add(c);
			}
		}

		/*
		 * If this Pipeline is in the background, just return.
		 */
		if (this.execInBackground) {
			/* Run waitForExitAndCleanup() in a separate thread */
			Thread t = new Thread(this);
			t.start();
			return;
		}

		/* Otherwise, we're not running in the background */
		waitForExitAndCleanup();

		/*
		 * If we got any exceptions from the threads during reading or writing
		 * at ends of Pipeline, throw them now
		 */
		if (outputException.getException() != null) {
			throw outputException.getAsTclException(interp);
		}
		if (errorException.getException() != null) {
			throw errorException.getAsTclException(interp);
		}
		if (inputException.getException() != null) {
			throw inputException.getAsTclException(interp);
		}
	}

	/**
	 * Wait for processes in pipeline to die, then close couplers and any open
	 * channels
	 */
	private void waitForExitAndCleanup() {
		/*
		 * Wait for processes to finish
		 */
		for (int i = 0; i < processes.size(); i++) {
			try {
				processes.get(i).waitFor();
			} catch (InterruptedException e) {
				processes.get(i).destroy();
			}

		}

		if (stdinCoupler != null) {
			/* Stop the stdin coupler, so it doesn't continue to suck up std in */
			stdinCoupler.requestStop();
		}
		/*
		 * Wait for output coupler threads to finish before caller tries to
		 * collect any data from them
		 */
		ListIterator<PipelineCoupler> iterator = couplers.listIterator();
		while (iterator.hasNext()) {
			try {
				PipelineCoupler c = iterator.next();
				c.join();
			} catch (InterruptedException e) {
				// do nothing
			}
		}

		/* Close any channels that need closing */
		ListIterator<Channel> chiterator = channelsToClose.listIterator();
		while (chiterator.hasNext()) {
			Channel ch = chiterator.next();
			TclIO.unregisterChannel(interp, ch);
		}

	}

	/**
	 * @return the pipelineInputChannel
	 */
	public Channel getPipelineInputChannel() {
		return pipelineInputChannel;
	}

	/**
	 * @param pipelineInputChannel
	 *            the pipelineInputChannel to set, which the pipeline receives
	 *            data from
	 */
	public void setPipelineInputChannel(Channel pipelineInputChannel, boolean close) {
		this.pipelineInputChannel = pipelineInputChannel;
		if (close)
			this.channelsToClose.add(pipelineInputChannel);
	}

	/**
	 * @param pipelineInputChannel
	 *            the pipelineInputChannel to set, which the pipeline receives
	 *            data from
	 */
	public void setPipelineInputChannel(Channel pipelineInputChannel) {
		this.pipelineInputChannel = pipelineInputChannel;
	}

	/**
	 * @return the pipelineOutputChannel
	 */
	public Channel getPipelineOutputChannel() {
		return pipelineOutputChannel;
	}

	/**
	 * @param pipelineOutputChannel
	 *            the pipelineOutputChannel to set, which the pipeline writes
	 *            standard output to
	 */
	public void setPipelineOutputChannel(Channel pipelineOutputChannel, boolean close) {
		this.pipelineOutputChannel = pipelineOutputChannel;
		if (close)
			this.channelsToClose.add(pipelineOutputChannel);
	}

	/**
	 * @param pipelineOutputChannel
	 *            the pipelineOutputChannel to set, which the pipeline writes
	 *            standard output to
	 */
	public void setPipelineOutputChannel(Channel pipelineOutputChannel) {
		this.pipelineOutputChannel = pipelineOutputChannel;
	}

	/**
	 * @return the pipelineErrorChannel
	 */
	public Channel getPipelineErrorChannel() {
		return pipelineErrorChannel;
	}

	/**
	 * @param pipelineErrorChannel
	 *            the pipelineErrorChannel to set, which the pipeline writes
	 *            stderr to
	 */
	public void setPipelineErrorChannel(Channel pipelineErrorChannel, boolean close) {
		this.pipelineErrorChannel = pipelineErrorChannel;
		if (close)
			channelsToClose.add(pipelineErrorChannel);
	}

	/**
	 * @param pipelineErrorChannel
	 *            the pipelineErrorChannel to set, which the pipeline writes
	 *            stderr to
	 */
	public void setPipelineErrorChannel(Channel pipelineErrorChannel) {
		this.pipelineErrorChannel = pipelineErrorChannel;
	}

	/**
	 * @return the execInBackground property
	 */
	public boolean isExecInBackground() {
		return execInBackground;
	}

	/**
	 * @param execInBackground
	 *            Set the execInBackground property, which determines if the
	 *            Pipeline runs in the background
	 */
	public void setExecInBackground(boolean execInBackground) {
		this.execInBackground = execInBackground;
	}

	/**
	 * @return Array of pseudo process identifiers, since JVM doesn't give us
	 *         the real thing
	 */
	public int[] getProcessIdentifiers() {
		int[] pid = new int[processes.size()];
		for (int i = 0; i < processes.size(); i++) {
			pid[i] = i;
		}
		return pid;
	}

	/**
	 * @return Exit values of all processes in the pipeline; only valid if
	 *         processes have exited
	 */
	public int[] getExitValues() {
		int[] exitValues = new int[processes.size()];
		for (int i = 0; i < processes.size(); i++) {
			try {
				exitValues[i] = processes.get(i).exitValue();
			} catch (Exception e) {
				exitValues[i] = 0;
			}
		}
		return exitValues;
	}

	public void run() {
		waitForExitAndCleanup();
	}

}
