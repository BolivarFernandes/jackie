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
		state = new LineFollowingState(this);
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

	/**
	 * Constructs {@link Moment}, which involves taking an image from webcam
	 * supplier and collecting sensor data.
	 * 
	 * @return
	 */
	public Moment constructMoment() {
		BufferedImage image = imageSupplier.getImage();
		if (image == null) {
			System.out.println("Got null image");
			return null;
		}
		SensorData sensorData = SensorData.collect();

		/*-Image scaled = image.getScaledInstance(image.getWidth() / 2, image.getHeight() / 2,
				BufferedImage.SCALE_FAST);
		image = new BufferedImage(scaled.getWidth(null), scaled.getHeight(null), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.drawImage(scaled, 0, 0, null);
		g.dispose();*/

		return new Moment(image, sensorData);
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
					state.end();
					next.begin();
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

}
