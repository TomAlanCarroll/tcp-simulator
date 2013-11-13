/*
 * Created on Feb 21, 2013
 *
 * Rutgers University, Department of Electrical and Computer Engineering
 * <P> Copyright (c) 2005-2013 Rutgers University
 */
package simulation.tcp;

import simulation.network.Endpoint;

/**
 * The <b>NewReno</b> version of the TCP sender was described in
 * <a href="http://www.apps.ietf.org/rfc/rfc2581.html" target="page">RFC 2581</a>
 * and clarified in <a href="http://tools.ietf.org/html/rfc3782" target="page">RFC 3782</a>.<BR>
 * This is a highly simplified version of NewReno sender,
 * which is currently specified in
 * <a href="http://tools.ietf.org/html/rfc5681" target="page">RFC 5681</a>.</p>
 * 
 * <p>This class does not implement any code. It just serves
 * to help the {@link SenderStateFastRecovery} to know in
 * which context it is running (old Reno or NewReno) and to
 * behave accordingly.
 * 
 * @see SenderReno
 * @see SenderStateFastRecovery
 * 
 * @author Ivan Marsic
 */
public class SenderNewReno extends SenderReno {

	/**
	 * Constructor.
	 * 
	 * @param localTCPendpoint_ The local TCP endpoint
	 * object that contains this module. 
	 */
	public SenderNewReno(Endpoint localTCPendpoint_) {
		super(localTCPendpoint_);
	}
}