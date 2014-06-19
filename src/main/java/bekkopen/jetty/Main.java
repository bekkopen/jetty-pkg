package bekkopen.jetty;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.ProtectionDomain;

import org.apache.commons.io.FileUtils;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.webapp.WebAppContext;

public class Main {

    private final int port;
    private final String contextPath;
    private final String workPath;
    private final String secret;

    public static void main(String[] args) throws Exception {
        Main sc = new Main();

        if (args.length != 1)               sc.start();
        else if ("status".equals(args[0]))  sc.status();
        else if ("offline".equals(args[0])) sc.offline();
        else if ("online".equals(args[0]))  sc.online();
        else if ("stop".equals(args[0]))    sc.stop();
        else if ("start".equals(args[0]))   sc.start();
        else                                sc.usage();
    }

    public Main() {
        try {
            String configFile = System.getProperty("config", "jetty.properties");
            System.getProperties().load(new FileInputStream(configFile));
        } catch (Exception ignored) {}

        port = Integer.parseInt(System.getProperty("jetty.port", "8080"));
        contextPath = System.getProperty("jetty.contextPath", "/");
        workPath = System.getProperty("jetty.workDir", null);
        secret = System.getProperty("jetty.secret", "eb27fb2e61ed603363461b3b4e37e0a0");
    }

    private void start() {
    	try {

            // Setup Threadpool
            QueuedThreadPool threadPool = new QueuedThreadPool(512);

            // Setup Jetty Server instance
    		Server server = new Server(threadPool);
            server.setStopAtShutdown(true);
            server.setStopTimeout(5000);

            ServerConnector connector = new ServerConnector(server);       
            connector.setPort(port);
            connector.setIdleTimeout(30000);
            server.setConnectors(new Connector[]{connector});

            // Get the war-file
            ProtectionDomain protectionDomain = Main.class.getProtectionDomain();
            String warFile = protectionDomain.getCodeSource().getLocation().toExternalForm();
            String currentDir = new File(protectionDomain.getCodeSource().getLocation().getPath()).getParent();

            // Handle signout/signin in BigIP-cluster

            // Add the warFile (this jar)
            WebAppContext context = new WebAppContext(warFile, contextPath);
            context.setServer(server);
            resetTempDirectory(context, currentDir);

            // Add the handlers
            HandlerList handlers = new HandlerList();
            handlers.addHandler(context);
            handlers.addHandler(new ShutdownHandler(server, context, secret));
            handlers.addHandler(new BigIPNodeHandler(secret));
            server.setHandler(handlers);

            server.start();
            server.join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stop() {
        System.out.println(ShutdownHandler.shutdown(port, secret));
    }

    private void status() {
        System.out.println(BigIPNodeHandler.check(port));
    }

    private void online() {
        System.out.println(BigIPNodeHandler.online(port, secret));
    }

    private void offline() {
        System.out.println(BigIPNodeHandler.offline(port, secret));
    }

    private void usage() {
        System.out.println("Usage: java -jar <file.jar> [start|stop|status|enable|disable]\n\t" +
                "start    Start the server (default)\n\t" +
                "stop     Stop the server gracefully\n\t" +
                "status   Check the current server status\n\t" +
                "online   Sign in to BigIP load balancer\n\t" +
                "offline  Sign out from BigIP load balancer\n"
        );
        System.exit(-1);
    }

    private void resetTempDirectory(WebAppContext context, String currentDir) throws IOException {
        File workDir;
        if (workPath != null) {
            workDir = new File(workPath);
        } else {
            workDir = new File(currentDir, "work");
        }
        FileUtils.deleteDirectory(workDir);
        context.setTempDirectory(workDir);
    }


}
