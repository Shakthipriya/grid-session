package grith.gridsession;

import grisu.jcommons.configuration.CommonGridProperties;
import grisu.jcommons.dependencies.BouncyCastleTool;
import grisu.jcommons.utils.EnvironmentVariableHelpers;
import grisu.jcommons.utils.JythonHelpers;

import java.io.File;
import java.io.InputStream;
import java.net.BindException;
import java.net.ServerSocket;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;

import com.sun.akuma.Daemon;
import com.sun.akuma.JavaVMArguments;

public class GridClient {

	public static void execute(GridClient client, boolean useSSL) {

		if (CommonGridProperties.getDefault().useGridSession()) {

			// check whether we run on windows, if that is the case, we can't
			// daemonize...
			String currentOs = System.getProperty("os.name").toUpperCase();

			if (currentOs.contains("WINDOWS")) {
				myLogger.debug("Starting grid-session client without even trying to spawn grid-session daemon (bloody windows)...");
				startClient(client, useSSL);
				return;
			}

			try {

				File file = new File("/tmp/jna");
				file.mkdirs();

				file.setWritable(true, false);

			} catch (Exception e) {
				myLogger.error("Can't create dir or change permissions for /tmp/jna: "
						+ e.getLocalizedMessage());
			}

			EnvironmentVariableHelpers.loadEnvironmentVariablesToSystemProperties();
			Thread.currentThread().setName("main");
			JythonHelpers.setJythonCachedir();

			Daemon d = new Daemon();

			try {

				// check whether server already running
				try {
					ServerSocket s = new ServerSocket(RpcPort.getUserPort());
					s.close();
				} catch (BindException be) {
					// that's good, means something already running, we don't
					// need to try to daemonize...
					myLogger.debug("Starting grid-session client");
					startClient(client, useSSL);
					return;
				}

				if (d.isDaemonized()) {
					d.init();

				} else {

					String memMin = "-Xms24m";
					String memMax = "-Xmx24m";

					JavaVMArguments args = JavaVMArguments.current();
					int indexMax = -1;
					int indexMin = -1;
					for (int i = 0; i < args.size(); i++) {
						String arg = args.get(i);
						if (arg.startsWith("-Xmx")) {
							indexMax = i;
						}
						if (arg.startsWith("-Xms")) {
							indexMin = i;
						}
					}

					if (indexMin >= 0) {
						args.set(indexMin, memMin);
					} else {
						args.add(memMin);
					}
					if (indexMax >= 0) {
						args.set(indexMax, memMax);
					} else {
						args.add(memMax);
					}
					d.daemonize(args);

					startClient(client, useSSL);

					return;
				}
			} catch (Exception e) {
				e.printStackTrace();
				myLogger.error(
						"Error when trying to daemonize grid-session service.", e);
				System.exit(1);
			}
			try {
				LoggerContext context = (LoggerContext) LoggerFactory
						.getILoggerFactory();
				JoranConfigurator configurator = new JoranConfigurator();
				configurator.setContext(context);

				InputStream config = SessionClient.class
						.getResourceAsStream("/logback_server.xml");
				configurator.doConfigure(config);
			} catch (Exception e) {
				myLogger.error("Error when trying to configure grid-session service logging");

			}

			myLogger.debug("Starting grid-session service...");

			try {
				BouncyCastleTool.initBouncyCastle();
				TinySessionServer server = new TinySessionServer();
			} catch (Exception e) {
				myLogger.error(
						"Error starting grid-session service: "
								+ e.getLocalizedMessage(), e);
				System.exit(1);
			}
		} else {
			client.run();
		}

	}

	public static void main(String[] args) {

		GridClient client = new GridClient();

		execute(client, true);

	}

	private static void startClient(GridClient client, boolean useSSL) {

		// check whether we can actually connect to grid-session service...
		String ping = null;
		try {
			SessionClient sc = SessionClient.getDefault(useSSL);
			if (sc != null) {
				ping = sc.getSessionManagement().ping();
			}
		} catch (Exception e) {
			myLogger.error("Error when trying to ping grid session.");
		}

		if (StringUtils.isBlank(ping)) {
			myLogger.error("Session client can't be reached, disabling use of grid session.");
			System.setProperty(
					CommonGridProperties.Property.USE_GRID_SESSION
					.toString(), "false");

		}

		myLogger.debug("Starting grid-session client");
		client.run();
		return;
	}

	private boolean useSSL = false;

	private boolean useLocalTransport = false;

	public static final Logger myLogger = LoggerFactory
			.getLogger(GridClient.class);

	private ISessionManagement session;

	public GridClient() {
	}

	// private void execute() {
	// BouncyCastleTool.initBouncyCastle();
	// run();
	// }

	public ISessionManagement getSession() {

		if (session == null) {
			if (!isUseLocalTransport()) {
				SessionClient client = SessionClient.getDefault(isUseSSL());
				this.session = client.getSessionManagement();
			} else {
				this.session = new SessionManagement();
				SessionManagement.kickOffIdpPreloading();
			}
		}
		return session;

	}

	public boolean isUseLocalTransport() {
		return useLocalTransport;
	}

	public boolean isUseSSL() {
		return useSSL;
	}

	/**
	 * Overwrite this method with the code that starts your own client.
	 *
	 * @param args
	 *            the arguments for your client
	 */
	public void run() {

		System.out.println("Dummy grid client");
		System.out.println("Grid-Session status:");
		System.out.println(getSession().status());
	}

	public void setUseLocalTransport(boolean useLocalTransport) {
		this.useLocalTransport = useLocalTransport;
	}

	public void setUseSSL(boolean useSSL) {
		this.useSSL = useSSL;
	}

}
