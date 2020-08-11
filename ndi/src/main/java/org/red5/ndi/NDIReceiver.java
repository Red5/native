package org.red5.ndi;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NDIReceiver {

    private static Logger log = LoggerFactory.getLogger(NDIReceiver.class);

    private static boolean isTrace = log.isTraceEnabled();

    // storage of incoming packets
    private final ConcurrentLinkedDeque<NDIPacket> packets = new ConcurrentLinkedDeque<>();

    public NDIReceiver() {
    }

    /**
     * Receive handler for incoming byte arrays.
     * 
     * @param data
     */
    public void receive(byte[] data) {
        if (isTrace) {
            //log.trace("receive {}", Arrays.toString(data));
            log.trace("receive {}", new String(data));
        }
        // construct a packet and store it in the packet deque; a client id of 0 is defaulted to for client instances
        packets.offer(NDIPacket.build(System.currentTimeMillis(), data));
    }

    /**
     * Receive handler for incoming short arrays.
     * 
     * @param data
     */
    public void receive(short[] data) {
        if (isTrace) {
            log.trace("receive {}", Arrays.toString(data));
        }
        // construct a packet and store it in the packet deque
        packets.offer(NDIPacket.build(System.currentTimeMillis(), data));
    }

    /**
     * Returns the next packet in the deque.
     * 
     * @return next packet if it exists or null if deque is empty
     */
    public NDIPacket getNext() {
        return packets != null ? packets.poll() : null;
    }

    /**
     * Drain the packets to a list; this is destructive and will clear the existing packet list.
     * 
     * @return list of current packets
     */
    public LinkedList<NDIPacket> drain() {
        // create a list with all the current packets in-order
        LinkedList<NDIPacket> list = new LinkedList<>(Arrays.asList(packets.toArray(new NDIPacket[0])));
        // clear the list
        packets.clear();
        // return the packets
        return list;
    }

}