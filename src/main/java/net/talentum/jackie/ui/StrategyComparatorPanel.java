package net.talentum.jackie.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDesktopPane;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamPicker;

import net.talentum.jackie.ir.ImageOutput;
import net.talentum.jackie.moment.Moment;
import net.talentum.jackie.moment.SensorData;
import net.talentum.jackie.system.StrategyComparatorPreview;
import net.talentum.jackie.tools.AtomicTools;

/**
 * An implementation of {@link JPanel} that is put into the frame created by
 * {@link StrategyComparatorPreview}. Contains a desktop pane, where
 * {@link ImageOutputFrame}s are created and displayed.
 * 
 * <p>
 * This class is also responsible for handling events and actions invoked by the
 * GUI. This includes managing {@link ImageOutputFrame}s, taking images,
 * processing them, redrawing.
 * </p>
 * 
 * @author JJurM
 */
public class StrategyComparatorPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	/**
	 * list of {@link ImageOutput}s to offer
	 */
	private ImageOutput[] imageOutputs;

	/**
	 * List of opened {@link ImageOutputFrame}s
	 */
	private CopyOnWriteArrayList<ImageOutputFrame> imageOutputFrames = new CopyOnWriteArrayList<ImageOutputFrame>();

	/**
	 * Number of opened {@link ImageOutputFrame}s
	 */
	static int openFrameCount = 0;

	/**
	 * Webcam that was last selected
	 */
	private Webcam lastWebcam;

	/**
	 * Whether webcam is opened
	 */
	private AtomicBoolean webcamOpen = new AtomicBoolean(false);

	/**
	 * This executor handles taking and processing images
	 */
	static ExecutorService executor = Executors.newCachedThreadPool();

	/**
	 * Whether the continuous shot mode has been started
	 */
	private boolean startedContinuous = false;

	/**
	 * Wherher the server has been started
	 */
	private AtomicBoolean startedServer = new AtomicBoolean(false);

	private ServerSocket serverSocket;

	// components
	private JPanel menu1;
	private WebcamPicker webcamSelection;
	private JComboBox<DimensionComboBoxItem> viewSizeSelection;
	private JButton btnOpenClose;
	private JButton btnRunServer;
	private JPanel menu2;
	private JComboBox<ImageOutput> imageOutputSelection;
	private JButton btnAddOutput;
	private Component horizontalStrut;
	private JPanel centerPanel;
	private JButton btnShot;
	private JButton btnStart;
	private JButton btnStop;
	private JLabel lblFps;
	private Component horizontalStrut_1;
	private JLabel lblScale;
	private JComboBox<Double> scaleSelection;
	private JDesktopPane desktopPane;

	public StrategyComparatorPanel(ImageOutput[] irOutputs) {
		this.imageOutputs = irOutputs;

		setLayout(new BorderLayout(0, 0));
		createComponents();
		webcamChanged();
	}

	/**
	 * Constructs GUI components inside the panel
	 */
	private void createComponents() {
		List<Webcam> webcams = Webcam.getWebcams();

		JPanel menuPanel = new JPanel();
		add(menuPanel, BorderLayout.NORTH);
		menuPanel.setLayout(new BoxLayout(menuPanel, BoxLayout.Y_AXIS));

		menu1 = new JPanel();
		FlowLayout flowLayout = (FlowLayout) menu1.getLayout();
		flowLayout.setAlignment(FlowLayout.LEFT);
		menuPanel.add(menu1);

		webcamSelection = new WebcamPicker(webcams);
		webcamSelection.setSelectedIndex(webcams.size() - 1);
		webcamSelection.setPreferredSize(new Dimension(160, 20));
		webcamSelection.addActionListener(e -> webcamChanged());
		menu1.add(webcamSelection);

		viewSizeSelection = new JComboBox<DimensionComboBoxItem>();
		viewSizeSelection.setPreferredSize(new Dimension(80, 20));
		viewSizeSelection.addActionListener(e -> viewSizeChanged());
		menu1.add(viewSizeSelection);

		btnOpenClose = new JButton("Open");
		btnOpenClose.addActionListener(e -> {
			btnOpenClose.setEnabled(false);
			if (!AtomicTools.getAndNegate(webcamOpen)) {
				// camera closed
				setEnabledFor(false, webcamSelection, viewSizeSelection);

				executor.submit(() -> {
					// open webcam
					final boolean opened = webcamSelection.getSelectedWebcam().open();

					// enqueue GUI changes
					EventQueue.invokeLater(() -> {
						if (opened)
							btnOpenClose.setText("Close");
						btnOpenClose.setEnabled(true);
					});
				});
			} else {
				// camera opened
				stop();

				executor.submit(() -> {
					// close webcam
					webcamSelection.getSelectedWebcam().close();

					// enqueue GUI changes
					EventQueue.invokeLater(() -> {
						btnOpenClose.setText("Open");
						setEnabledFor(true, webcamSelection, viewSizeSelection, btnOpenClose);
					});
				});
			}
		});
		menu1.add(btnOpenClose);

		btnRunServer = new JButton("Run server");
		btnRunServer.addActionListener(e -> {
			if (!webcamOpen.get())
				return;

			if (!AtomicTools.getAndNegate(startedServer)) {
				// not started
				executor.submit(() -> runServer());
				EventQueue.invokeLater(() -> {
					btnRunServer.setText("Stop server");
				});
			} else {
				// started
				try {
					serverSocket.close();
				} catch (Exception e1) {
					e1.printStackTrace();
				}
				EventQueue.invokeLater(() -> {
					btnRunServer.setText("Run server");
				});
			}
		});
		menu1.add(btnRunServer);

		horizontalStrut_1 = Box.createHorizontalStrut(20);
		menu1.add(horizontalStrut_1);

		lblScale = new JLabel("Scale:");
		menu1.add(lblScale);

		scaleSelection = new JComboBox<Double>();
		scaleSelection.setModel(new DefaultComboBoxModel<Double>(new Double[] { 0.25, 0.5, 1.0, 2.0, 4.0 }));
		scaleSelection.setSelectedItem(1.0);
		scaleSelection.addActionListener(e -> scaledViewSizeChanged());
		menu1.add(scaleSelection);

		menu2 = new JPanel();
		FlowLayout flowLayout_1 = (FlowLayout) menu2.getLayout();
		flowLayout_1.setAlignment(FlowLayout.LEFT);
		menuPanel.add(menu2);

		imageOutputSelection = new JComboBox<ImageOutput>();
		imageOutputSelection.setPreferredSize(new Dimension(200, 20));
		imageOutputSelection.setModel(new DefaultComboBoxModel<ImageOutput>(imageOutputs));
		imageOutputSelection.setMaximumRowCount(20);
		menu2.add(imageOutputSelection);

		btnAddOutput = new JButton("Add output");
		btnAddOutput.addActionListener(e -> {
			// create new image output frame
			ImageOutput imageOutput = (ImageOutput) imageOutputSelection.getSelectedItem();
			ImageOutputFrame frame = new ImageOutputFrame(imageOutput);
			imageOutputFrames.add(frame);
			desktopPane.add(frame);
			try {
				frame.setSelected(true);
			} catch (Exception e1) {
			}

			desktopPane.revalidate();
			desktopPane.repaint();
		});
		menu2.add(btnAddOutput);

		horizontalStrut = Box.createHorizontalStrut(20);
		menu2.add(horizontalStrut);

		btnShot = new JButton("Shot");
		btnShot.addActionListener(e -> {
			if (!webcamOpen.get())
				return;
			setEnabledFor(false, btnShot, btnStart);
			executor.submit(() -> {
				shot();
				EventQueue.invokeLater(() -> setEnabledFor(true, btnShot, btnStart));
			});
		});
		menu2.add(btnShot);

		btnStart = new JButton("Start continuous");
		btnStart.addActionListener(e -> {
			if (!webcamOpen.get())
				return;
			setEnabledFor(false, btnShot, btnStart);
			btnStop.setEnabled(true);
			startedContinuous = true;
			executor.submit(() -> {
				while (startedContinuous) {
					if (!webcamOpen.get())
						break;
					shot();
				}
				EventQueue.invokeLater(() -> {
					setEnabledFor(true, btnShot, btnStart);
					btnStop.setEnabled(false);
				});
			});
		});
		menu2.add(btnStart);

		btnStop = new JButton("Stop");
		btnStop.setEnabled(false);
		btnStop.addActionListener(e -> {
			stop();
		});
		menu2.add(btnStop);

		lblFps = new JLabel("");
		menu2.add(lblFps);

		centerPanel = new JPanel();
		add(centerPanel, BorderLayout.CENTER);
		centerPanel.setLayout(new BorderLayout(0, 0));

		desktopPane = new JDesktopPane();
		centerPanel.add(desktopPane, BorderLayout.CENTER);
		desktopPane.setPreferredSize(new Dimension(500, 500));
	}

	/**
	 * Method run when the webcam selection has been changed
	 */
	private void webcamChanged() {
		Webcam webcam = webcamSelection.getSelectedWebcam();

		if (webcam != lastWebcam) {
			lastWebcam = webcam;
			viewSizeSelection.setModel(new DefaultComboBoxModel<DimensionComboBoxItem>(Arrays
					.stream(webcam.getViewSizes()).map((Dimension dimension) -> new DimensionComboBoxItem(dimension))
					.toArray(size -> new DimensionComboBoxItem[size])));
			viewSizeSelection.setSelectedIndex(webcam.getViewSizes().length - 1);
		}

		viewSizeChanged();
	}

	/**
	 * Returns currently selected view size
	 * 
	 * @return
	 */
	private Dimension getViewSize() {
		return ((DimensionComboBoxItem) viewSizeSelection.getSelectedItem()).getValue();
	}

	/**
	 * Returns currently selected view scale
	 * 
	 * @return
	 */
	private double getScale() {
		return (Double) scaleSelection.getSelectedItem();
	}

	/**
	 * Returns currently selected view size multiplied by currently selected
	 * scale.
	 * 
	 * @return
	 */
	private Dimension getScaledViewSize() {
		Dimension orig = getViewSize();
		double scale = getScale();
		return new Dimension((int) (orig.width * scale), (int) (orig.height * scale));
	}

	/**
	 * Method run when the currently selected view size has been changed
	 */
	private void viewSizeChanged() {
		Dimension viewSize = getViewSize();
		webcamSelection.getSelectedWebcam().setViewSize(viewSize);

		scaledViewSizeChanged();
	}

	/**
	 * Method run when the currently selected view size or scale has been
	 * changed
	 */
	private void scaledViewSizeChanged() {
		Dimension scaled = getScaledViewSize();
		imageOutputFrames.stream().forEach(panel -> {
			panel.viewSizeChanged(scaled);
		});

		revalidate();
		repaint();
	}

	/**
	 * Takes one-time image from webcam and collects sensor data, creates moment
	 * and lets the moment to be processed by each {@link ImageOutputFrame}.
	 * Also calculates and
	 */
	private void shot() {
		long t = System.currentTimeMillis();
		try {
			// construct Moment
			Webcam webcam = webcamSelection.getSelectedWebcam();
			BufferedImage image = webcam.getImage();
			SensorData sensorData = SensorData.collect();
			Moment moment = new Moment(image, sensorData);

			// arrange for processing
			for (ImageOutputFrame p : imageOutputFrames) {
				p.receive(moment);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		// calculates the duration of the processing and enqueues the change of
		// displayed FPS
		final long duration = System.currentTimeMillis() - t;
		EventQueue.invokeLater(() -> lblFps.setText("FPS: " + 1000 / duration));
	}

	/**
	 * Calls {@link #setEnabled(boolean)} method for given components.
	 * Convenience method.
	 * 
	 * @param enabled
	 *            Whether the components should be enabled or disabled
	 * @param components
	 *            List of components to call {@link #setEnabled(boolean)} on
	 */
	public void setEnabledFor(boolean enabled, JComponent... components) {
		for (JComponent component : components) {
			component.setEnabled(enabled);
		}
	}

	/**
	 * Stops continuous mode
	 */
	public void stop() {
		startedContinuous = false;
	}

	/**
	 * Image output frame (child of {@link JInternalFrame}) that is created
	 * inside the in-app desktop of the {@link StrategyComparatorPanel}. Each
	 * user-selected {@link ImageOutput} creates its own panel. This panel can
	 * be freely moved inside the desktop and supports Minimize and Close
	 * operations (invoked from title bar).
	 * 
	 * @author JJurM
	 */
	class ImageOutputFrame extends JInternalFrame {
		private static final long serialVersionUID = 1L;
		static final int xOffset = 20, yOffset = 20;

		private ImageOutput imageOutput;
		private ImagePanel imagePanel;

		/**
		 * Creates the frame and its components
		 * 
		 * @param irOutput
		 */
		public ImageOutputFrame(ImageOutput irOutput) {
			super(irOutput.getName(), false, true, false, true);
			this.imageOutput = irOutput;
			openFrameCount++;

			setLocation(xOffset * openFrameCount, yOffset * openFrameCount);
			setLayout(new BorderLayout(0, 0));
			setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
			addInternalFrameListener(new ImageOutputFrameListener());

			imagePanel = new ImagePanel();
			imagePanel.setPreferredSize(getScaledViewSize());
			// imagePanel.setBackground(Color.GRAY);
			add(imagePanel, BorderLayout.CENTER);

			pack();
			setVisible(true);
		}

		/**
		 * This method is triggered when the frame should update its view size.
		 * 
		 * @param d
		 */
		private void viewSizeChanged(Dimension d) {
			imagePanel.setPreferredSize(d);
			pack();

			imagePanel.revalidate();
			imagePanel.repaint();
		}

		/**
		 * Evokes processing by ImageRecognitionOutput and displays result.
		 * 
		 * @param moment
		 */
		void receive(Moment moment) {
			Image image = imageOutput.process(moment);

			Dimension scaled = getScaledViewSize();
			image = image.getScaledInstance(scaled.width, scaled.height, Image.SCALE_FAST);

			imagePanel.setImage(image);
			imagePanel.repaint();
		}

		/**
		 * Listener for internal frame events, only used in
		 * {@link ImageOutputFrame}.
		 * 
		 * @author JJurM
		 */
		class ImageOutputFrameListener extends InternalFrameAdapter {

			@Override
			public void internalFrameClosing(InternalFrameEvent e) {
				openFrameCount--;

				ImageOutputFrame frame = (ImageOutputFrame) e.getInternalFrame();
				desktopPane.remove(frame);
				imageOutputFrames.remove(frame);

				desktopPane.revalidate();
				desktopPane.repaint();
			}

		}

	}

	/**
	 * Image panel used for drawing images inside the {@link ImageOutputFrame}s
	 * 
	 * @author JJurM
	 */
	static class ImagePanel extends JPanel {
		private static final long serialVersionUID = 1L;

		private Image image = null;

		public void setImage(Image image) {
			this.image = image;
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			if (image != null) {
				g.drawImage(image, 0, 0, null);
			}
		}

	}

	/**
	 * Continuously runs server (while not stopped by user)
	 */
	public void runServer() {

		int portNumber = 4444;
		try {
			serverSocket = new ServerSocket(portNumber);

			while (startedServer.get()) {
				try {
					Socket client = serverSocket.accept();

					executor.submit(() -> {
						try {
							while (true) {
								BufferedImage image = webcamSelection.getSelectedWebcam().getImage();
								ImageIO.write(image, "JPG", client.getOutputStream());
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					});

				} catch (IOException e) {
					e.printStackTrace();
				}
			}

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

}
