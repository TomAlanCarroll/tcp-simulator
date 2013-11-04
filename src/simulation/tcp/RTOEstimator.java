/*
 * Created on Oct 27, 2012
 *
 * Rutgers University, Department of Electrical and Computer Engineering
 * <P> Copyright (c) 2005-2013 Rutgers University
 */
package simulation.tcp;

import simulation.Simulator;

/**
 * This class performs ongoing estimation of the TCP retransmission
 * timeout time. A retransmission timer is used when
 * expecting an acknowledgment from the receiving end.
 * This implementation is based on 
 * <a href="http://tools.ietf.org/html/rfc6298" target="page">RFC 6298</a> and
 * TCP/IP Illustrated, Volume 1: The Protocols, by W. Richard Stevens
 * (<a href="http://www.pcvr.nl/tcpip/tcp_time.htm" target="page">Chapter 21</a>).</p>
 * 
 * <p>Note that the time units are the <em>simulator clock
 * ticks</em>, instead of actual time units, such as seconds.</p>
 * 
 * <p>The sending time of a segment is recorded as {@link Segment#timestamp}
 * in the TCP header (similar to the timestamp option in
 * the <em>Options</em> field of an actual TCP header)
 * and returned by the corresponding acknowledgment packet.
 * {@link Segment#timestamp} is set to <code>-1</code> if the segment is 
 * a retransmitted segment, and no RTT estimation is performed 
 * for retransmitted segments.
 * <pre>
 *	SampleRTT = current_time - timestamp;
 *	EstimatedRTT[new] = (1 - alpha)*EstimatedRTT[old] + alpha*SampleRTT;
 *	Delta = |SampleRTT - EstimatedRTT[old]|;
 *	DeviationRTT[new] = (1 - beta)*DeviationRTT[old] + beta*Delta;
 *	</pre>
 * The above should be computed using alpha=1/8 and beta=1/4.<BR>
 * The exception is that when the first RTT measurement is made,
 * the host <em>must</em> set:
 * <pre>
 *	SampleRTT = current_time - timestamp;
 *	EstimatedRTT[new] = SampleRTT;
 *	DeviationRTT[new] = SampleRTT/2;
 *	</pre>
 * The retransmission timer base is always computed as:
 * <pre>
 *	TimeoutInterval[new] = EstimatedRTT[new] + max{ G, K*DeviationRTT[new] }.
 * </pre>
 * where <em>G</em> is the system clock granularity (in
 * seconds), and <em>K</em> is usually set to <code>4</code>.<BR>
 * (Check <a href="http://tools.ietf.org/html/rfc6298" target="page">RFC 6298</a> for
 * discussion about the need for the clock granularity parameter <em>G</em>.)
 * 
 * @author Ivan Marsic
 */
public class RTOEstimator {
	/** Binary exponent of "alpha" weight for updating of
	 *  the estimated RTT {@link #estimatedRTT}.
	 *  That is, 2^{@value} = 8 = 1/alpha. */
	static final int alphaShift = 3;

	/** Binary exponent of "beta" weight for updating of
	 *  the RTT deviation {@link #devRTT}.
	 *  That is, 2^{@value} = 4 = 1/beta. */ 
	static final int betaShift = 2;

	/** Binary exponent of deviation multiple used for
	 * computing {@link #timeoutInterval}.
	 * This is the <code>K</code> multiplier, usually set as
	 * <code>K = 4 = 2^{@value}</code>. */
	static final int stdDevMultShift = 2;

    /** Maximum value of a RTO timeout (in seconds).
     * <a href="http://tools.ietf.org/html/rfc6298" target="page">RFC 6298</a>
     * states that a maximum value may be placed on RTO provided
     *  it is at least 60 seconds. We set the maximum value
     *  in the <em>simulator clock ticks</em> */
	protected double maxTimeoutInterval = 100000.0;

	/** Initial value of estimated RTT (in simulator clock ticks)
	 *  (multiplied by "alpha" {@link #alphaShift}) */
	protected int estimatedRTT_init = 0;

	/** Initial value of RTT deviation (in simulator clock ticks)
	 *  (multiplied by "beta" {@link #betaShift}) */
	protected int deviationRTT_init = 12;

	/** Initial value of base RTO timer (in simulator clock ticks). */
	protected double timeoutInterval_init = 6.0;

	/** Simulator clock tick duration (in seconds) for all the RTT variables.
	 * Real TCP implementations use two timer granularities:<BR>
	 * (i) the <em>fast timer</em>, called every 200ms, and<BR>
	 * (ii) the <em>slow timer</em>, called every 500ms.<BR>
	 * All TCP timers are expressed in terms of the number of ticks of these two timers.
	 * The timer counts for the RTO timer, {@link Receiver#delayedACKtimer}, etc.,
	 * are decremented by 1 every timer the fast or slow timer expires.
	 * Only {@link Receiver#delayedACKtimer} is expressed in terms of the fast
	 * timer ticks, and all other TCP timers are expressed in terms of the
	 * slow timer ticks. Unfortunately, because of coarse granularity of our time simulation,
	 * currently we do <em>not</em> implement these timers.
	 * A good discussion of TCP timers is available in
	 * <a href="http://www.cl.cam.ac.uk/research/dtg/lce-pub/public/kjm25/CUED_F-INFENG_TR487.pdf" target="page">Tweaking TCP's Timers<a>.
	 */
	protected double tickDuration = 1.0;

	/** Current estimated RTT value (in simulator clock ticks)<BR>
	 * (shifted by {@link #alphaShift}) */
	transient protected int estimatedRTT = 0;

	/** Current estimated RTT deviation (shifted by {@link #betaShift}) */
	transient protected int devRTT = 0;

	/** Current RTO timer value (in simulator clock ticks). */
	transient protected double timeoutInterval = maxTimeoutInterval;

    /** Current RTO timer backoff value (unitless number). */
	transient protected int backoff = 1;

	/** Default constructor calls the other constructor
	 * with the initial values of the input parameters:
	 * {@link #estimatedRTT_init}, {@link #deviationRTT_init},
	 * {@link #timeoutInterval_init}, and {@link #maxTimeoutInterval},<BR>
	 * all given in real time units (seconds) for user convenience.
	 * 
	 * @param tick_ time unit for RTT variables.
	 */
	public RTOEstimator(double tick_) {
		this(tick_, 0, 12, 6.0, 100000.0);
	}

	/** The constructor initializes the variables for
	 *  the retransmit timer.<BR>
	 *  <b>Note:</b> The input parameters are given in
	 *  real time units (seconds) for user convenience,
	 *  and are converted in the constructor to
	 *  the simulator clock ticks.
	 *  
	 * @param tick_ time unit for RTT variables.
	 * @param estimRTT_init_ initial value of estimated RTT.
	 * @param deviatRTT_init_ initial value of RTT deviation.
	 * @param baseRTT_init_ initial value of TimeoutInterval.
	 * @param maxRTO_ the maximum value of retransmission timeout.
	 */
	public RTOEstimator(
		double tick_, double estimRTT_init_, double deviatRTT_init_,
		double baseRTT_init_, double maxRTO_
	) {
		if (tick_ < 0.0)
			tick_ = tickDuration;

		if (estimRTT_init_ >= 0.0)
			estimatedRTT = (int)(estimRTT_init_ / tick_);
		else
			estimatedRTT =
				(int)(estimatedRTT_init * tickDuration/tick_);

		if (deviatRTT_init_ >= 0.0)
			devRTT = (int)(deviatRTT_init_ / tick_);
		else
			devRTT =
				(int)(deviationRTT_init * tickDuration/tick_);

		if (baseRTT_init_ >= 0.0)
			timeoutInterval = baseRTT_init_;

		if (maxRTO_ > 0.0)
			maxTimeoutInterval = maxRTO_;

		backoff = 1;

		tickDuration = tick_;
	}

	/**
	 * Updates RTT estimations and recalculates Retransmission timer (RTO) base.
 	 * RTO base is calculated after each new ACK is received, using the
	 * <a href="http://en.wikipedia.org/wiki/Karn%27s_Algorithm" target="page">Karn algorithm</a>.
	 * The estimation method is same as in
	 * <a href="http://tools.ietf.org/html/rfc6298" target="page">RFC 6298</a>.
	 * The sending time of a packet is reflected back by acknowledgment
	 * packet as <code>timestamp_</code> in header (carried as part of
	 * the Options field in a real TCP/IP header).
	 * The <code>timestamp_</code> equal <code>-1</code> signifies a
	 * retransmitted segment, so no RTT estimation updating is done for
	 * such a segment.
	 * The algorithm is:
	 * <pre>
	 * EstimatedRTT[new] = 7/8&times;EstimatedRTT[old] + 1/8&times;SampleRTT;
	 * Delta = |SampleRTT - EstimatedRTT[old]|;
	 * DeviationRTT[new] = 3/4&times;DeviationRTT[old] + 1/4&times;Delta;
	 * </pre>
	 * When the first RTT measurement is made, the host <em>must</em> set:
	 * <pre>
	 * EstimatedRTT[new] = SampleRTT;
	 * DeviationRTT[new] = SampleRTT/2;
	 * </pre>
	 * The retransmission timer base is always:
	 * <pre>
	 * TimeoutInterval[new] = EstimatedRTT[new] + 4&times;DeviationRTT[new].
	 * </pre>
	 * 
	 * @param currentTime_ &nbsp;the current reference time.
	 * @param timestamp_ &nbsp;the time when this acknowledged segment was originally sent. 
	 *
	 */
	protected void updateRTT(double currentTime_, double timestamp_) {
		// Check if this is a retransmitted segment.
		// For such segments, the timestamp is set to "-1"
		// and no RTT estimation is performed.
		if (timestamp_ < 0) return;

		backoff = 1;	// reset the backoff for a new ACK

		// Most recent measured sample RTT value:
		int sampleRTT = (int)((currentTime_ - timestamp_) / tickDuration + 0.5);
		// Round the RTT to integer times of the time increment
		if (sampleRTT < 1) sampleRTT = 1;

	    if (estimatedRTT != 0) {	// If NOT the first RTT estimation ...
			int err = sampleRTT - estimatedRTT; // difference of measured and estimated

			// EstimatedRTT[new] = (1 - alpha)*EstimatedRTT[old] + alpha*SampleRTT
			// where alpha = 1/8 = 1 / (2^alphaShift) = 1/(2^3)
			estimatedRTT += (err >> alphaShift);

	    	// Delta = |SampleRTT - EstimatedRTT[old]|
			if (err < 0)	// absolute value of the difference
				err = -err;
			// DeviationRTT[new] = 3/4*DeviationRTT[old] + 1/4*Delta
			int delta = err - devRTT;
			devRTT += (delta >> betaShift);
		} else { 
			/*
			 * For the first RTT estimation, set EstimatedRTT as RTT and
			 * DevRTT as half of RTT as required by RFC-6298
			 */
			estimatedRTT = sampleRTT;	// EstimatedRTT = RTT
			devRTT = sampleRTT >> 1;	// DevRTT = RTT / 2
		}

		/*
		 * Current RTO timer value is:
		 * (unscaled) round-trip time estimate
		 * plus 2^stdDevMultShift times (unscaled) devRTT, i.e.:
		 * = EstimatedRTT[new] + max{tick, 4*DeviationRTT[new]} 
		 */
		timeoutInterval = estimatedRTT +
			Math.max(tickDuration, (devRTT << stdDevMultShift));
		// RFC-6298 says that, whenever RTO is computed, if it is
		// less than 1 second, then the RTO should be rounded up to 1 second.
		//
		if (timeoutInterval < 1.0) timeoutInterval = 1.0;
		timeoutInterval *= tickDuration;

		if (
			(Simulator.currentReportingLevel & Simulator.REPORTING_RTO_ESTIMATE) != 0
		) {
			System.out.println(
				"RTT UPDATE:  (sampleRTT=" + sampleRTT + ", estimatedRTT=" +
				 estimatedRTT + ", devRTT=" + devRTT + ", timeoutInterval=" +
				timeoutInterval + ", backoff=" + backoff +")"
			);
		}
	}

	/**
	 * Backs off the RXT timer backoff, as specified in [RFC-6298].
	 * The sender must set RTO &larr; RTO &times; 2 ("back off the timer").
	 * As discussed in RFC-6298, the maximum value may be used
     * to provide an upper bound to this doubling operation.
	 */
	protected void timerBackoff() {
		if (timeoutInterval < maxTimeoutInterval) {
			 backoff <<= 1;	// double the backoff
			if (
				(Simulator.currentReportingLevel & Simulator.REPORTING_RTO_ESTIMATE) != 0
			) {
				System.out.println("TIMEOUT: timer backoff to " + backoff + " times");
			}
		}
	}

	/**
	 * Returns the RTO timeout value (in simulator clock ticks)<BR>
	 * by multiplying the base value and the current backoff value.
	 */
	protected double getTimeoutInterval() {
		double rto_ = timeoutInterval * backoff;
		if (rto_ < tickDuration)
			return tickDuration;
		else if (rto_ > maxTimeoutInterval)
			return maxTimeoutInterval;
		else
			return rto_;
	}
}