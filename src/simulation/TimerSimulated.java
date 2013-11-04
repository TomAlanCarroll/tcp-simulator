/*
 * Created on Oct 27, 2012
 *
 * Rutgers University, Department of Electrical and Computer Engineering
 * <P> Copyright (c) 2005-2012 Rutgers University
 */
package simulation;

/** The Timer class for the simulator.
 * The simulator components cannot use actual system timers
 * because the timers must run on simulated time.</p>
 * 
 * <p>The time units for the timer are the <em>simulator clock
 * ticks</em>, instead of actual time units, such as seconds.</p>
 * 
 * @author Ivan Marsic
 */
public class TimerSimulated implements Cloneable {
	/** The callback object that will be called when this timer expires. */
	public TimedComponent callback;
	
	/** Type of the timer, to help the component distinguish between multiple running timers. */
	public int type;

	/** The future time when this timer will expire. */
	private double time;

	/**
	 * <p><b>Note:</b> The constructor should check that <code>time_</code>
	 * is indeed in the future, but we currently don't check that...
	 * 
	 * @param callback_ callback object to call when this timer expires
	 * @param type_ timer type, in case the component is running multiple timers
	 * @param time_ future time when this timer will fire
	 */
	public TimerSimulated(TimedComponent callback_, int type_, double time_) {
		callback = callback_;
		type = type_;
		setTime(time_); //TODO: should check that the time is in the future!
	}

	/**
	 * This method is part of the java.lang.Cloneable interface.
	 */
	public Object clone() {
        try {
            return super.clone();
        } catch(CloneNotSupportedException ex) {
        	System.out.print("TimerSimulated.clone():\t" + ex.toString());
            return null;
        }
    }

	/** Returns the time when this timer expires. */
	public double getTime() {
		return time;
	}

	/**
	 * @param time the time to set
	 */
	public void setTime(double time) {
		this.time = time;
	}
}