package net.talentum.jackie.system;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import javax.swing.JFrame;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import net.talentum.jackie.ir.BlurredBooleanImageOutput;
import net.talentum.jackie.ir.BooleanImageOutput;
import net.talentum.jackie.ir.ImageOutput;
import net.talentum.jackie.ir.RobotStrategyIROutput;
import net.talentum.jackie.ir.SourceImageOutput;
import net.talentum.jackie.moment.Parameters;
import net.talentum.jackie.moment.module.AveragingTrailWidthDeterminerModule;
import net.talentum.jackie.moment.module.BasicAngularTurnHandlerModule;
import net.talentum.jackie.moment.module.BasicBorderFinderModule;
import net.talentum.jackie.moment.module.BasicLineFinderModule;
import net.talentum.jackie.moment.module.BlurImageModifierModule;
import net.talentum.jackie.moment.module.BottomLineStartFinderModule;
import net.talentum.jackie.moment.module.UnivBooleanImageFilterModule;
import net.talentum.jackie.moment.module.VectorDirectionManagerModule;
import net.talentum.jackie.moment.strategy.HorizontalLevelObservingStrategy;
import net.talentum.jackie.moment.strategy.LineFollowingStrategy;
import net.talentum.jackie.ui.StrategyComparatorPanel;

/**
 * Runnable class.
 * 
 * <p>
 * Opens windows allowing user to compare different strategies or
 * {@link ImageOutput}s. Those are defined in method
 * {@link #createImageOutputs()}.
 * </p>
 * 
 * @author JJurM
 */
public class StrategyComparatorPreview {

	static Parameters param;
	static ImageOutput[] imageOutputs;

	static JFrame previewFrame;
	static StrategyComparatorPanel strategyComparatorPanel;

	public static void main(String[] args) {

		run(args);

	}

	public static void run(String[] args) {
		param = new Parameters();

		createImageOutputs();

		EventQueue.invokeLater(() -> {
			createFrame();
		});
	}

	/**
	 * Here are defined {@link ImageOutput}s to offer in GUI.
	 */
	private static void createImageOutputs() {
		List<ImageOutput> list = new ArrayList<ImageOutput>();

		// @formatter:off
		list.add(new SourceImageOutput("Source"));
		list.add(new BlurImageModifierModule("Blur"));
		list.add(new BooleanImageOutput("BW(100)", 100));
		list.add(new BooleanImageOutput("Green", new Function<Color, Boolean>() {
			@Override
			public Boolean apply(Color c) {
				return ((double) c.getGreen()) / (c.getBlue() + c.getRed() + 1) > 0.64;
			}
		}));
		list.add(new BlurredBooleanImageOutput("Green + blur", new Function<Color, Boolean>() {
			@Override
			public Boolean apply(Color c) {
				return ((double) c.getGreen()) / (c.getBlue() + c.getRed() + 1) > 0.64;
			}
		}));
		list.add(new BlurredBooleanImageOutput("Blur + BW(100)", 100));
		list.add(new RobotStrategyIROutput("*LineFollowing", new LineFollowingStrategy(
				param,
				new BlurImageModifierModule(),
				new UnivBooleanImageFilterModule(100),
				new BottomLineStartFinderModule(),
				(d) -> new AveragingTrailWidthDeterminerModule(d, 3),
				(d) -> new VectorDirectionManagerModule(8, 3),
				new BasicLineFinderModule(
						20.0 * (Math.PI / 180),
						new BasicBorderFinderModule(2, 140, 10),
						new BasicAngularTurnHandlerModule()
				)
		)));
		list.add(new HorizontalLevelObservingStrategy.ImageOutput("*HorizontalLevelObserving", new HorizontalLevelObservingStrategy(
				param,
				new BlurImageModifierModule(),
				new UnivBooleanImageFilterModule(100),
				new BasicBorderFinderModule(2, 600, 3)
		)));
		// @formatter:on

		imageOutputs = list.toArray(new ImageOutput[0]);
	}

	private static void createFrame() {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException
				| UnsupportedLookAndFeelException e) {
			e.printStackTrace();
		}

		previewFrame = new JFrame("StrategyComparatorPreview");
		previewFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		previewFrame.setBounds(100, 100, 900, 700);
		previewFrame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				strategyComparatorPanel.stop();
				System.exit(0);
			}
		});

		strategyComparatorPanel = new StrategyComparatorPanel(imageOutputs);
		previewFrame.setContentPane(strategyComparatorPanel);

		previewFrame.setVisible(true);
	}

}
