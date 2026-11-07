/*
 * SPDX-License-Identifier: MIT
 * This file is part of CassandraGargoyle Community Project
 * Licensed under the MIT License - see LICENSE file for details
 */
package org.cassandragargoyle.portunix.tabledetector.pdf;

import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;
import org.cassandragargoyle.portunix.tabledetector.model.BoundingBox;
import org.cassandragargoyle.portunix.tabledetector.model.TableCell;
import org.cassandragargoyle.portunix.tabledetector.model.TableModel;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.cassandragargoyle.api.log.LogFactory;

/**
 * Detects tables in PDF pages using text position analysis
 * Uses heuristics to identify tabular structure from text layout
 * Migrated from vim-parser-table TableDetector
 *
 * @author Zdenek
 * @since 2026-02-03
 */
public class PdfTableDetector extends PDFTextStripper
{
	private static final Logger LOG = LogFactory.getLogger(PdfTableDetector.class);

	// Tolerance for column alignment (in PDF points)
	private static final float COLUMN_TOLERANCE = 5.0f;

	// Minimum number of rows to consider as table
	private static final int MIN_ROWS = 2;

	// Minimum number of columns to consider as table
	private static final int MIN_COLUMNS = 2;

	// Baselines closer than this many points belong to one row
	private static final float ROW_TOLERANCE = 3.0f;

	// Gap threshold to detect column separation (multiplier of average char width)
	private static final float GAP_THRESHOLD = 2.0f;

	// A row is running text when it has at least this many visible glyphs and words, and evenly spaced
	// words: the median word gap is at most RUNNING_TEXT_MAX_WORD_GAP space widths and the widest word
	// gap at most RUNNING_TEXT_MAX_GAP_RATIO times the median
	private static final int RUNNING_TEXT_MIN_GLYPHS = 40;

	private static final int RUNNING_TEXT_MIN_WORDS = 6;

	private static final float RUNNING_TEXT_MAX_WORD_GAP = 6.0f;

	private static final float RUNNING_TEXT_MAX_GAP_RATIO = 2.0f;

	// Glyphs closer than this part of the space width belong to the same word
	private static final float WORD_GAP_RATIO = 0.5f;

	// Baselines closer than this many degrees belong to one family, the words of an OCR text layer of one
	// table vary by a few tenths of a degree while tables of different skew stay apart (issue #208)
	private static final float ANGLE_GAP = 1.0f;

	// Baselines closer than this many degrees form a cluster inside a family, a family can hold an upright
	// table next to a slightly skewed paragraph whose angles run into each other
	private static final float ANGLE_STEP = 0.15f;

	// A cluster with at least this share of the glyphs of the page or its family offers its mean angle as
	// a frame, a single noisy cell has to choose a frame of its table
	private static final float FRAME_MIN_SHARE = 0.1f;

	private static final double[] RIGHT_ANGLES = {0, 90, 180, 270};

	// Mean angle closer than this many degrees to a right angle is that right angle
	private static final float RIGHT_ANGLE_EPSILON = 0.05f;

	// Baselines further apart than this many points in a frame count as separate rows of that frame
	private static final float ROW_GAP = 0.75f;

	// A frame is searched this many degrees around the angle of its cluster in steps of FRAME_SEARCH_STEP,
	// an OCR text layer can write the words of a table with an angle other than the skew of the page
	private static final float FRAME_SEARCH_RANGE = 1.5f;

	private static final float FRAME_SEARCH_STEP = 0.1f;

	// The skew of a page is searched this many degrees around each right angle, an OCR text layer can write
	// glyph angles that carry no trace of that skew (issue #211)
	private static final float PAGE_SKEW_RANGE = 5.0f;

	// A page skew has to leave at most this share of the rows of the right angle, text of two tables at
	// different angles gains nothing from a frame of one of them
	private static final float PAGE_SKEW_MAX_ROWS = 0.75f;

	// Frames closer than this many degrees to the page skew are that skew, a family must not split from
	// the rest of the page by a few hundredths of a degree
	private static final float PAGE_SKEW_SAME_FRAME = 0.25f;

	// The page skew is searched on at most this many glyphs, every angle sorts the whole sample
	private static final int PAGE_SKEW_MAX_GLYPHS = 2000;

	// Text further apart than this many points across the rows of a right angle lies in separate regions
	// of the page, each region is searched for its own skew (issue #213)
	private static final float REGION_MIN_GAP = 40.0f;

	// A region needs at least this many rows in a frame to take it, two cells off each other line up at
	// some angle, so a few rows tell nothing about skew
	private static final int REGION_MIN_ROWS = 4;

	// A region frame has to leave at most this share of the rows of the frame it replaces, stricter than
	// PAGE_SKEW_MAX_ROWS: two columns on offset baselines line up at some angle and halve their rows,
	// a skewed table of at least three columns falls to a third
	private static final float REGION_MAX_ROWS = 0.4f;

	// A region of at least REGION_MIN_COLUMNS columns in the frame may keep this share of the rows, its
	// columns can land on neighbouring rows of the frame it replaces, which only halves them (issue #215)
	private static final float REGION_MAX_ROWS_COLUMNS = 0.5f;

	private static final int REGION_MIN_COLUMNS = 3;

	// A row with at least this many glyphs is a line of its own, not a cell turned inside a table
	private static final int LINE_MIN_GLYPHS = 25;

	private final List<TextPosition> textPositions;

	private int currentPage;

	public PdfTableDetector() throws IOException
	{
		super();
		this.textPositions = new ArrayList<>();
		setSortByPosition(true);
	}

	/**
	 * High-level API: detect tables on a specific page of a PDF document
	 *
	 * @param document   the PDF document
	 * @param pageNumber 1-based page number
	 * @return list of detected tables
	 */
	public List<TableModel> detectTables(PDDocument document, int pageNumber) throws IOException
	{
		reset();
		setCurrentPage(pageNumber);
		setStartPage(pageNumber);
		setEndPage(pageNumber);

		StringWriter writer = new StringWriter();
		writeText(document, writer);

		return detectTables();
	}

	/**
	 * Reset state for new page processing
	 */
	public void reset()
	{
		textPositions.clear();
	}

	/**
	 * Set current page number
	 */
	public void setCurrentPage(int page)
	{
		this.currentPage = page;
	}

	/**
	 * Process a page, an empty crop box is replaced by the media box for the time of processing
	 * PDFBox clips the crop box to the media box, the result is empty when the box is zero sized or
	 * disjoint from the media box, while viewers still show the page
	 */
	@Override
	public void processPage(PDPage page) throws IOException
	{
		PDRectangle cropBox = page.getCropBox();
		if (cropBox.getWidth() > 0 && cropBox.getHeight() > 0)
		{
			super.processPage(page);
			return;
		}

		COSBase declaredCropBox = page.getCOSObject().getItem(COSName.CROP_BOX);
		page.setCropBox(page.getMediaBox());
		try
		{
			super.processPage(page);
		}
		finally
		{
			page.getCOSObject().setItem(COSName.CROP_BOX, declaredCropBox);
		}
	}

	@Override
	protected void writeString(String string, List<TextPosition> positions) throws IOException
	{
		for (TextPosition pos : positions)
		{
			if (isOnDisplayedPage(pos))
			{
				textPositions.add(pos);
			}
		}
	}

	/**
	 * Whether at least a part of the glyph box lies on the displayed page
	 * PDFBox also reports text outside the crop box, viewers do not show it
	 */
	private static boolean isOnDisplayedPage(TextPosition pos)
	{
		boolean swapped = Math.floorMod(Math.round(pos.getRotation() / 90f), 2) == 1;
		float displayWidth = swapped ? pos.getPageHeight() : pos.getPageWidth();
		float displayHeight = swapped ? pos.getPageWidth() : pos.getPageHeight();
		BoundingBox box = toDisplaySpace(new BoundingBox(x(pos), y(pos) - height(pos), width(pos), height(pos)), pos);
		return box.getX() <= displayWidth && box.getX() + box.getWidth() >= 0
			&& box.getY() <= displayHeight && box.getY() + box.getHeight() >= 0;
	}

	/**
	 * Detect tables from collected text positions
	 * @return list of detected tables
	 */
	public List<TableModel> detectTables()
	{
		if (textPositions.isEmpty())
		{
			LOG.log(Level.FINER, "No text positions collected");
			return Collections.emptyList();
		}

		LOG.log(Level.FINER, "Text positions: {0}", textPositions.size());

		List<TableModel> tables = new ArrayList<>();

		// Text of each reading direction is laid out in its own frame, vertical labels must not split
		// the rows of a horizontal table (issue #173)
		for (List<Glyph> direction : groupByDirection())
		{
			// Group text by rows (Y position)
			List<TextRow> rows = groupIntoRows(direction);

			LOG.log(Level.FINER, "Rows detected: {0}", rows.size());

			// Running text separates regions, a matrix between paragraphs gets its own table (issue #200)
			for (List<TextRow> region : splitAtRunningText(rows))
			{
				TableModel table = detectTable(region, "table-" + (tables.size() + 1));
				if (table != null)
				{
					tables.add(table);
				}
			}
		}

		return tables;
	}

	/**
	 * Text positions grouped by text direction, 0 first, then 90, 180 and 270 degrees
	 * Skewed text follows, one group per frame angle in ascending order (issue #208)
	 * The angle of a glyph only proposes frames, the position of the text on the page decides
	 */
	private List<List<Glyph>> groupByDirection()
	{
		List<List<Glyph>> directions = new ArrayList<>();
		for (int i = 0; i < 4; i++)
		{
			directions.add(new ArrayList<>());
		}
		List<List<TextPosition>> families = groupByAngle(textPositions, ANGLE_GAP);
		Map<Double, Integer> frames = candidateFrames(families);
		TreeMap<Double, List<TextPosition>> skewed = new TreeMap<>();
		for (List<TextPosition> family : families)
		{
			for (List<TextPosition> part : splitByRegionFrame(family, frames))
			{
				splitByFrame(part, frames).forEach((frame, group) ->
				{
					if (isSkewed(frame))
					{
						skewed.computeIfAbsent(frame, key -> new ArrayList<>()).addAll(group);
					}
					else
					{
						for (TextPosition pos : group)
						{
							directions.get(directionIndex(pos)).add(Glyph.upright(pos));
						}
					}
				});
			}
		}
		skewed.forEach((frame, group) -> directions.add(toFrame(group, frame)));
		directions.removeIf(List::isEmpty);
		return directions;
	}

	/**
	 * Text positions grouped by baseline angle, neighbours in a group differ by less than the gap
	 */
	private static List<List<TextPosition>> groupByAngle(List<TextPosition> positions, float gap)
	{
		List<TextPosition> sorted = new ArrayList<>(positions);
		sorted.sort(Comparator.comparingDouble(PdfTableDetector::baselineAngle));

		List<List<TextPosition>> groups = new ArrayList<>();
		List<TextPosition> group = null;
		double previous = 0;
		for (TextPosition pos : sorted)
		{
			double angle = baselineAngle(pos);
			if (group == null || angle - previous >= gap)
			{
				group = new ArrayList<>();
				groups.add(group);
			}
			group.add(pos);
			previous = angle;
		}

		// Angles are circular, text around 0 degrees starts at both ends of the range
		if (groups.size() > 1 && baselineAngle(sorted.get(0)) + 360 - previous < gap)
		{
			groups.get(groups.size() - 1).addAll(groups.remove(0));
		}
		return groups;
	}

	/**
	 * Frame angles with the number of glyphs that support them: right angles, the mean of each family and
	 * the mean of each cluster with enough glyphs
	 * A family supports its mean only when its rows are straighter there than at the nearest right angle
	 */
	private Map<Double, Integer> candidateFrames(List<List<TextPosition>> families)
	{
		Map<Double, Integer> frames = new LinkedHashMap<>();
		for (double angle : RIGHT_ANGLES)
		{
			frames.put(angle, 0);
		}
		for (List<TextPosition> family : families)
		{
			double mean = meanAngle(family);
			double right = nearestRightAngle(mean);
			boolean large = family.size() >= FRAME_MIN_SHARE * textPositions.size();
			double frame = large && isSkewed(mean) ? straightestFrame(family, mean) : mean;
			if (isSkewed(mean))
			{
				frames.putIfAbsent(frame, 0);
			}
			frames.merge(isSkewed(mean) && countRows(family, frame) < countRows(family, right) ? frame : right, family.size(), Integer::sum);
			for (List<TextPosition> cluster : groupByAngle(family, ANGLE_STEP))
			{
				double angle = meanAngle(cluster);
				if (isSkewed(angle) && cluster.size() >= FRAME_MIN_SHARE * textPositions.size())
				{
					frames.merge(straightestFrame(cluster, angle), cluster.size(), Integer::sum);
				}
			}
		}
		for (double right : RIGHT_ANGLES)
		{
			addPageSkew(frames, right);
		}
		return frames;
	}

	/**
	 * Offer the angle in which the glyphs near a right angle form far fewer rows than at that right angle
	 * The whole page only proposes the frame, each family still takes it by its own rows in chooseFrame,
	 * so a table that is straight at its own angle keeps that angle (issue #211)
	 * The frame wins every tie of rows, so a family too small to show the skew joins the rest of the page
	 * Two regions of rows are searched one by one, tables at different skew cancel out on the whole page
	 * and an OCR text layer can write both at one angle (issue #213)
	 * The page skew does not raise the bar for a region, between two skews it is a compromise that halves
	 * the rows of both tables (issue #215)
	 */
	private void addPageSkew(Map<Double, Integer> frames, double right)
	{
		List<TextPosition> near = new ArrayList<>();
		for (TextPosition pos : textPositions)
		{
			if (angleDistance(baselineAngle(pos), right) <= PAGE_SKEW_RANGE)
			{
				near.add(pos);
			}
		}
		if (near.isEmpty())
		{
			return;
		}

		List<TextPosition> sample = sampleOf(near);
		Double pageSkew = findSkew(sample, right);

		if (pageSkew != null)
		{
			frames.keySet().removeIf(frame -> isSkewed(frame) && angleDistance(frame, pageSkew) < PAGE_SKEW_SAME_FRAME);
			frames.put(pageSkew, Collections.max(frames.values()) + 1);
		}

		// A frame across the rows counts the width of the text, that says nothing about these rows
		List<Double> rivals = new ArrayList<>(frames.keySet());
		rivals.removeIf(frame -> frame.equals(pageSkew) || angleDistance(frame, right) > 45);

		// One region of rows is the page itself, only two of them can tell two skews
		List<List<TextPosition>> regions = new ArrayList<>(splitAtEmptyBands(sample, right));
		regions.removeIf(region -> countRows(region, right) < REGION_MIN_ROWS);
		if (regions.size() < 2)
		{
			return;
		}
		for (List<TextPosition> region : regions)
		{
			// A region takes a frame of its own only when no frame so far comes close, a table split by a
			// band stays in one frame
			// Like the page skew the frame wins every tie, a column at its own angle stays in its table
			Double skew = findSkew(region, right);
			int rows = rivals.stream().mapToInt(frame -> countRows(region, frame)).min().orElseThrow();
			if (skew != null && isFarStraighter(region, skew, rows))
			{
				frames.put(skew, Collections.max(frames.values()) + 1);
				rivals.add(skew);
			}
		}
	}

	/**
	 * Angle within PAGE_SKEW_RANGE of the right angle in which the group forms far fewer rows than at that
	 * right angle, null when there is none
	 */
	private static Double findSkew(List<TextPosition> group, double right)
	{
		int steps = Math.round(PAGE_SKEW_RANGE / FRAME_SEARCH_STEP);
		int bestRows = Integer.MAX_VALUE;
		int firstStep = 0;
		int lastStep = 0;
		for (int step = -steps; step <= steps; step++)
		{
			int rows = countRows(group, right + step * FRAME_SEARCH_STEP);
			if (rows < bestRows)
			{
				bestRows = rows;
				firstStep = step;
				lastStep = step;
			}
			else if (rows == bestRows && step == lastStep + 1)
			{
				lastStep = step;
			}
		}
		if (bestRows > PAGE_SKEW_MAX_ROWS * countRows(group, right))
		{
			return null;
		}
		// Short rows stay straight over a range of angles, the true skew lies in the middle of it
		return (right + (firstStep + lastStep) / 2.0 * FRAME_SEARCH_STEP + 360) % 360;
	}

	/**
	 * Whether the region forms a table sized block of rows in the frame, far fewer than the given rows
	 * Offset text columns only pair up, so half of the rows counts only with more columns than a pair
	 */
	private static boolean isFarStraighter(List<TextPosition> region, double frame, int rows)
	{
		int frameRows = countRows(region, frame);
		if (frameRows < REGION_MIN_ROWS)
		{
			return false;
		}
		return frameRows <= REGION_MAX_ROWS * rows
			|| frameRows <= REGION_MAX_ROWS_COLUMNS * rows && splitAtEmptyBands(region, frame + 90).size() >= REGION_MIN_COLUMNS;
	}

	/**
	 * Text of the group split at bands wider than REGION_MIN_GAP across the rows of the angle, the order
	 * of the group stays inside each region
	 * A band found at a right angle stays empty on a skewed page only when it is wider than the text drifts
	 */
	private static List<List<TextPosition>> splitAtEmptyBands(List<TextPosition> group, double angle)
	{
		double[] baselines = new double[group.size()];
		for (int i = 0; i < baselines.length; i++)
		{
			baselines[i] = baseline(group.get(i), angle);
		}
		Arrays.sort(baselines);

		List<Double> bands = new ArrayList<>();
		for (int i = 1; i < baselines.length; i++)
		{
			if (baselines[i] - baselines[i - 1] > REGION_MIN_GAP)
			{
				bands.add(baselines[i]);
			}
		}
		if (bands.isEmpty())
		{
			return List.of(group);
		}

		List<List<TextPosition>> regions = new ArrayList<>();
		for (int i = 0; i <= bands.size(); i++)
		{
			regions.add(new ArrayList<>());
		}
		for (TextPosition pos : group)
		{
			int index = Collections.binarySearch(bands, baseline(pos, angle));
			regions.get(index < 0 ? -index - 1 : index + 1).add(pos);
		}
		return regions;
	}

	/**
	 * Family without the regions that are far straighter in a frame of their own, each of them follows
	 * as a part, a region gaining a row or two stays with the family
	 */
	private static List<List<TextPosition>> splitByRegionFrame(List<TextPosition> family, Map<Double, Integer> frames)
	{
		List<List<TextPosition>> regions = splitAtEmptyBands(family, nearestRightAngle(meanAngle(family)));
		if (regions.size() < 2)
		{
			return regions;
		}
		double familyFrame = chooseFrame(family, frames);
		List<List<TextPosition>> parts = new ArrayList<>();
		List<TextPosition> rest = new ArrayList<>();
		for (List<TextPosition> region : regions)
		{
			if (isFarStraighter(region, chooseFrame(region, frames), countRows(region, familyFrame)))
			{
				parts.add(region);
			}
			else
			{
				rest.addAll(region);
			}
		}
		if (parts.isEmpty())
		{
			return List.of(family);
		}
		if (!rest.isEmpty())
		{
			parts.add(rest);
		}
		return parts;
	}

	/**
	 * Evenly spaced glyphs of the group, at most PAGE_SKEW_MAX_GLYPHS of them
	 */
	private static List<TextPosition> sampleOf(List<TextPosition> group)
	{
		if (group.size() <= PAGE_SKEW_MAX_GLYPHS)
		{
			return group;
		}
		List<TextPosition> sample = new ArrayList<>();
		for (int i = 0; i < PAGE_SKEW_MAX_GLYPHS; i++)
		{
			sample.add(group.get((int) ((long) i * group.size() / PAGE_SKEW_MAX_GLYPHS)));
		}
		return sample;
	}

	/**
	 * Angle around the given one in which the group forms the fewest rows, on a tie the closest
	 * one, so text that is straight at its own angle keeps that angle
	 * The nearest right angle wins when it gives no more rows
	 */
	private static double straightestFrame(List<TextPosition> group, double angle)
	{
		double best = angle;
		int bestRows = countRows(group, angle);
		int steps = Math.round(FRAME_SEARCH_RANGE / FRAME_SEARCH_STEP);
		for (int step = 1; step <= steps; step++)
		{
			for (int sign = -1; sign <= 1; sign += 2)
			{
				double candidate = angle + sign * step * FRAME_SEARCH_STEP;
				int rows = countRows(group, candidate);
				if (rows < bestRows)
				{
					best = candidate;
					bestRows = rows;
				}
			}
		}
		double right = nearestRightAngle(angle);
		return countRows(group, right) <= bestRows ? right : (best + 360) % 360;
	}

	/**
	 * Family by frame: one frame, or the nearest right angle and the frame of each large cluster when
	 * they give fewer rows, that family mixes upright text with slightly skewed text
	 */
	private static Map<Double, List<TextPosition>> splitByFrame(List<TextPosition> family, Map<Double, Integer> frames)
	{
		List<Double> parts = new ArrayList<>(List.of(nearestRightAngle(meanAngle(family))));
		for (List<TextPosition> cluster : groupByAngle(family, ANGLE_STEP))
		{
			if (cluster.size() >= FRAME_MIN_SHARE * family.size() && isSkewed(meanAngle(cluster)))
			{
				parts.add(meanAngle(cluster));
			}
		}

		Map<Double, List<TextPosition>> split = new TreeMap<>();
		for (TextPosition pos : family)
		{
			double angle = baselineAngle(pos);
			double part = Collections.min(parts, Comparator.comparingDouble(frame -> angleDistance(angle, frame)));
			split.computeIfAbsent(part, key -> new ArrayList<>()).add(pos);
		}

		int rows = 0;
		for (Map.Entry<Double, List<TextPosition>> part : split.entrySet())
		{
			rows += countRows(part.getValue(), part.getKey());
		}
		double frame = chooseFrame(family, frames);
		if (split.size() > 1 && rows < countRows(family, frame))
		{
			return split;
		}
		return Map.of(frame, family);
	}

	/**
	 * Frame in which the text forms the fewest rows, the angle of a glyph is only a proposal, the words of
	 * one row turned by noise still line up in the frame of their table
	 * On a tie the frame with the most glyphs wins, then the closest one
	 * Text of one row in its own frame has no rows to tell, a turned cell stays in the frame of its table
	 * and only a long line keeps its own frame
	 */
	private static double chooseFrame(List<TextPosition> group, Map<Double, Integer> frames)
	{
		double angle = meanAngle(group);
		boolean singleRow = group.size() < LINE_MIN_GLYPHS && countRows(group, angle) == 1;
		double best = 0;
		double bestDistance = Double.MAX_VALUE;
		int bestRows = Integer.MAX_VALUE;
		int bestWeight = -1;
		for (Map.Entry<Double, Integer> frame : frames.entrySet())
		{
			double distance = angleDistance(angle, frame.getKey());
			if (distance > 45)
			{
				continue;
			}
			int rows = singleRow ? 1 : countRows(group, frame.getKey());
			int weight = frame.getValue();
			if (rows < bestRows || (rows == bestRows && (weight > bestWeight || (weight == bestWeight && distance < bestDistance))))
			{
				best = frame.getKey();
				bestDistance = distance;
				bestRows = rows;
				bestWeight = weight;
			}
		}
		return best;
	}

	private static double nearestRightAngle(double angle)
	{
		return Math.floorMod(Math.round(angle / 90), 4) * 90.0;
	}

	/**
	 * Smallest difference of two angles in degrees, 0 to 180
	 */
	private static double angleDistance(double first, double second)
	{
		double difference = Math.abs(first - second) % 360;
		return Math.min(difference, 360 - difference);
	}

	/**
	 * Number of rows the glyph origins form in the frame of the angle
	 * Lines crossing the rows of the frame run together, a row higher than ROW_TOLERANCE counts once per
	 * ROW_TOLERANCE of its height
	 */
	private static int countRows(List<TextPosition> group, double angle)
	{
		double[] baselines = new double[group.size()];
		for (int i = 0; i < baselines.length; i++)
		{
			baselines[i] = baseline(group.get(i), angle);
		}
		Arrays.sort(baselines);
		int rows = 0;
		double top = baselines[0];
		for (int i = 1; i <= baselines.length; i++)
		{
			if (i == baselines.length || baselines[i] - baselines[i - 1] > ROW_GAP)
			{
				rows += 1 + (int) ((baselines[i - 1] - top) / ROW_TOLERANCE);
				top = i < baselines.length ? baselines[i] : top;
			}
		}
		return rows;
	}

	/**
	 * Position of the glyph origin across the rows of the frame of the angle
	 */
	private static double baseline(TextPosition pos, double angle)
	{
		double radians = Math.toRadians(angle - pos.getRotation());
		return pos.getX() * Math.sin(radians) + pos.getY() * Math.cos(radians);
	}

	/**
	 * Circular mean of the baseline angles, 0 to 360, the group can lie on both sides of 0 degrees
	 */
	private static double meanAngle(List<TextPosition> group)
	{
		double sin = 0;
		double cos = 0;
		for (TextPosition pos : group)
		{
			double radians = Math.toRadians(baselineAngle(pos));
			sin += Math.sin(radians);
			cos += Math.cos(radians);
		}
		double angle = Math.toDegrees(Math.atan2(sin, cos));
		return angle < 0 ? angle + 360 : angle;
	}

	/**
	 * Glyphs of one group in a frame where their text runs from left to right
	 * The frame turns the displayed page by the angle of the baseline in that page
	 * A word stays on the row of its first glyph, an OCR text layer can write it at an angle other than the
	 * frame and its end would leave the row (issue #211)
	 */
	private static List<Glyph> toFrame(List<TextPosition> group, double angle)
	{
		double frameAngle = angle - group.get(0).getRotation();
		List<Glyph> glyphs = new ArrayList<>();
		TextPosition previous = null;
		for (TextPosition pos : group)
		{
			Glyph glyph = Glyph.skewed(pos, frameAngle);
			if (previous != null && continuesWord(previous, pos))
			{
				glyph = glyph.onRowOf(glyphs.get(glyphs.size() - 1));
			}
			glyphs.add(glyph);
			previous = pos;
		}
		return glyphs;
	}

	/**
	 * Whether the glyph starts where the previous one ends, on a baseline of the same angle
	 */
	private static boolean continuesWord(TextPosition previous, TextPosition pos)
	{
		Matrix start = previous.getTextMatrix();
		Matrix next = pos.getTextMatrix();
		double advance = Math.hypot(previous.getEndX() - start.getTranslateX(), previous.getEndY() - start.getTranslateY());
		double gap = Math.hypot(next.getTranslateX() - previous.getEndX(), next.getTranslateY() - previous.getEndY());
		return gap <= WORD_GAP_RATIO * advance && baselineAngle(previous) == baselineAngle(pos);
	}

	/**
	 * Baseline angle of the text rendering matrix counterclockwise in degrees, 0 to 360, before the page
	 * rotation; TextPosition.getDir gives only the nearest quarter turn
	 */
	private static double baselineAngle(TextPosition pos)
	{
		Matrix matrix = pos.getTextMatrix();
		double angle = Math.toDegrees(Math.atan2(matrix.getShearY(), matrix.getScaleX()));
		return angle < 0 ? angle + 360 : angle;
	}

	/**
	 * Whether the angle is not a right angle, right angles keep the direction-adjusted frame
	 */
	private static boolean isSkewed(double angle)
	{
		return Math.abs(angle - 90 * Math.round(angle / 90)) >= RIGHT_ANGLE_EPSILON;
	}

	/**
	 * Quarter turns of the text direction, 0 to 3
	 */
	private static int directionIndex(TextPosition pos)
	{
		return Math.floorMod(Math.round(pos.getDir() / 90f), 4);
	}

	// Geometry is read in the direction-adjusted frame, where text always runs from left to right and
	// y grows downward, whatever the page /Rotate and the text matrix are (issue #173)

	private static float x(TextPosition pos)
	{
		return pos.getXDirAdj();
	}

	private static float y(TextPosition pos)
	{
		return pos.getYDirAdj();
	}

	private static float width(TextPosition pos)
	{
		return pos.getWidthDirAdj();
	}

	private static float height(TextPosition pos)
	{
		return pos.getHeightDir();
	}

	/**
	 * Box of the direction-adjusted frame of the reference glyph in the displayed page space
	 * The frame turns by (page rotation - text direction) clockwise into the displayed page, its size is
	 * the media box size, swapped for vertical text
	 */
	private static BoundingBox toDisplaySpace(BoundingBox box, TextPosition reference)
	{
		if (box == null)
		{
			return null;
		}
		int direction = directionIndex(reference);
		int turns = Math.floorMod(Math.round(reference.getRotation() / 90f) - direction, 4);
		boolean vertical = direction % 2 == 1;
		float frameWidth = vertical ? reference.getPageHeight() : reference.getPageWidth();
		float frameHeight = vertical ? reference.getPageWidth() : reference.getPageHeight();

		float bx = box.getX();
		float by = box.getY();
		float bw = box.getWidth();
		float bh = box.getHeight();
		return switch (turns)
		{
			case 1 -> new BoundingBox(frameHeight - (by + bh), bx, bh, bw);
			case 2 -> new BoundingBox(frameWidth - (bx + bw), frameHeight - (by + bh), bw, bh);
			case 3 -> new BoundingBox(by, frameWidth - (bx + bw), bh, bw);
			default -> box;
		};
	}

	/**
	 * Detect one table in a region of rows without running text, null when there is none
	 */
	private TableModel detectTable(List<TextRow> region, String id)
	{
		List<TextRow> rows = region;
		List<Float> columnBoundaries;
		while (true)
		{
			if (rows.size() < MIN_ROWS)
			{
				LOG.log(Level.FINER, "Not enough rows (min: {0})", MIN_ROWS);
				return null;
			}

			// Detect column boundaries
			columnBoundaries = detectColumnBoundaries(rows);

			LOG.log(Level.FINER, "Column boundaries: {0} -> {1}", LogFactory.args(columnBoundaries.size(), columnBoundaries));

			List<TextRow> trimmed = trimStrayEdgeRows(rows, columnBoundaries);
			if (trimmed.size() == rows.size())
			{
				break;
			}
			rows = trimmed;
		}

		columnBoundaries = removeWordCuts(rows, columnBoundaries);

		if (columnBoundaries.size() < MIN_COLUMNS - 1)  // boundaries = columns - 1
		{
			LOG.log(Level.FINER, "Not enough column boundaries (need at least {0})", MIN_COLUMNS - 1);
			return null;
		}

		// Build table from rows and columns
		TableModel table = buildTable(rows, columnBoundaries, id);
		if (table.getCells().size() < MIN_ROWS * MIN_COLUMNS)
		{
			return null;
		}
		return table;
	}

	/**
	 * Split rows into regions at rows of running text, the running text rows belong to no region
	 */
	private static List<List<TextRow>> splitAtRunningText(List<TextRow> rows)
	{
		List<List<TextRow>> regions = new ArrayList<>();
		List<TextRow> region = new ArrayList<>();
		for (TextRow row : rows)
		{
			if (row.isRunningText())
			{
				LOG.log(Level.FINER, "Running text row at y {0}", row.getBottom());
				if (!region.isEmpty())
				{
					regions.add(region);
					region = new ArrayList<>();
				}
			}
			else
			{
				region.add(row);
			}
		}
		if (!region.isEmpty())
		{
			regions.add(region);
		}
		return regions;
	}

	/**
	 * Rows without the stray rows at the top and bottom edge
	 * A stray row has visible text in one column only or text across a boundary without a column gap,
	 * like the last line of a paragraph or a heading next to the table (issue #200)
	 */
	private static List<TextRow> trimStrayEdgeRows(List<TextRow> rows, List<Float> boundaries)
	{
		int first = 0;
		int last = rows.size();
		while (first < last && isStrayRow(rows.get(first), boundaries))
		{
			first++;
		}
		while (last > first && isStrayRow(rows.get(last - 1), boundaries))
		{
			last--;
		}
		if (first > 0 || last < rows.size())
		{
			LOG.log(Level.FINER, "Trimmed stray edge rows: {0} top, {1} bottom", LogFactory.args(first, rows.size() - last));
		}
		return new ArrayList<>(rows.subList(first, last));
	}

	/**
	 * Whether the row does not fit the column grid and must not start or end a table
	 * Only rows at the region edges are tested, a stray row inside the table stays a table row
	 */
	private static boolean isStrayRow(TextRow row, List<Float> boundaries)
	{
		// A row of whitespace glyphs only has no text to misplace, it stays part of the table
		if (!row.hasVisibleText())
		{
			return false;
		}
		// Text in one column only, e.g. a heading or a short paragraph line left of the first boundary
		if (row.countFilledColumns(boundaries) < MIN_COLUMNS)
		{
			return true;
		}
		// Text across a boundary without a column gap, e.g. the last line of a paragraph that spans
		// several columns, its words would be split between cells
		List<TextRow> single = List.of(row);
		List<List<Gap>> gaps = List.of(row.findGaps(GAP_THRESHOLD));
		for (float boundary : boundaries)
		{
			if (!liesInGapOfEveryRow(single, gaps, boundary))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Boundaries that do not fall inside a word of any row (issue #200)
	 */
	private static List<Float> removeWordCuts(List<TextRow> rows, List<Float> boundaries)
	{
		List<Float> kept = new ArrayList<>();
		for (float boundary : boundaries)
		{
			boolean cuts = false;
			for (TextRow row : rows)
			{
				cuts |= row.cutsWord(boundary);
			}
			if (cuts)
			{
				LOG.log(Level.FINER, "Boundary at {0} cuts a word", boundary);
			}
			else
			{
				kept.add(boundary);
			}
		}
		return kept;
	}

	/**
	 * Group text positions of one text direction into rows based on Y coordinate
	 */
	private static List<TextRow> groupIntoRows(List<Glyph> direction)
	{
		List<TextRow> rows = new ArrayList<>();

		// Sort by Y position (top to bottom)
		List<Glyph> sorted = new ArrayList<>(direction);
		sorted.sort(Comparator.comparing(Glyph::y));

		TextRow currentRow = null;
		float lastY = -1;

		for (Glyph pos : sorted)
		{
			float y = pos.y();

			if (currentRow == null || Math.abs(y - lastY) > ROW_TOLERANCE)
			{
				currentRow = new TextRow();
				rows.add(currentRow);
				lastY = y;
			}

			currentRow.addPosition(pos);
		}

		return rows;
	}

	/**
	 * Detect column boundaries by analyzing gaps between text
	 */
	private List<Float> detectColumnBoundaries(List<TextRow> rows)
	{
		// Analyze each row to find consistent gaps
		List<List<Gap>> allRowGaps = new ArrayList<>();
		for (TextRow row : rows)
		{
			allRowGaps.add(row.findGaps(GAP_THRESHOLD));
		}

		List<Float> boundaries = selectBoundaries(rows, collectRowGaps(rows, allRowGaps, false));

		// Wide gaps across empty cells vote for the column gaps they cross, a boundary found only by
		// these votes must not cut through the text of any row (issue #182)
		for (Float candidate : selectBoundaries(rows, collectRowGaps(rows, allRowGaps, true)))
		{
			if (!isNearBoundary(rows, boundaries, candidate) && liesInGapOfEveryRow(rows, allRowGaps, candidate))
			{
				boundaries.add(candidate);
				LOG.log(Level.FINER, "Boundary at {0} confirmed by wide gaps", candidate);
			}
		}

		Collections.sort(boundaries);
		return boundaries;
	}

	/**
	 * Boundaries from gap midpoints that appear in enough rows
	 */
	private static List<Float> selectBoundaries(List<TextRow> rows, List<List<Float>> rowGaps)
	{
		List<Float> boundaries = new ArrayList<>();

		LOG.log(Level.FINER, "Rows with gaps: {0} / {1}", LogFactory.args(rowGaps.size(), rows.size()));

		if (rowGaps.isEmpty())
		{
			return boundaries;
		}

		// Collect all gap positions from all rows
		List<Float> allGaps = new ArrayList<>();
		for (List<Float> gaps : rowGaps)
		{
			allGaps.addAll(gaps);
		}
		Collections.sort(allGaps);

		// Cluster nearby gaps and count occurrences
		List<Float> clusteredGaps = new ArrayList<>();
		List<Integer> gapCounts = new ArrayList<>();

		for (Float gap : allGaps)
		{
			boolean merged = false;
			for (int i = 0; i < clusteredGaps.size(); i++)
			{
				if (Math.abs(clusteredGaps.get(i) - gap) < COLUMN_TOLERANCE)
				{
					// Update average position
					int count = gapCounts.get(i);
					clusteredGaps.set(i, (clusteredGaps.get(i) * count + gap) / (count + 1));
					gapCounts.set(i, count + 1);
					merged = true;
					break;
				}
			}
			if (!merged)
			{
				clusteredGaps.add(gap);
				gapCounts.add(1);
			}
		}

		mergeEmptyColumnClusters(rows, clusteredGaps, gapCounts);

		// Select gaps that appear in at least 30% of rows with gaps
		int minOccurrences = Math.max(2, (int) (rowGaps.size() * 0.3));
		LOG.log(Level.FINER, "Min occurrences needed: {0}", minOccurrences);

		for (int i = 0; i < clusteredGaps.size(); i++)
		{
			if (gapCounts.get(i) >= minOccurrences)
			{
				boundaries.add(clusteredGaps.get(i));
				LOG.log(Level.FINER, "Boundary at {0} (count: {1})", LogFactory.args(clusteredGaps.get(i), gapCounts.get(i)));
			}
		}

		Collections.sort(boundaries);
		return boundaries;
	}

	/**
	 * Whether no visible glyph starts between the candidate and one of the boundaries
	 * Such a candidate would add a column empty in every row (issue #178)
	 */
	private static boolean isNearBoundary(List<TextRow> rows, List<Float> boundaries, float candidate)
	{
		for (float boundary : boundaries)
		{
			if (!hasVisibleTextStartingIn(rows, Math.min(boundary, candidate), Math.max(boundary, candidate)))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether the position lies outside the visible text or inside a gap in every row
	 */
	private static boolean liesInGapOfEveryRow(List<TextRow> rows, List<List<Gap>> allRowGaps, float position)
	{
		for (int r = 0; r < rows.size(); r++)
		{
			TextRow row = rows.get(r);
			if (!row.hasVisibleText() || position <= row.getMinX() || position >= row.getMaxX())
			{
				continue;
			}
			boolean inGap = false;
			for (Gap gap : allRowGaps.get(r))
			{
				inGap |= position >= gap.start() && position <= gap.end();
			}
			if (!inGap)
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * Gap midpoints of each row that has any, wide gaps across an empty cell are dropped (issue #180)
	 * With voteForSpanned a dropped gap between visible text adds the column gaps it crosses instead
	 */
	private static List<List<Float>> collectRowGaps(List<TextRow> rows, List<List<Gap>> allRowGaps, boolean voteForSpanned)
	{
		List<List<Float>> rowGaps = new ArrayList<>();
		for (int r = 0; r < rows.size(); r++)
		{
			List<Gap> gaps = allRowGaps.get(r);
			List<Float> midpoints = new ArrayList<>();
			for (Gap gap : gaps)
			{
				List<Float> spanned = spannedColumnGaps(gap, gaps, allRowGaps);
				if (spanned.isEmpty())
				{
					midpoints.add(gap.middle());
				}
				else if (voteForSpanned && rows.get(r).isBetweenVisibleText(gap))
				{
					LOG.log(Level.FINER, "Wide gap {0} - {1} votes for {2}", LogFactory.args(gap.start(), gap.end(), spanned));
					midpoints.addAll(spanned);
				}
				else
				{
					LOG.log(Level.FINER, "Ignored wide gap {0} - {1}", LogFactory.args(gap.start(), gap.end()));
				}
			}
			if (!midpoints.isEmpty())
			{
				rowGaps.add(midpoints);
			}
		}
		return rowGaps;
	}

	/**
	 * Gap midpoints of the other row with the most midpoints inside the gap, empty when no row has two
	 * Such a gap runs across an empty cell and its midpoint lies inside a column (issue #180)
	 * The midpoints of the other row are the column gaps it crosses, so a table where only one row
	 * fills all columns keeps its boundaries (issue #182)
	 */
	private static List<Float> spannedColumnGaps(Gap gap, List<Gap> ownGaps, List<List<Gap>> allRowGaps)
	{
		List<Float> best = Collections.emptyList();
		for (List<Gap> otherGaps : allRowGaps)
		{
			if (otherGaps == ownGaps)
			{
				continue;
			}
			List<Float> inside = new ArrayList<>();
			for (Gap other : otherGaps)
			{
				if (other.middle() > gap.start() && other.middle() < gap.end())
				{
					inside.add(other.middle());
				}
			}
			if (inside.size() >= 2 && inside.size() > best.size())
			{
				best = inside;
			}
		}
		return best;
	}

	/**
	 * Merge neighbouring gap clusters with no visible glyph starting between them in any row
	 * Gap midpoints move with the text width of the left column, so one column gap can split into
	 * several clusters that would create a column empty in every row (issue #178)
	 * Runs before the occurrence filter, so the merged cluster counts all its rows
	 */
	private static void mergeEmptyColumnClusters(List<TextRow> rows, List<Float> positions, List<Integer> counts)
	{
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < positions.size(); i++)
		{
			order.add(i);
		}
		order.sort(Comparator.comparing(positions::get));

		List<Float> mergedPositions = new ArrayList<>();
		List<Integer> mergedCounts = new ArrayList<>();
		for (int i : order)
		{
			float position = positions.get(i);
			int count = counts.get(i);
			int last = mergedPositions.size() - 1;
			if (last >= 0 && !hasVisibleTextStartingIn(rows, mergedPositions.get(last), position))
			{
				int lastCount = mergedCounts.get(last);
				mergedPositions.set(last, (mergedPositions.get(last) * lastCount + position * count) / (lastCount + count));
				mergedCounts.set(last, lastCount + count);
				LOG.log(Level.FINER, "Merged gap cluster {0} into {1}", LogFactory.args(position, mergedPositions.get(last)));
			}
			else
			{
				mergedPositions.add(position);
				mergedCounts.add(count);
			}
		}

		positions.clear();
		positions.addAll(mergedPositions);
		counts.clear();
		counts.addAll(mergedCounts);
	}

	private static boolean hasVisibleTextStartingIn(List<TextRow> rows, float start, float end)
	{
		for (TextRow row : rows)
		{
			if (row.hasVisibleTextStartingIn(start, end))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Build table model from detected rows and column boundaries
	 */
	private TableModel buildTable(List<TextRow> rows, List<Float> columnBoundaries, String id)
	{
		TableModel table = new TableModel(id, currentPage);
		table.setRowCount(rows.size());
		table.setColumnCount(columnBoundaries.size() + 1);
		table.setConfidence(calculateConfidence(rows, columnBoundaries));
		table.setHasBorders(false); // Text-based detection assumes no visible borders

		// Real horizontal extent of the table text, replaces sentinel boundaries for empty cells
		float tableMinX = Float.MAX_VALUE;
		float tableMaxX = -Float.MAX_VALUE;
		for (TextRow row : rows)
		{
			tableMinX = Math.min(tableMinX, row.getMinX());
			tableMaxX = Math.max(tableMaxX, row.getMaxX());
		}

		int rowIndex = 0;
		for (TextRow row : rows)
		{
			List<String> cellTexts = row.splitByBoundaries(columnBoundaries);

			for (int colIndex = 0; colIndex < cellTexts.size(); colIndex++)
			{
				String text = cellTexts.get(colIndex).trim();
				TableCell cell = new TableCell(rowIndex, colIndex, text);

				// First row is assumed to be header
				cell.setHeader(rowIndex == 0);

				// Calculate cell bounding box, the detection frame is turned back into the displayed page
				BoundingBox bbox = row.getBoundingBoxForColumn(colIndex, columnBoundaries, tableMinX, tableMaxX);
				cell.setBoundingBox(row.positions.get(0).toDisplaySpace(bbox));

				table.addCell(cell);
			}

			rowIndex++;
		}

		table.calculateBoundingBox();
		return table;
	}

	/**
	 * Calculate confidence score based on table structure quality
	 * Penalizes layout tables (full-page structures used for formatting)
	 */
	private float calculateConfidence(List<TextRow> rows, List<Float> columnBoundaries)
	{
		// Base confidence
		float confidence = 0.5f;

		// More rows = higher confidence, but too many rows suggests layout table
		if (rows.size() >= 5 && rows.size() <= 30)
		{
			confidence += 0.15f;
		}
		else if (rows.size() >= 3 && rows.size() <= 30)
		{
			confidence += 0.1f;
		}

		// More columns = higher confidence
		if (columnBoundaries.size() >= 3)
		{
			confidence += 0.15f;
		}
		else if (columnBoundaries.size() >= 2)
		{
			confidence += 0.1f;
		}

		// Consistent row heights = higher confidence
		if (hasConsistentRowHeights(rows))
		{
			confidence += 0.1f;
		}

		// Penalize layout table patterns
		confidence -= calculateLayoutPenalty(rows, columnBoundaries);

		return Math.max(0.1f, Math.min(confidence, 0.95f));
	}

	/**
	 * Calculate penalty for patterns typical of layout tables (not data tables)
	 * Layout tables span full pages and contain flowing text, not tabular data
	 */
	private float calculateLayoutPenalty(List<TextRow> rows, List<Float> columnBoundaries)
	{
		float penalty = 0f;

		// Too many rows = likely full-page layout, not a data table
		if (rows.size() > 40)
		{
			penalty += 0.4f;
		}
		else if (rows.size() > 25)
		{
			penalty += 0.25f;
		}

		// Check for long text in cells (sentences vs short data values)
		int longCellCount = 0;
		int emptyCellCount = 0;
		int totalCells = 0;

		for (TextRow row : rows)
		{
			List<String> cells = row.splitByBoundaries(columnBoundaries);
			for (String cell : cells)
			{
				totalCells++;
				String trimmed = cell.trim();
				if (trimmed.isEmpty())
				{
					emptyCellCount++;
				}
				else if (trimmed.length() > 50)
				{
					longCellCount++;
				}
			}
		}

		if (totalCells > 0)
		{
			// High ratio of empty cells = layout table
			float emptyRatio = (float) emptyCellCount / totalCells;
			if (emptyRatio > 0.4f)
			{
				penalty += 0.2f;
			}

			// Long text cells = flowing text, not tabular data
			float longRatio = (float) longCellCount / totalCells;
			if (longRatio > 0.15f)
			{
				penalty += 0.25f;
			}
			else if (longRatio > 0.05f)
			{
				penalty += 0.1f;
			}
		}

		// Few columns (2) with many rows = likely label:value layout
		if (columnBoundaries.size() == 1 && rows.size() > 10)
		{
			penalty += 0.15f;
		}

		return penalty;
	}

	/**
	 * Check if rows have consistent heights
	 */
	private boolean hasConsistentRowHeights(List<TextRow> rows)
	{
		if (rows.size() < 2)
		{
			return true;
		}

		float firstHeight = rows.get(0).getHeight();
		int consistentCount = 0;

		for (TextRow row : rows)
		{
			if (Math.abs(row.getHeight() - firstHeight) < 3.0f)
			{
				consistentCount++;
			}
		}

		return consistentCount >= rows.size() * 0.7;
	}

	/**
	 * Horizontal gap between two neighbouring glyphs of a row
	 */
	private record Gap(float start, float end)
	{
		float middle()
		{
			return (start + end) / 2;
		}
	}

	/**
	 * Text position with its geometry in the frame of its group, text runs from left to right there
	 * angle is the frame angle of a skewed group in degrees, 0 marks the direction-adjusted frame
	 */
	private record Glyph(TextPosition source, float x, float y, float width, float height, double angle)
	{
		static Glyph upright(TextPosition pos)
		{
			return new Glyph(pos, PdfTableDetector.x(pos), PdfTableDetector.y(pos), PdfTableDetector.width(pos),
				PdfTableDetector.height(pos), 0);
		}

		/**
		 * Glyph origin in the displayed page turned by the angle, the advance along the baseline is the
		 * width because getWidthDirAdj is only its projection onto the x axis
		 */
		static Glyph skewed(TextPosition pos, double angle)
		{
			double radians = Math.toRadians(angle);
			double cos = Math.cos(radians);
			double sin = Math.sin(radians);
			float frameX = (float) (pos.getX() * cos - pos.getY() * sin);
			float frameY = (float) (pos.getX() * sin + pos.getY() * cos);
			Matrix matrix = pos.getTextMatrix();
			float advance = (float) Math.hypot(pos.getEndX() - matrix.getTranslateX(), pos.getEndY() - matrix.getTranslateY());
			return new Glyph(pos, frameX, frameY, advance, PdfTableDetector.height(pos), angle);
		}

		Glyph onRowOf(Glyph other)
		{
			return new Glyph(source, x, other.y, width, height, angle);
		}

		/**
		 * Box of the frame of this glyph in the displayed page
		 * A skewed box becomes the axis-aligned envelope of its turned rectangle
		 */
		BoundingBox toDisplaySpace(BoundingBox box)
		{
			if (box == null || angle == 0)
			{
				return PdfTableDetector.toDisplaySpace(box, source);
			}

			double radians = Math.toRadians(angle);
			double cos = Math.cos(radians);
			double sin = Math.sin(radians);
			float minX = Float.MAX_VALUE;
			float maxX = -Float.MAX_VALUE;
			float minY = Float.MAX_VALUE;
			float maxY = -Float.MAX_VALUE;
			for (float frameX : new float[] {box.getX(), box.getRight()})
			{
				for (float frameY : new float[] {box.getY(), box.getBottom()})
				{
					float displayX = (float) (frameX * cos + frameY * sin);
					float displayY = (float) (frameY * cos - frameX * sin);
					minX = Math.min(minX, displayX);
					maxX = Math.max(maxX, displayX);
					minY = Math.min(minY, displayY);
					maxY = Math.max(maxY, displayY);
				}
			}
			return new BoundingBox(minX, minY, maxX - minX, maxY - minY);
		}
	}

	/**
	 * Internal class representing a row of text
	 */
	private static class TextRow
	{
		private final List<Glyph> positions;

		TextRow()
		{
			this.positions = new ArrayList<>();
		}

		void addPosition(Glyph pos)
		{
			positions.add(pos);
		}

		float getHeight()
		{
			if (positions.isEmpty())
			{
				return 0;
			}
			return positions.get(0).height();
		}

		/**
		 * Top edge of the highest visible glyph in the row
		 * TextPosition.getY is the baseline measured from the top of the page, the glyph lies above it
		 */
		float getTop()
		{
			float top = Float.MAX_VALUE;
			for (Glyph pos : positions)
			{
				if (isVisible(pos))
				{
					top = Math.min(top, pos.y() - pos.height());
				}
			}
			return top;
		}

		/**
		 * Lowest baseline of the visible glyphs in the row
		 */
		float getBottom()
		{
			float bottom = -Float.MAX_VALUE;
			for (Glyph pos : positions)
			{
				if (isVisible(pos))
				{
					bottom = Math.max(bottom, pos.y());
				}
			}
			return bottom;
		}

		/**
		 * Whitespace glyphs carry no visible text, so they do not count for geometry
		 * Character.isSpaceChar also covers no-break spaces (U+00A0, U+2007, U+202F) that isWhitespace skips
		 */
		static boolean isVisible(Glyph pos)
		{
			String unicode = pos.source().getUnicode();
			return unicode != null
				&& !unicode.codePoints().allMatch(cp -> Character.isWhitespace(cp) || Character.isSpaceChar(cp));
		}

		boolean hasVisibleText()
		{
			for (Glyph pos : positions)
			{
				if (isVisible(pos))
				{
					return true;
				}
			}
			return false;
		}

		/**
		 * Whether a visible glyph starts in [start, end), the range splitByBoundaries uses for a cell
		 */
		boolean hasVisibleTextStartingIn(float start, float end)
		{
			for (Glyph pos : positions)
			{
				if (isVisible(pos) && pos.x() >= start && pos.x() < end)
				{
					return true;
				}
			}
			return false;
		}

		/**
		 * Number of columns with a visible glyph starting in them
		 */
		int countFilledColumns(List<Float> boundaries)
		{
			List<Float> allBoundaries = new ArrayList<>();
			allBoundaries.add(-Float.MAX_VALUE);
			allBoundaries.addAll(boundaries);
			allBoundaries.add(Float.MAX_VALUE);

			int filled = 0;
			for (int i = 0; i < allBoundaries.size() - 1; i++)
			{
				if (hasVisibleTextStartingIn(allBoundaries.get(i), allBoundaries.get(i + 1)))
				{
					filled++;
				}
			}
			return filled;
		}

		/**
		 * Whether the row is a line of running text: many words with even gaps close to a space width
		 * Justified text has wide but even word gaps, a table row has column gaps much wider than the
		 * word gaps inside its cells, or few words
		 */
		boolean isRunningText()
		{
			List<Glyph> visible = getVisibleSortedByX();
			if (visible.size() < RUNNING_TEXT_MIN_GLYPHS)
			{
				return false;
			}

			// Word gaps in space widths of the glyph before the gap
			List<Float> wordGaps = new ArrayList<>();
			for (int i = 1; i < visible.size(); i++)
			{
				Glyph prev = visible.get(i - 1);
				float gap = visible.get(i).x() - (prev.x() + prev.width());
				float space = spaceWidth(prev);
				if (gap >= WORD_GAP_RATIO * space)
				{
					wordGaps.add(gap / space);
				}
			}
			if (wordGaps.size() < RUNNING_TEXT_MIN_WORDS - 1)
			{
				return false;
			}

			Collections.sort(wordGaps);
			float median = wordGaps.get(wordGaps.size() / 2);
			float widest = wordGaps.get(wordGaps.size() - 1);
			return median <= RUNNING_TEXT_MAX_WORD_GAP && widest <= RUNNING_TEXT_MAX_GAP_RATIO * median;
		}

		/**
		 * Whether the boundary falls between two neighbouring visible glyphs of the same word
		 * Glyphs closer than WORD_GAP_RATIO of the space width belong to one word, splitByBoundaries
		 * would split it into two cells
		 */
		boolean cutsWord(float boundary)
		{
			List<Glyph> visible = getVisibleSortedByX();
			for (int i = 1; i < visible.size(); i++)
			{
				Glyph prev = visible.get(i - 1);
				Glyph curr = visible.get(i);
				if (prev.x() < boundary && curr.x() >= boundary)
				{
					return curr.x() - (prev.x() + prev.width()) < WORD_GAP_RATIO * spaceWidth(prev);
				}
			}
			return false;
		}

		private List<Glyph> getVisibleSortedByX()
		{
			List<Glyph> visible = new ArrayList<>();
			for (Glyph pos : positions)
			{
				if (isVisible(pos))
				{
					visible.add(pos);
				}
			}
			visible.sort(Comparator.comparing(Glyph::x));
			return visible;
		}

		/**
		 * Space width of the glyph font, the glyph width when the font gives none
		 */
		private static float spaceWidth(Glyph pos)
		{
			return pos.source().getWidthOfSpace() > 0 ? pos.source().getWidthOfSpace() : pos.width();
		}

		/**
		 * Whether the gap lies between visible glyphs, not next to whitespace glyphs at the row edge
		 */
		boolean isBetweenVisibleText(Gap gap)
		{
			return gap.start() >= getMinX() && gap.end() <= getMaxX();
		}

		/**
		 * Left edge of the leftmost visible text in the row
		 */
		float getMinX()
		{
			float minX = Float.MAX_VALUE;
			for (Glyph pos : positions)
			{
				if (isVisible(pos))
				{
					minX = Math.min(minX, pos.x());
				}
			}
			return minX;
		}

		/**
		 * Right edge of the rightmost visible text in the row
		 */
		float getMaxX()
		{
			float maxX = -Float.MAX_VALUE;
			for (Glyph pos : positions)
			{
				if (isVisible(pos))
				{
					maxX = Math.max(maxX, pos.x() + pos.width());
				}
			}
			return maxX;
		}

		/**
		 * Find gaps between text that could indicate column boundaries
		 */
		List<Gap> findGaps(float thresholdMultiplier)
		{
			List<Gap> gaps = new ArrayList<>();

			if (positions.size() < 2)
			{
				return gaps;
			}

			// Sort by X position
			List<Glyph> sorted = new ArrayList<>(positions);
			sorted.sort(Comparator.comparing(Glyph::x));

			// Calculate average character width
			float totalWidth = 0;
			for (Glyph pos : sorted)
			{
				totalWidth += pos.width();
			}
			float avgWidth = totalWidth / sorted.size();
			float gapThreshold = avgWidth * thresholdMultiplier;

			// Find gaps
			for (int i = 1; i < sorted.size(); i++)
			{
				Glyph prev = sorted.get(i - 1);
				Glyph curr = sorted.get(i);

				float gap = curr.x() - (prev.x() + prev.width());
				if (gap > gapThreshold)
				{
					gaps.add(new Gap(prev.x() + prev.width(), curr.x()));
				}
			}

			return gaps;
		}

		/**
		 * Split row text by column boundaries
		 */
		List<String> splitByBoundaries(List<Float> boundaries)
		{
			List<String> cells = new ArrayList<>();

			// Sort positions by X
			List<Glyph> sorted = new ArrayList<>(positions);
			sorted.sort(Comparator.comparing(Glyph::x));

			// Add sentinel boundaries
			List<Float> allBoundaries = new ArrayList<>();
			allBoundaries.add(-Float.MAX_VALUE);
			allBoundaries.addAll(boundaries);
			allBoundaries.add(Float.MAX_VALUE);

			for (int i = 0; i < allBoundaries.size() - 1; i++)
			{
				float start = allBoundaries.get(i);
				float end = allBoundaries.get(i + 1);

				StringBuilder cellText = new StringBuilder();
				for (Glyph pos : sorted)
				{
					float x = pos.x();
					if (x >= start && x < end)
					{
						cellText.append(pos.source().getUnicode());
					}
				}
				cells.add(cellText.toString());
			}

			return cells;
		}

		/**
		 * Get bounding box for a specific column
		 * An empty cell spans the column limited by the table text extent instead of the sentinels,
		 * a row without visible text has no bounding boxes
		 */
		BoundingBox getBoundingBoxForColumn(int colIndex, List<Float> boundaries, float tableMinX, float tableMaxX)
		{
			if (!hasVisibleText())
			{
				return null;
			}

			List<Float> allBoundaries = new ArrayList<>();
			allBoundaries.add(-Float.MAX_VALUE);
			allBoundaries.addAll(boundaries);
			allBoundaries.add(Float.MAX_VALUE);

			if (colIndex >= allBoundaries.size() - 1)
			{
				return new BoundingBox(0, getTop(), 0, getBottom() - getTop());
			}

			float startX = allBoundaries.get(colIndex);
			float endX = allBoundaries.get(colIndex + 1);

			// Find actual text bounds within this column, each glyph has its own baseline and height
			float minX = Float.MAX_VALUE;
			float maxX = -Float.MAX_VALUE;
			float top = Float.MAX_VALUE;
			float bottom = -Float.MAX_VALUE;

			for (Glyph pos : positions)
			{
				float x = pos.x();
				if (isVisible(pos) && x >= startX && x < endX)
				{
					minX = Math.min(minX, x);
					maxX = Math.max(maxX, x + pos.width());
					top = Math.min(top, pos.y() - pos.height());
					bottom = Math.max(bottom, pos.y());
				}
			}

			if (minX == Float.MAX_VALUE)
			{
				float left = Math.max(startX, tableMinX);
				float right = Math.min(endX, tableMaxX);
				return new BoundingBox(left, getTop(), Math.max(0, right - left), getBottom() - getTop());
			}

			return new BoundingBox(minX, top, maxX - minX, bottom - top);
		}
	}
}
