package net.talentum.jackie.robot;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import net.talentum.jackie.comm.Commander;
import net.talentum.jackie.image.supplier.ImageSupplier;
import net.talentum.jackie.robot.state.InterruptedExecution;
import net.talentum.jackie.robot.state.LineFollowingState;
import net.talentum.jackie.robot.state.State;
import net.talentum.jackie.system.Config;
import net.talentum.jackie.system.Main;
import net.talentum.jackie.tools.TimeTools;

/**
 * One instance of this class represents one robot (there should naturally be at
 * most one instance). Robot is responsible for constructing {@link Moment}s
 * 
 * @author JJurM
 */
public class Robot {

	Thread thread;

	/**
	 * Holds object that is capable of supplying webcam images.
	 */
	ImageSupplier imageSupplier;

	/**
	 * Reference to the {@link Commander}
	 */
	public final Commander commander;

	/**
	 * Whether the robot should run
	 */
	public final AtomicBoolean run = new AtomicBoolean(true);

	/**
	 * List of listeners to call in case of config changed event
	 */
	protected List<Runnable> configChangedListeners = new ArrayList<Runnable>();

	/**
	 * Actual state, its method {@link State#getMotorInstructions()} is called
	 * in a loop.
	 */
	private State state;

	private AtomicBoolean toRefresh = new AtomicBoolean(false);

	public LineFollowingState lineFollowingState = new LineFollowingState(this);

	/**
	 * Default constructor
	 * 
	 * @param commander
	 */
	public Robot(Commander commander) {
		this.commander = commander;
	}

	/**
	 * This should be called just before the loop {@link #runCycle()}.
	 */
	public void init() {
		// create state
		// state = new HorizontalLevelObservingState(this);
		lineFollowingState = new LineFollowingState(this);
		// state = new ObstacleAvoidanceState(this);
		state = lineFollowingState;
		state.begin();
	}

	public void setImageSupplier(ImageSupplier imageSupplier) {
		this.imageSupplier = imageSupplier;
	}

	/**
	 * Returns image got from {@link ImageSupplier}.
	 * 
	 * @return
	 */
	public BufferedImage getImage() {
		return imageSupplier.getImage();
	}

	public void start() {
		thread = new Thread(this::runCycle);
		thread.start();
	}

	/**
	 * Runs {@link #runOnce()} repeatedly in a {@code while(true)} loop.
	 */
	protected void runCycle() {
		Thread.currentThread().setName("RobotThread");
		while (run.get()) {
			try {
				State next = state.run();
				Main.runs.getAndIncrement();
				if (toRefresh.getAndSet(false) || next != state) {
					// switching to next state
					toRefresh.set(false);
					System.out.println("Switching strategy to " + next.getClass().getName());
					state.end();
					next.begin();
					state = next;
				}
			} catch (InterruptedExecution e) {
				// start cycle again and check "run" variable
			}
		}
		state.end();
		System.out.println("Robot stopped");
	}

	/**
	 * Stops the main cycle (method {@link #runCycle()}).
	 */
	public void stop() {
		run.set(false);
		state.interrupt();
		try {
			thread.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Method called on the robot by a superior class when the configuration has
	 * been changed.
	 */
	public void configurationReloaded() {
		Config.reload();
		for (Runnable listener : configChangedListeners) {
			listener.run();
		}
	}

	/**
	 * Adds listener that is called when Robot has detected a change in the
	 * configuration. Values from the configuration should be reloaded.
	 * 
	 * @param listener
	 *            a runnable
	 */
	public void addConfigChangedListener(Runnable listener) {
		configChangedListeners.add(listener);
	}

	/**
	 * 
	 */
	public void refresh() {
		toRefresh.set(true);
	}

	public void begin() {
		// move camera up
		commander.writeMotor(Commander.MOTOR_CAMERA, Config.get().getInt("params/motorPositions/camera/up"));
		TimeTools.sleep(Config.get().getInt("params/motorDelay"));

		// move arm up
		commander.writeMotor(Commander.MOTOR_ARM, Config.get().getInt("params/motorPositions/arm/normal"));
		TimeTools.sleep(Config.get().getInt("params/motorDelay"));

		// turn backlight on
		commander.light(Commander.BACKLIGHT, true);
		commander.light(Commander.FLASHLIGHT, true);

		// move camera down
		commander.writeMotor(Commander.MOTOR_CAMERA, Config.get().getInt("params/motorPositions/camera/down"));
		TimeTools.sleep(Config.get().getInt("params/motorDelay"));
	}

	public void end() {
		commander.light(0, false);
		commander.light(1, false);
		commander.writePropulsionMotors(0);
	}

}
