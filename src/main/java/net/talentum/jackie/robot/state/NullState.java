package net.talentum.jackie.robot.state;

/**
 * An example state that does nothing
 * 
 * @author JJurM
 */
public class NullState implements State {

	@Override
	public State run() {
		return this;
	}

	@Override
	public void interrupt() {
		// no need for interrupting
	}

	@Override
	public void begin() {

	}

	@Override
	public void end() {

	}

}
