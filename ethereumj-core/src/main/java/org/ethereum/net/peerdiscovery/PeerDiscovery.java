package org.ethereum.net.peerdiscovery;

import org.ethereum.net.p2p.Peer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.ethereum.config.SystemProperties.CONFIG;

/**
 * @author Roman Mandeleil
 * Created on: 22/05/2014 09:10
 */
public class PeerDiscovery {

	private static final Logger logger = LoggerFactory.getLogger("peerdiscovery");

	private final Set<PeerInfo> peers = Collections.synchronizedSet(new HashSet<PeerInfo>());
	
	private PeerMonitorThread monitor;
	private ThreadFactory threadFactory;
	private ThreadPoolExecutor executorPool;
	private RejectedExecutionHandler rejectionHandler;

	private final AtomicBoolean started = new AtomicBoolean(false);

	public void start() {

		// RejectedExecutionHandler implementation
		rejectionHandler = new RejectionLogger();

		// Get the ThreadFactory implementation to use
		threadFactory = Executors.defaultThreadFactory();

		// creating the ThreadPoolExecutor
		executorPool = new ThreadPoolExecutor(CONFIG.peerDiscoveryWorkers(),
                CONFIG.peerDiscoveryWorkers(), 10, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(
						1000), threadFactory, rejectionHandler);

		// start the monitoring thread
		monitor = new PeerMonitorThread(executorPool, 1);
		Thread monitorThread = new Thread(monitor);
		monitorThread.start();

        // Initialize PeerData
        List<PeerInfo> peerDataList = parsePeerDiscoveryIpList(CONFIG.peerDiscoveryIPList());
        addPeers(peerDataList);

        for (PeerInfo peerData : this.peers) {
            executorPool.execute(new WorkerThread(peerData, executorPool));
        }

		started.set(true);
	}

	public void stop() {
		executorPool.shutdown();
		monitor.shutdown();
		started.set(false);
	}

	public boolean isStarted() {
		return started.get();
	}
	
    public Set<PeerInfo> getPeers() {
		return peers;
	}
		
    /**
     * Update list of known peers with new peers
     * This method checks for duplicate peer id's and addresses
     *
     * @param newPeers to be added to the list of known peers
     */
    public void addPeers(Set<Peer> newPeers) {
        synchronized (peers) {
			for (final Peer newPeer : newPeers) {
                PeerInfo peerInfo =
                        new PeerInfo(newPeer.getAddress(), newPeer.getPort(), newPeer.getPeerId());
                if (started.get() && !peers.contains(peerInfo )){
                    startWorker(peerInfo);
                }
                peers.add(peerInfo);
            }
        }
    }

    public void addPeers(Collection<PeerInfo> newPeers) {
        synchronized (peers) {
                peers.addAll(newPeers);
            }
    }


	private void startWorker(PeerInfo peer) {
		logger.debug("Add new peer for discovery: {}", peer);
		executorPool.execute(new WorkerThread(peer, executorPool));
	}

    public List<PeerInfo> parsePeerDiscoveryIpList(final String peerDiscoveryIpList){

        final List<String> ipList = Arrays.asList( peerDiscoveryIpList.split(",") );
        final List<PeerInfo> peers = new ArrayList<>();

        for (String ip : ipList){
            String[] addr = ip.trim().split(":");
            String ip_trim = addr[0];
            String port_trim = addr[1];

            try {
                InetAddress iAddr = InetAddress.getByName(ip_trim);
                int port = Integer.parseInt(port_trim);

                PeerInfo peerData = new PeerInfo(iAddr, port, "");
                peers.add(peerData);
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
        }

        return peers;
    }


}