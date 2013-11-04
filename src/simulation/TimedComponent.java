/*
 * Created on Oct 27, 2012
 *
 * Rutgers University, Department of Electrical and Computer Engineering
 * <P> Copyright (c) 2005-2012 Rutgers University
 */
package simulation;

/**
 * The interface for simulated software components,
 * such as protocol modules.<BR>
 * Provides a callback method to call when a
 * simulated timer expires.
 * 
 * @author Ivan Marsic
 */
public interface TimedComponent {
	/**
	 * Callback method to call when a simulated timer expires.
	 * Multiple timers can be run by the same component.
	 * 
	 * @param timerType_ type of the timer set initially by the caller,
	 * in case multiple timers are run by the same component.
	 */
	public void timerExpired(int timerType_);
}