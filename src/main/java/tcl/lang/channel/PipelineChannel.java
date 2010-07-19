package tcl.lang.channel;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import tcl.lang.Interp;
import tcl.lang.Pipeline;
import tcl.lang.TclException;
import tcl.lang.TclIO;
import tcl.lang.TclList;
import tcl.lang.TclObject;
import tcl.lang.TclString;
import tcl.lang.process.Redirect;

/**
 * This class provides a Channel view of a Pipeline object
 * 
 */
public class PipelineChannel extends Channel {
	private PipedInputStream pipedInputStream;
	private PipedOutputStream pipedOutputStream;
	private Pipeline pipeline;
	private TclByteArrayChannel stderr;
	Interp interp = null;

	/**
	 * Open a new pipeline channel
	 * 
	 * @param interp
	 * @param execString
	 *            String in the form of "exec" or "open"; first '|' is optional
	 * @param modeFlags
	 *            TclIO.RDONLY or TclIO.WRONLY
	 * @return channel name
	 * @throws IOException
	 * @throws TclException
	 */
	public String open(Interp interp, String execString, int modeFlags) throws IOException, TclException {

		this.interp = interp;

		if (modeFlags != TclIO.RDONLY && modeFlags != TclIO.WRONLY) {
			throw new TclException(interp, "Pipeline must be opened read-only or write-only");
		}
		if (execString.startsWith("|")) {
			execString = execString.substring(1);
		}
		TclObject[] objv = TclList.getElements(interp, TclString.newInstance(execString));

		if (objv.length == 0) {
			throw new TclException(interp, "illegal use of | or |& in command");
		}
		pipeline = new Pipeline(interp, objv, 0);

		/* There's two ends to the pipe: read and write */
		pipedInputStream = new PipedInputStream();
		pipedOutputStream = new PipedOutputStream();
		pipedInputStream.connect(pipedOutputStream);
		stderr = new TclByteArrayChannel(interp);

		if ((modeFlags & TclIO.RDONLY) == TclIO.RDONLY) {
			this.mode = TclIO.RDONLY;
			/* Read from pipelines output */
			if (pipeline.getStdoutRedirect() == null) {
				pipeline.setStdoutRedirect(new Redirect(pipedOutputStream));
			} else {
				throw new TclException(interp, "can't read output from command: standard output was redirected");
			}
			if (pipeline.getStdinRedirect() == null) {
				pipeline.setStdinRedirect(Redirect.inherit());
			}
		} else if ((modeFlags & TclIO.WRONLY) == TclIO.WRONLY) {
			this.mode = TclIO.WRONLY;
			/* Write to pipeline's input */
			if (pipeline.getStdinRedirect() == null) {
				pipeline.setStdinRedirect(new Redirect(pipedInputStream));
				this.setBuffering(TclIO.BUFF_NONE);
			} else {
				throw new TclException(interp, "can't write input to command: standard input was redirected");
			}
			if (pipeline.getStdoutRedirect() == null) {
				pipeline.setStdoutRedirect(Redirect.inherit());
			}
		}
		if (pipeline.getStderrRedirect() == null) {
			pipeline.setStderrRedirect(new Redirect(stderr, true));
		}
		pipeline.setExecInBackground(true);
		pipeline.exec();

		setChanName(TclIO.getNextDescriptor(interp, getChanType()));
		return getChanName();
	}

	/**
	 * @return The standard error output from the command, if any
	 */
	public String getStderrOutput() {
		if (stderr != null) {
			return stderr.getTclByteArray().toString();
		}
		return "";
	}

	@Override
	String getChanType() {
		return "pipeline";
	}

	/**
	 * 
	 * @return the Pipeline object for this Channel
	 */
	public Pipeline getPipeline() {
		return pipeline;
	}

	/**
	 * Get the pipedInputStream. Made public so Pipeline can read directly
	 * without using the Channel interface
	 * 
	 * @see tcl.lang.channel.Channel#getInputStream()
	 */
	@Override
	public InputStream getInputStream() throws IOException {
		return pipedInputStream;
	}

	/**
	 * Get the pipedOutputStream. Made public so Pipeline can write directly
	 * without using the Channel interface
	 * 
	 * @see tcl.lang.channel.Channel#getOutputStream()
	 */
	@Override
	public OutputStream getOutputStream() throws IOException {
		return pipedOutputStream;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tcl.lang.channel.Channel#close()
	 */
	@Override
	public void close() throws IOException {
		IOException ex = null;

		if (pipedInputStream != null) {
			try {
				pipedInputStream.close();
			} catch (IOException e) {
				ex = e;
			}
			pipedInputStream = null;
		}

		if (pipedOutputStream != null) {
			try {
				pipedOutputStream.close();
			} catch (IOException e) {
				ex = e;
			}
			pipedOutputStream = null;
		}


		if (this.blocking)
			try {
				// force death of process if we are reading from the process, obviously we don't
				// need anything more
				pipeline.waitForExitAndCleanup(this.mode == TclIO.RDONLY);
			} catch (TclException e) {
				throw new IOException(e.getMessage());
			}

		if (ex != null)
			throw ex;
	}

}
