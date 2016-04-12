package net.talentum.jackie.robot.state;

import java.awt.Point;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.lang3.tuple.ImmutablePair;

import net.talentum.jackie.libs.PIDController;
import net.talentum.jackie.module.MotorIntensityFunction;
import net.talentum.jackie.module.impl.BasicBorderFinderModule;
import net.talentum.jackie.module.impl.BasicIntersectionSolver;
import net.talentum.jackie.module.impl.BlurImageModifierModule;
import net.talentum.jackie.module.impl.LinearMotorIntensityFunction;
import net.talentum.jackie.module.impl.UnivBooleanImageFilterModule;
import net.talentum.jackie.robot.Moment;
import net.talentum.jackie.robot.Robot;
import net.talentum.jackie.robot.RobotInstruction;
import net.talentum.jackie.robot.strategy.HorizontalLevelObservingStrategy;
import net.talentum.jackie.robot.strategy.RobotStrategy;
import net.talentum.jackie.system.Config;
import net.talentum.jackie.system.ConfigurationManager;
import net.talentum.jackie.tools.TimeTools;

/**
 * State intended for following black line, using the line
 * {@link HorizontalLevelObservingStrategy}.
 * 
 * @author padr31
 *
 */
public class LineFollowingState extends AbstractState {

	private RobotStrategy strategy;
	protected PIDController pid;
	private MotorIntensityFunction mif;

	public LineFollowingState(Robot robot) {
		super(robot);
		HierarchicalConfiguration config = ConfigurationManager.getGeneralConfiguration();

		this.mif = new LinearMotorIntensityFunction();

		// @formatter:off
		this.strategy = new HorizontalLevelObservingStrategy(
				new BlurImageModifierModule(),
				new UnivBooleanImageFilterModule(() -> ConfigurationManager.getGeneralConfiguration().getInt("params/bwTreshold")),
				new BasicBorderFinderModule(2, 600, 3),
				new BasicIntersectionSolver()
		);

		// create and setup PIDController
		pid = new PIDController(
				config.getDouble("params/lineFollowing/pid/P"),
				config.getDouble("params/lineFollowing/pid/I"),
				config.getDouble("params/lineFollowing/pid/D")
		);
		pid.setInputRange(-Math.PI / 2, Math.PI / 2);
		pid.setOutputRange(-Math.PI / 2, Math.PI / 2);
		pid.setSetpoint(0);
		pid.enable();
		
		robot.addConfigChangedListener(() -> {
			pid.setPID(
					config.getDouble("params/lineFollowing/pid/P"),
					config.getDouble("params/lineFollowing/pid/I"),
					config.getDouble("params/lineFollowing/pid/D")
			);
		});
		// @formatter:on
	}

	/**
	 * Lets the strategy process the moment. Returns {@link RobotInstruction}.
	 * 
	 * @param moment
	 * @return
	 */
	public final synchronized RobotInstruction process(Moment moment) {
		strategy.prepare(moment);
		RobotInstruction instruction = strategy.evaluate();

		return instruction;
	}

	@Override
	public State run0() {
		double heading = 0.0;

		if (robot.commander.readUltrasonicSensor(0) < 6) {
			return new ObstacleAvoidanceState(robot);
		}

		// obtain moment
		Moment moment = robot.constructMoment();

		if (moment != null) {
			// process
			RobotInstruction instruction = process(moment);

			// set motors
			// check if the result is valid
			if (instruction.destination != null && !instruction.destination.equals(new Point(0, 0))) {
				// get direction
				// direction = Math.PI / 2 -
				// Math.atan2(instruction.destination.y,
				// instruction.destination.x);
				heading = ((double) instruction.destination.x) / instruction.moment.image.getWidth();
			}
		}

		// compute heading (= control variable of PID controller)
		pid.getInput(-heading);
		heading = pid.performPID();

		// get angle values to send
		ImmutablePair<Integer, Integer> motors = mif.getMotors(heading);

		// finally write motors
		robot.commander.writePropulsionMotors(motors.left, motors.right);

		return this;
	}

	@Override
	public void begin() {
		// move camera up
		robot.commander.writeMotor(5, Config.get().getInt("params/motorPositions/m5/up"));
		TimeTools.sleep(Config.get().getInt("params/motorDelay"));

		// move arm up
		robot.commander.writeMotor(2, Config.get().getInt("params/motorPositions/m2/normal"));
		TimeTools.sleep(Config.get().getInt("params/motorDelay"));

		// turn backlight on
		robot.commander.light(0, true);

		// move camera down
		robot.commander.writeMotor(5, Config.get().getInt("params/motorPositions/m5/down"));
		TimeTools.sleep(Config.get().getInt("params/motorDelay"));
	}

	@Override
	public void end() {
		// backlight off
		robot.commander.light(0, false);
	}

}
