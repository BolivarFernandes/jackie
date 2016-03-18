package net.talentum.jackie.moment.module;

import java.awt.Point;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import net.talentum.jackie.moment.MomentData;

public class BasicBorderFinderModule implements BorderFinderModule {

	int movedst;
	int tresholdWidthMax;
	int tresholdWidthMin;

	/**
	 * @param movedst
	 * @param tresholdWidthMax
	 *            max width of detected trail
	 * @param tresholdWidthMin
	 *            min width of detected trail
	 */
	public BasicBorderFinderModule(int movedst, int tresholdWidthMax, int tresholdWidthMin) {
		this.movedst = movedst;
		this.tresholdWidthMax = tresholdWidthMax;
		this.tresholdWidthMin = tresholdWidthMin;
	}

	@Override
	public Pair<Point, Point> findBorders(MomentData d, Point expected, double direction) {
		// get perpendicular line
		double perpAngle = d.perpendicularAngle(direction);

		// search for borders
		Point l = d.findBorder(expected, perpAngle, movedst, -1);
		Point r = d.findBorder(expected, perpAngle, movedst, 1);

		double dst = d.dst(l, r);
		if (dst <= tresholdWidthMax && dst >= tresholdWidthMin) {
			return new ImmutablePair<Point, Point>(l, r);
		}

		return null;
	}

}
