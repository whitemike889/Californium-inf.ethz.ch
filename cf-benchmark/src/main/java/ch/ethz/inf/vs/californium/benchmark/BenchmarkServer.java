package ch.ethz.inf.vs.californium.benchmark;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import ch.ethz.inf.vs.californium.coap.Request;
import ch.ethz.inf.vs.californium.coap.Response;
import ch.ethz.inf.vs.californium.network.CoAPEndpoint;
import ch.ethz.inf.vs.californium.network.config.NetworkConfig;
import ch.ethz.inf.vs.californium.network.config.NetworkConfigDefaults;
import ch.ethz.inf.vs.californium.server.Server;
import ch.ethz.inf.vs.elements.UDPConnector;

/**
 * This server has optimal parameters for benchmarking. The optimal JVM
 * parameters on my machine were:
 * <pre>
 * -Xms4096m -Xmx4096m
 * </pre>
 * @author Martin Lanter
 */
public class BenchmarkServer {
	
	public static final int CORES = Runtime.getRuntime().availableProcessors();
	public static final String OS = System.getProperty("os.name");
	public static final boolean WINDOWS = OS.startsWith("Windows");
	
	public static final String DEFAULT_ADDRESS = null;
	public static final int DEFAULT_PORT = 5683;
	
	public static final int DEFAULT_ENDPOINT_THREAD_COUNT = CORES;
	
	public static final int DEFAULT_SENDER_COUNT = WINDOWS ? CORES : 1;
	public static final int DEFAULT_RECEIVER_COUNT = WINDOWS ? CORES : 1;

	public static void main(String[] args) throws Exception {
		System.out.println("Californium (Cf) Benchmark Server");
		System.out.println("(c) 2013, Institute for Pervasive Computing, ETH Zurich");
		System.out.println();
		System.out.println("This machine has "+CORES+" cores");
		System.out.println("Operating system: " + OS);
		
		String address = null;
		int port = DEFAULT_PORT;
		int udp_sender = DEFAULT_SENDER_COUNT;
		int udp_receiver = DEFAULT_RECEIVER_COUNT;
		int endpoint_threads = DEFAULT_ENDPOINT_THREAD_COUNT;
		boolean verbose = false;
		boolean use_workers = false;
		
		// Parse input
		if (args.length > 0) {
			int index = 0;
			while (index < args.length) {
				String arg = args[index];
				if ("-usage".equals(arg) || "-help".equals(arg) || "-h".equals(arg) || "-?".equals(arg)) {
					printUsage();
				} else if ("-t".equals(arg)) {
					endpoint_threads = Integer.parseInt(args[index+1]);
				} else if ("-s".equals(arg)) {
					udp_sender = Integer.parseInt(args[index+1]);
				} else if ("-r".equals(arg)) {
					udp_receiver = Integer.parseInt(args[index+1]);
				} else if ("-p".equals(arg)) {
					port = Integer.parseInt(args[index+1]);
				} else if ("-a".equals(arg)) {
					address = args[index+1];
				} else if ("-v".equals(arg)) {
					verbose = true;
				} else if ("-use-workers".equals(arg)) {
					use_workers = true;
				} else {
					System.err.println("Unknwon arg "+arg);
					printUsage();
				}
				index += 2;
			}
		}
		
		// Parse address
		InetAddress addr = address!=null ? InetAddress.getByName(address) : null;
		InetSocketAddress sockAddr = new InetSocketAddress((InetAddress) addr, port);
		
		
		setBenchmarkConfiguration(udp_sender, udp_receiver, verbose);
		
		// Create server
		Server server = new Server();
		if (use_workers) {
			System.out.println("Use queues with "+endpoint_threads+" workers");
			server.setExecutor(new WorkQueueExecutor(endpoint_threads));
		} else {
			System.out.println("Endpoint thread-pool size: "+endpoint_threads);
			server.setExecutor(Executors.newScheduledThreadPool(endpoint_threads));
		}
		System.out.println("Number of receiver/sender threads: "+udp_receiver+"/"+udp_sender);
			
		server.add(new BenchmarkResource("benchmark"));
		server.add(new FibonacciResource("fibonacci"));
		server.add(new ShutDownResource("shutdown"));
		
		server.addEndpoint(new CoAPEndpoint(sockAddr));
		server.start();

		System.out.println("Benchmark server listening on " + sockAddr);
	}
	
	private static void setBenchmarkConfiguration(int udp_sender, int udp_receiver, boolean verbose) {

		if (verbose) {
			Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).setLevel(Level.ALL);
			for (Handler h:Logger.getLogger("").getHandlers())
				h.setLevel(Level.ALL);
			Logger.getLogger(UDPConnector.class.toString()).setLevel(Level.ALL);
			
		} else {
			Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).setLevel(Level.SEVERE);
			Logger.getLogger("").setLevel(Level.SEVERE);
		}

		// Network configuration optimal for performance benchmarks
		NetworkConfig.createStandardWithoutFile()
			// Disable deduplication OR strongly reduce lifetime
			.setString(NetworkConfigDefaults.DEDUPLICATOR, NetworkConfigDefaults.NO_DEDUPLICATOR)
			.setInt(NetworkConfigDefaults.EXCHANGE_LIFECYCLE, 1500)
			.setInt(NetworkConfigDefaults.MARK_AND_SWEEP_INTERVAL, 2000)
			
			// Increase buffer for network interface to 10 MB
			.setInt(NetworkConfigDefaults.UDP_CONNECTOR_RECEIVE_BUFFER, 10*1024*1024)
			.setInt(NetworkConfigDefaults.UDP_CONNECTOR_SEND_BUFFER, 10*1024*1024)
		
			// Increase threads for receiving and sending packets through the socket
			.setInt(NetworkConfigDefaults.UDP_CONNECTOR_RECEIVER_THREAD_COUNT, udp_receiver)
			.setInt(NetworkConfigDefaults.UDP_CONNECTOR_SENDER_THREAD_COUNT, udp_sender)
			
			// Disable message logging
			.setBoolean(NetworkConfigDefaults.UDP_CONNECTOR_LOG_PACKETS, verbose);
	}
	
	private static void printUsage() {
		System.out.println();
		System.out.println("SYNOPSIS");
		System.out.println("	" + BenchmarkServer.class.getSimpleName() + " [-a ADDRESS] [-p PORT] [-t POOLSIZE] [-s SENDERS] [-r RECEIVERS]");
		System.out.println("OPTIONS");
		System.out.println("	-a ADDRESS");
		System.out.println("		Bind the server to a specific host IP address given by ADDRESS (default is wildcard address).");
		System.out.println("	-p PORT");
		System.out.println("		Listen on UDP port PORT (default is "+DEFAULT_PORT+").");
		System.out.println("	-t POOLSIZE");
		System.out.println("		Use POOLSIZE worker threads in the endpoint (default is the number of cores).");
		System.out.println("	-s SENDERS");
		System.out.println("		Use SENDERS threads to copy messages to the UDP socket.");
		System.out.println("		The default is number of cores on Windows and 1 otherwise.");
		System.out.println("	-r RECEIVERS");
		System.out.println("		Use RECEIVERS threads to copy messages from the UDP socket.");
		System.out.println("		The default is number of cores on Windows and 1 otherwise.");
		System.out.println("    -use-workers");
		System.out.println("        Use a specialized queue for incoming requests that reduces synchronization of threads.");
		System.out.println("OPTIMIZATIONS");
		System.out.println("	-Xms4096m -Xmx4096m");
		System.out.println("		Set the Java heap size to 4 GiB.");
		System.out.println("EXAMPLES");
		System.out.println("	java -Xms4096m -Xmx4096m " + BenchmarkServer.class.getSimpleName() + " -p 5684 -t 16");
		System.out.println("	java -Xms4096m -Xmx4096m -jar " + BenchmarkServer.class.getSimpleName() + ".jar -s 2 -r 2");
		System.exit(0);
	}
	
	/*
	 *  Sends a GET request to itself
	 */
	public static void selfTest() {
		try {
			Request request = Request.newGet();
			request.setURI("localhost:5683/benchmark");
			request.send();
			Response response = request.waitForResponse(1000);
			System.out.println("received "+response);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
