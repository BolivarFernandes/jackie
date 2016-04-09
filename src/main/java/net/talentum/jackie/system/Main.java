package net.talentum.jackie.system;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import net.talentum.jackie.comm.Commander;
import net.talentum.jackie.comm.ConsoleReader;
import net.talentum.jackie.comm.I2CCommunicator;
import net.talentum.jackie.comm.ImageServer;
import net.talentum.jackie.comm.TextInputProcessor;
import net.talentum.jackie.image.supplier.ImageSupplier;
import net.talentum.jackie.image.supplier.LocalWebcamImageSupplier;
import net.talentum.jackie.robot.Robot;
import net.talentum.jackie.robot.state.State;

/**
 * Runnable class.
 * 
 * <p>
 * This class is run normally, when controlling robot.
 * </p>
 * 
 * @author JJurM
 */
public class Main {

	private static ExecutorService executor = Executors.newCachedThreadPool();
	
	private static I2CCommunicator i2c;
	private static Commander commander;
	private static TextInputProcessor textInputProcessor;
	private static ConsoleReader consoleReader;
	
	private static Robot robot;
	private static ImageServer server;

	/**
	 * Number of {@link State#run()} method runs
	 */
	public static AtomicInteger runs = new AtomicInteger(0);

	public static void run(String[] args) {
		registerShutdownHook();
		
		// initialize configuration manager
		ConfigurationManager.init();
		
		Run.loadOpenCV();
		
		// create robot
		System.out.println("Setting up control classes");
		i2c = new I2CCommunicator();
		commander = new Commander(i2c);
		textInputProcessor = new TextInputProcessor(commander);
		consoleReader = new ConsoleReader(textInputProcessor);
		
		System.out.println("Creating robot");
		robot = new Robot(commander);
		ConfigurationManager.setReloadedListener(robot::configurationReloaded);

		// create image supplier
		ImageSupplier imageSupplier;
		
		/*if (args.length < 1) {
			System.out.println("You must supply server name");
			return;
		}
		imageSupplier = new ServerImageSupplier(args[0]);*/
		imageSupplier = new LocalWebcamImageSupplier();
		
		robot.setImageSupplier(imageSupplier);
		
		// start webcam server
		System.out.println("Starting webcam server");
		server = new ImageServer(imageSupplier);
		server.start();
		
		// start ConsoleReader
		consoleReader.start();

		System.out.println("Running...");

		// monitor running
		executor.submit(new Runnable() {
			@Override
			public void run() {
				while (true) {
					int count = runs.getAndSet(0);
					System.out.println(String.format("Runs: %d", count));
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		});

		// run robot's cycle infinitely
		robot.runCycle();

	}
	
	private static void registerShutdownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				shutdown();
			}
		});
	}
	
	public static void shutdown() {
		consoleReader.stop();
		robot.stop();
		
	}

}
